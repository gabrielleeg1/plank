package com.gabrielleeg1.plank.compiler.instructions.decl

import arrow.core.computations.either
import com.gabrielleeg1.plank.analyzer.element.ResolvedLetDecl
import com.gabrielleeg1.plank.compiler.CompilerContext
import com.gabrielleeg1.plank.compiler.buildAlloca
import com.gabrielleeg1.plank.compiler.buildStore
import com.gabrielleeg1.plank.compiler.instructions.CodegenResult
import com.gabrielleeg1.plank.compiler.instructions.CompilerInstruction

class LetInstruction(private val descriptor: ResolvedLetDecl) : CompilerInstruction() {
  override fun CompilerContext.codegen(): CodegenResult = either.eager {
    val name = descriptor.name.text

    val variable = buildAlloca(descriptor.type.toType().bind(), name).also {
      addVariable(name, descriptor.type, it)
    }

    val value = descriptor.value.toInstruction().codegen().bind()

    buildStore(variable, value)
  }
}