package org.plank.codegen.expr

import org.plank.analyzer.element.TypedAccessExpr
import org.plank.analyzer.element.TypedRefExpr
import org.plank.codegen.CodegenInstruction
import org.plank.codegen.alloca
import org.plank.codegen.scope.CodegenContext
import org.plank.llvm4k.ir.Value

class RefInst(private val descriptor: TypedRefExpr) : CodegenInstruction {
  override fun CodegenContext.codegen(): Value {
    return when (val expr = descriptor.value) {
      is TypedAccessExpr -> getSymbol(this, expr.name.text)
      else -> alloca(expr.codegen())
    }
  }
}
