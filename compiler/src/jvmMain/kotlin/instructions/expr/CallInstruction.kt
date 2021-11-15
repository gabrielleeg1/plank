package com.gabrielleeg1.plank.compiler.instructions.expr

import arrow.core.computations.either
import arrow.core.left
import com.gabrielleeg1.plank.analyzer.element.TypedAccessExpr
import com.gabrielleeg1.plank.analyzer.element.TypedCallExpr
import com.gabrielleeg1.plank.analyzer.element.TypedGetExpr
import com.gabrielleeg1.plank.compiler.CompilerContext
import com.gabrielleeg1.plank.compiler.buildCall
import com.gabrielleeg1.plank.compiler.instructions.CodegenResult
import com.gabrielleeg1.plank.compiler.instructions.CompilerInstruction
import com.gabrielleeg1.plank.compiler.instructions.unresolvedFunctionError
import org.llvm4j.llvm4j.Function

class CallInstruction(private val descriptor: TypedCallExpr) : CompilerInstruction() {
  override fun CompilerContext.codegen(): CodegenResult = either.eager {
    val callee = when (val callee = descriptor.callee) {
      is TypedAccessExpr -> findFunction(callee.name.text)
      is TypedGetExpr -> findFunction(callee.member.text)
      else -> null
    }

    val function = callee
      ?.accessIn(this@codegen)
      ?: unresolvedFunctionError(descriptor.callee)
        .left()
        .bind<Function>()

    val arguments = descriptor.arguments.map { it.toInstruction().codegen().bind() }

    buildCall(function, arguments)
  }
}
