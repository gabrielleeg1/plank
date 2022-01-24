package com.gabrielleeg1.plank.compiler.instructions.expr

import com.gabrielleeg1.plank.analyzer.element.TypedConstExpr
import com.gabrielleeg1.plank.compiler.CompilerContext
import com.gabrielleeg1.plank.compiler.builder.buildGlobalStringPtr
import com.gabrielleeg1.plank.compiler.instructions.CompilerInstruction
import com.gabrielleeg1.plank.compiler.instructions.invalidConstantError
import org.llvm4j.llvm4j.Value

class ConstInstruction(private val descriptor: TypedConstExpr) : CompilerInstruction {
  override fun CompilerContext.codegen(): Value {
    return when (val value = descriptor.value) {
      is Double -> runtime.types.double.getConstant(value.toDouble())
      is Int -> runtime.types.int.getConstant(value.toInt())
      is Byte -> runtime.types.i8.getConstant(value.toInt())
      is Short -> runtime.types.i16.getConstant(value.toInt())
      is Float -> runtime.types.float.getConstant(value.toDouble())
      is Boolean -> if (value) runtime.trueConstant else runtime.falseConstant
      is String -> buildGlobalStringPtr(value, "str")
      else -> invalidConstantError(value)
    }
  }
}
