package com.lorenzoog.plank.compiler.instructions.element

import com.lorenzoog.plank.compiler.CompilerContext
import com.lorenzoog.plank.compiler.buildAlloca
import com.lorenzoog.plank.compiler.buildBitcast
import com.lorenzoog.plank.compiler.buildReturn
import com.lorenzoog.plank.compiler.buildStore
import com.lorenzoog.plank.compiler.builder.getInstance
import com.lorenzoog.plank.compiler.instructions.CodegenError
import com.lorenzoog.plank.compiler.instructions.unresolvedTypeError
import com.lorenzoog.plank.grammar.element.Decl
import com.lorenzoog.plank.shared.Either
import com.lorenzoog.plank.shared.Left
import com.lorenzoog.plank.shared.Right
import com.lorenzoog.plank.shared.either
import org.llvm4j.llvm4j.Constant
import org.llvm4j.llvm4j.Function

class IREnumConstructor(
  private val member: Decl.EnumDecl.Member,
  override val descriptor: Decl.EnumDecl,
) : IRFunction() {
  override val name = member.name.text
  override val mangledName = "${descriptor.name.text}_$name" // TODO: mangle properly

  override fun accessIn(context: CompilerContext): Function? {
    return context.module.getFunction(mangledName).toNullable()
  }

  override fun CompilerContext.codegen(): Either<CodegenError, Function> = either {
    val parameters = member.fields
      .map(binding::visit)
      .map { !it.toType() }

    val returnType = !binding.visit(descriptor).toType()
    val functionType = context.getFunctionType(
      returnType,
      *parameters.toTypedArray(),
      isVariadic = false
    )

    val enum = !binding.visit(descriptor).toType()
    val struct = findStruct(mangledName) ?: return Left(unresolvedTypeError(name))
    val function = module.addFunction(mangledName, functionType)

    createNestedScope(descriptor.name.text) {
      context.newBasicBlock("entry")
        .also(function::addBasicBlock)
        .also(builder::positionAfter)

      val arguments = function.getParameters().map { Constant(it.ref) }.toTypedArray()

      val index = runtime.types.i8.getConstant(descriptor.members.indexOf(member))
      val instance = !getInstance(struct, index, *arguments)

      val pointer = buildAlloca(instance.getType(), "ptr")
      buildStore(pointer, instance)

      val bitcast = buildBitcast(pointer, enum)

      buildReturn(bitcast)
    }

    Right(function)
  }
}
