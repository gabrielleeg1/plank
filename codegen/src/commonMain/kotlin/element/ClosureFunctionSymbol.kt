package org.plank.codegen.element

import org.plank.analyzer.FunctionType
import org.plank.analyzer.PlankType
import org.plank.codegen.CodegenContext
import org.plank.codegen.ExecContext
import org.plank.codegen.codegenError
import org.plank.codegen.createScopeContext
import org.plank.codegen.getField
import org.plank.codegen.instantiate
import org.plank.codegen.unsafeAlloca
import org.plank.llvm4k.ir.User
import org.plank.llvm4k.ir.Value
import org.plank.syntax.element.Identifier

class ClosureFunctionSymbol(
  override val type: FunctionType,
  override val name: String,
  private val mangled: String,
  private val references: Map<Identifier, PlankType>,
  private val parameters: Map<Identifier, PlankType>,
  private val realParameters: Map<Identifier, PlankType>,
  private val generate: GenerateBody,
) : FunctionSymbol {
  override fun CodegenContext.access(): User {
    return getSymbol(mangled)
  }

  override fun CodegenContext.codegen(): Value { // TODO: fix access of variables
    val returnType = type.actualReturnType.typegen()
    val references = references.mapKeys { (name) -> name.text }

    val environmentType = createNamedStruct("closure.env.$mangled") {
      elements = references.map { it.value.typegen() }
    }

    val functionType = org.plank.llvm4k.ir.FunctionType(
      returnType,
      environmentType.pointer(),
      *parameters.values.toList().typegen().toTypedArray(),
    )

    val closureFunctionType = createNamedStruct("closure.fn.$mangled") {
      elements = listOf(functionType.pointer(), environmentType.pointer())
    }

    val function = currentModule.addFunction(mangled, functionType)

    // All closures are nested
    val enclosingBlock = insertionBlock ?: codegenError("No block in context")

    createScopeContext(name) {
      positionAfter(createBasicBlock("entry").also(function::appendBasicBlock))
      val arguments = function.arguments
      val environment = arguments.first().apply { name = "env" }

      val executionContext = ExecContext(this, function, returnType)

      with(executionContext) {
        references.entries.forEachIndexed { index, (reference, type) ->
          val variable = getField(environment, index, "env.$reference")

          if (reference in this@ClosureFunctionSymbol.realParameters.keys.map { it.text }) {
            this.arguments[reference] = createLoad(variable)
          }

          setSymbol(reference, type, unsafeAlloca(variable))
        }

        val realArguments = arguments.drop(1)

        realArguments.forEachIndexed(generateParameter(parameters))

        generate()
      }

      if (!function.verify()) {
        codegenError("Invalid function `${function.name}`")
      }
    }

    positionAfter(enclosingBlock)

    val variables = references.keys
      .mapNotNull { findAlloca(it) }
      .map { createLoad(it) }
      .toTypedArray()

    val environment = run {
      val instance = createMalloc(environmentType, name = name)

      variables.forEachIndexed { idx, value ->
        val field = getField(instance, idx)

        createStore(value, field)
      }

      instance
    }
    val closure = instantiate(closureFunctionType, function, environment)

    setSymbol(mangled, type, closure)

    return closure
  }
}

fun CodegenContext.addClosure(
  name: String,
  type: FunctionType,
  mangled: String = name,
  references: Map<Identifier, PlankType> = linkedMapOf(),
  realParameters: Map<Identifier, PlankType> = type.realParameters,
  generate: GenerateBody,
): ClosureFunctionSymbol {
  val closure = ClosureFunctionSymbol(
    name = name,
    mangled = mangled,
    type = type,
    references = references,
    parameters = type.realParameters,
    realParameters = realParameters,
    generate = generate,
  )

  addFunction(closure)

  return closure
}
