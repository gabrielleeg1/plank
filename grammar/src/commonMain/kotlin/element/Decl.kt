package com.lorenzoog.plank.grammar.element

import org.antlr.v4.kotlinruntime.Token

sealed class Decl : Stmt() {
  data class StructDecl(
    val name: Identifier,
    val fields: List<Field>,
    override val location: Location
  ) : Decl() {
    data class Field(val mutable: Boolean, val name: Identifier, val type: TypeDef)

    override fun <T> accept(visitor: Visitor<T>): T {
      return visitor.visitClassDecl(this)
    }
  }

  data class ImportDecl(val module: Identifier, override val location: Location) : Decl() {
    override fun <T> accept(visitor: Visitor<T>): T {
      return visitor.visitImportDecl(this)
    }
  }

  data class ModuleDecl(
    val name: Identifier,
    val content: List<Decl>,
    override val location: Location
  ) : Decl() {
    override fun <T> accept(visitor: Visitor<T>): T {
      return visitor.visitModuleDecl(this)
    }
  }

  data class FunDecl(
    val modifiers: List<Modifier> = emptyList(),
    val name: Identifier,
    val type: TypeDef.Function,
    val body: List<Stmt>,
    val realParameters: Map<Token, TypeDef>,
    override val location: Location
  ) : Decl() {
    enum class Modifier { Native }

    val isNative get() = Modifier.Native in modifiers

    val parameters = type.parameters
    val returnType = type.returnType

    override fun <T> accept(visitor: Visitor<T>): T {
      return visitor.visitFunDecl(this)
    }
  }

  data class LetDecl(
    val name: Identifier,
    val mutable: Boolean,
    val type: TypeDef?,
    val value: Expr,
    override val location: Location
  ) : Decl() {
    override fun <T> accept(visitor: Visitor<T>): T {
      return visitor.visitLetDecl(this)
    }
  }
}
