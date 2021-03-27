package com.lorenzoog.plank.compiler.instructions.expr

import com.lorenzoog.plank.compiler.PlankContext
import com.lorenzoog.plank.compiler.instructions.PlankInstruction
import com.lorenzoog.plank.grammar.element.Expr
import org.llvm4j.llvm4j.Value
import org.llvm4j.optional.None

class AccessInstruction(private val descriptor: Expr.Access) : PlankInstruction() {
  override fun codegen(context: PlankContext): Value? {
    val name = descriptor.name.text

    val value = context.findVariable(name)
      ?: return context.report("variable does not exists", descriptor)

    return context.builder.buildLoad(value, None)
  }
}
