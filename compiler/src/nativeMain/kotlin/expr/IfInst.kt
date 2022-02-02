package com.gabrielleeg1.plank.compiler.expr

import com.gabrielleeg1.plank.analyzer.BoolType
import com.gabrielleeg1.plank.analyzer.PlankType
import com.gabrielleeg1.plank.analyzer.element.TypedIfExpr
import com.gabrielleeg1.plank.compiler.CodegenContext
import com.gabrielleeg1.plank.compiler.CodegenInstruction
import com.gabrielleeg1.plank.compiler.alloca
import com.gabrielleeg1.plank.compiler.codegenError
import com.gabrielleeg1.plank.compiler.createUnit
import org.plank.llvm4k.ir.Type
import org.plank.llvm4k.ir.Value

class IfInst(private val descriptor: TypedIfExpr) : CodegenInstruction {
  override fun CodegenContext.codegen(): Value {
    return createIf(
      descriptor.type,
      descriptor.cond.codegen(),
      thenStmts = { listOf(descriptor.thenBranch.codegen()) },
      elseStmts = { listOf(descriptor.elseBranch?.codegen() ?: createUnit()) },
    )
  }
}

fun CodegenContext.createAnd(lhs: Value, rhs: Value): Value {
  val variable = createAlloca(BoolType.typegen())

  val thenStmts = { listOf(createStore(variable, rhs)) }
  val elseStmts = { listOf(createStore(variable, i1.getConstant(0))) }

  createIf(BoolType, lhs, thenStmts, elseStmts)

  return createLoad(variable)
}

fun CodegenContext.createIf(
  type: PlankType,
  cond: Value,
  thenStmts: () -> List<Value>,
  elseStmts: () -> List<Value> = ::emptyList,
): Value {
  val insertionBlock = insertionBlock ?: codegenError("No block in context")
  val currentFunction = insertionBlock.function ?: codegenError("No function in context")

  val thenBranch = createBasicBlock("then").also(currentFunction::appendBasicBlock)
  var elseBranch = createBasicBlock("else")

  val mergeBranch = createBasicBlock("if.cont")

  val condBr = createCondBr(cond, thenBranch, elseBranch) // create condition

  val thenRet: Value?
  val elseRet: Value?

  thenBranch.also { br ->
    positionAfter(br) // emit then

    thenRet = thenStmts().lastOrNull()
      ?.takeIf { it.type.kind != Type.Kind.Void }
      ?.takeIf { it.type != void }
      ?.also { alloca(it, "then.v") }

    createBr(mergeBranch)
  }

  elseBranch.also { br ->
    currentFunction.appendBasicBlock(br)
    positionAfter(br) // emit else

    elseRet = elseStmts().lastOrNull()
      ?.takeIf { it.type.kind != Type.Kind.Void }
      ?.takeIf { it.type != void }
      ?.also { alloca(it, "else.v") }

    createBr(mergeBranch)
  }

  elseBranch = insertionBlock

  currentFunction.appendBasicBlock(mergeBranch)
  positionAfter(mergeBranch)

  if (thenRet != null && elseRet != null) {
    val phiType = type.typegen()

    return createPhi(phiType, "if.tmp").apply {
      addIncoming(thenRet, thenBranch)
      addIncoming(elseRet, elseBranch)
    }
  }

  return condBr
}