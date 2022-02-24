package org.plank.analyzer.phases

import org.plank.analyzer.element.ResolvedCodeBody
import org.plank.analyzer.element.ResolvedDecl
import org.plank.analyzer.element.ResolvedEnumDecl
import org.plank.analyzer.element.ResolvedExprBody
import org.plank.analyzer.element.ResolvedExprStmt
import org.plank.analyzer.element.ResolvedFunDecl
import org.plank.analyzer.element.ResolvedFunctionBody
import org.plank.analyzer.element.ResolvedLetDecl
import org.plank.analyzer.element.ResolvedModuleDecl
import org.plank.analyzer.element.ResolvedNoBody
import org.plank.analyzer.element.ResolvedPlankFile
import org.plank.analyzer.element.ResolvedReturnStmt
import org.plank.analyzer.element.ResolvedStmt
import org.plank.analyzer.element.ResolvedStructDecl
import org.plank.analyzer.element.ResolvedUseDecl
import org.plank.analyzer.element.TypedAccessExpr
import org.plank.analyzer.element.TypedAssignExpr
import org.plank.analyzer.element.TypedBlockBranch
import org.plank.analyzer.element.TypedBlockExpr
import org.plank.analyzer.element.TypedCallExpr
import org.plank.analyzer.element.TypedConstExpr
import org.plank.analyzer.element.TypedDerefExpr
import org.plank.analyzer.element.TypedEraseExpr
import org.plank.analyzer.element.TypedExpr
import org.plank.analyzer.element.TypedGetExpr
import org.plank.analyzer.element.TypedGroupExpr
import org.plank.analyzer.element.TypedIdentPattern
import org.plank.analyzer.element.TypedIfBranch
import org.plank.analyzer.element.TypedIfExpr
import org.plank.analyzer.element.TypedInstanceExpr
import org.plank.analyzer.element.TypedIntOperationExpr
import org.plank.analyzer.element.TypedMatchExpr
import org.plank.analyzer.element.TypedNamedTuplePattern
import org.plank.analyzer.element.TypedPattern
import org.plank.analyzer.element.TypedRefExpr
import org.plank.analyzer.element.TypedReifyExpr
import org.plank.analyzer.element.TypedSetExpr
import org.plank.analyzer.element.TypedSizeofExpr
import org.plank.analyzer.element.TypedThenBranch
import org.plank.syntax.element.Identifier
import org.plank.syntax.element.QualifiedPath

@Suppress("TooManyFunctions")
open class IrTransformingPhase :
  TypedExpr.Visitor<TypedExpr>,
  TypedPattern.Visitor<TypedPattern>,
  TypedIfBranch.Visitor<TypedIfBranch>,
  ResolvedStmt.Visitor<ResolvedStmt>,
  ResolvedPlankFile.Visitor<ResolvedPlankFile>,
  ResolvedFunctionBody.Visitor<ResolvedFunctionBody>,
  Identifier.Visitor<Identifier>,
  QualifiedPath.Visitor<QualifiedPath> {
  open fun transformNoBody(body: ResolvedNoBody): ResolvedFunctionBody {
    return body
  }

  open fun transformExprBody(body: ResolvedExprBody): ResolvedFunctionBody {
    return body
  }

  open fun transformCodeBody(body: ResolvedCodeBody): ResolvedFunctionBody {
    return body
  }

  open fun transformPlankFile(file: ResolvedPlankFile): ResolvedPlankFile {
    return file
  }

  open fun transformExprStmt(stmt: ResolvedExprStmt): ResolvedStmt {
    return stmt
  }

  open fun transformReturnStmt(stmt: ResolvedReturnStmt): ResolvedStmt {
    return stmt
  }

  open fun transformUseDecl(decl: ResolvedUseDecl): ResolvedStmt {
    return decl
  }

  open fun transformModuleDecl(decl: ResolvedModuleDecl): ResolvedStmt {
    return decl
  }

  open fun transformEnumDecl(decl: ResolvedEnumDecl): ResolvedStmt {
    return decl
  }

  open fun transformStructDecl(decl: ResolvedStructDecl): ResolvedStmt {
    return decl
  }

  open fun transformFunDecl(decl: ResolvedFunDecl): ResolvedStmt {
    return decl
  }

  open fun transformLetDecl(decl: ResolvedLetDecl): ResolvedStmt {
    return decl
  }

  open fun transformBlockExpr(expr: TypedBlockExpr): TypedExpr {
    return expr
  }

  open fun transformConstExpr(expr: TypedConstExpr): TypedExpr {
    return expr
  }

  open fun transformIfExpr(expr: TypedIfExpr): TypedExpr {
    return expr
  }

  open fun transformAccessExpr(expr: TypedAccessExpr): TypedExpr {
    return expr
  }

  open fun transformIntOperationExpr(expr: TypedIntOperationExpr): TypedExpr {
    return expr
  }

  open fun transformCallExpr(expr: TypedCallExpr): TypedExpr {
    return expr
  }

  open fun transformAssignExpr(expr: TypedAssignExpr): TypedExpr {
    return expr
  }

  open fun transformSetExpr(expr: TypedSetExpr): TypedExpr {
    return expr
  }

  open fun transformGetExpr(expr: TypedGetExpr): TypedExpr {
    return expr
  }

  open fun transformGroupExpr(expr: TypedGroupExpr): TypedExpr {
    return expr
  }

  open fun transformInstanceExpr(expr: TypedInstanceExpr): TypedExpr {
    return expr
  }

  open fun transformSizeofExpr(expr: TypedSizeofExpr): TypedExpr {
    return expr
  }

  open fun transformReferenceExpr(expr: TypedRefExpr): TypedExpr {
    return expr
  }

  open fun transformDerefExpr(expr: TypedDerefExpr): TypedExpr {
    return expr
  }

  open fun transformMatchExpr(expr: TypedMatchExpr): TypedExpr {
    return expr
  }

  open fun transformReifyExpr(expr: TypedReifyExpr): TypedExpr {
    return expr
  }

  open fun transformEraseExpr(expr: TypedEraseExpr): TypedExpr {
    return expr
  }

  open fun transformNamedTuplePattern(pattern: TypedNamedTuplePattern): TypedPattern {
    return pattern
  }

  open fun transformIdentPattern(pattern: TypedIdentPattern): TypedPattern {
    return pattern
  }

  open fun transformThenBranch(branch: TypedThenBranch): TypedIfBranch {
    return branch
  }

  open fun transformBlockBranch(branch: TypedBlockBranch): TypedIfBranch {
    return branch
  }

  open fun transformIdentifier(identifier: Identifier): Identifier {
    return identifier
  }

  open fun transformQualifiedPath(path: QualifiedPath): QualifiedPath {
    return path
  }

  final override fun visitNoBody(body: ResolvedNoBody): ResolvedFunctionBody {
    return transformNoBody(body)
  }

  final override fun visitExprBody(body: ResolvedExprBody): ResolvedFunctionBody {
    return transformExprBody(body.copy(expr = visitExpr(body.expr)))
  }

  final override fun visitCodeBody(body: ResolvedCodeBody): ResolvedFunctionBody {
    return transformCodeBody(
      body.copy(
        stmts = visitStmts(body.stmts),
        value = body.value?.let { visitExpr(it) },
      ),
    )
  }

  final override fun visitPlankFile(file: ResolvedPlankFile): ResolvedPlankFile {
    return transformPlankFile(
      file.copy(program = visitStmts(file.program).filterIsInstance<ResolvedDecl>())
    )
  }

  final override fun visitExprStmt(stmt: ResolvedExprStmt): ResolvedStmt {
    return transformExprStmt(stmt.copy(expr = visitExpr(stmt.expr)))
  }

  final override fun visitReturnStmt(stmt: ResolvedReturnStmt): ResolvedStmt {
    return transformReturnStmt(stmt.copy(value = stmt.value?.let { visitExpr(it) }))
  }

  final override fun visitUseDecl(decl: ResolvedUseDecl): ResolvedStmt {
    return transformUseDecl(decl)
  }

  final override fun visitModuleDecl(decl: ResolvedModuleDecl): ResolvedStmt {
    return transformModuleDecl(
      decl.copy(
        name = visitQualifiedPath(decl.name),
        content = visitStmts(decl.content).filterIsInstance<ResolvedDecl>()
      )
    )
  }

  final override fun visitEnumDecl(decl: ResolvedEnumDecl): ResolvedStmt {
    return transformEnumDecl(
      decl.copy(
        info = decl.info.copy(
          name = visitIdentifier(decl.name),
          members = decl.members.entries.associate { (name, member) ->
            val newName = visitIdentifier(name)

            newName to member.copy(name = visitIdentifier(member.name))
          }
        )
      )
    )
  }

  final override fun visitStructDecl(decl: ResolvedStructDecl): ResolvedStmt {
    return transformStructDecl(
      decl.copy(
        info = decl.info.copy(
          name = visitIdentifier(decl.name),
          members = decl.members.entries.associate { (name, property) ->
            val newName = visitIdentifier(name)

            newName to property.copy(name = newName)
          }
        )
      )
    )
  }

  final override fun visitFunDecl(decl: ResolvedFunDecl): ResolvedStmt {
    return transformFunDecl(
      decl.copy(
        body = visitFunctionBody(decl.body),
        info = decl.info.copy(name = visitIdentifier(decl.info.name))
      )
    )
  }

  final override fun visitLetDecl(decl: ResolvedLetDecl): ResolvedStmt {
    return transformLetDecl(
      decl.copy(
        name = visitIdentifier(decl.name),
        value = visitExpr(decl.value)
      )
    )
  }

  final override fun visitBlockExpr(expr: TypedBlockExpr): TypedExpr {
    return transformBlockExpr(
      expr.copy(stmts = visitStmts(expr.stmts), value = visitExpr(expr.value))
    )
  }

  final override fun visitConstExpr(expr: TypedConstExpr): TypedExpr {
    return transformConstExpr(expr)
  }

  final override fun visitIfExpr(expr: TypedIfExpr): TypedExpr {
    return transformIfExpr(
      expr.copy(
        cond = visitExpr(expr.cond),
        thenBranch = visitIfBranch(expr.thenBranch),
        elseBranch = expr.elseBranch?.let { visitIfBranch(it) }
      )
    )
  }

  final override fun visitAccessExpr(expr: TypedAccessExpr): TypedExpr {
    return transformAccessExpr(
      expr.copy(variable = expr.variable.name(visitIdentifier(expr.variable.name)))
    )
  }

  final override fun visitCallExpr(expr: TypedCallExpr): TypedExpr {
    return transformCallExpr(
      expr.copy(callee = visitExpr(expr.callee), argument = visitExpr(expr.argument))
    )
  }

  final override fun visitAssignExpr(expr: TypedAssignExpr): TypedExpr {
    return transformAssignExpr(
      expr.copy(name = visitIdentifier(expr.name), value = visitExpr(expr.value)),
    )
  }

  final override fun visitSetExpr(expr: TypedSetExpr): TypedExpr {
    return transformSetExpr(
      expr.copy(
        receiver = visitExpr(expr.receiver),
        member = visitIdentifier(expr.member),
        value = visitExpr(expr.value),
      )
    )
  }

  final override fun visitGetExpr(expr: TypedGetExpr): TypedExpr {
    return transformGetExpr(
      expr.copy(receiver = visitExpr(expr.receiver), member = visitIdentifier(expr.member))
    )
  }

  final override fun visitGroupExpr(expr: TypedGroupExpr): TypedExpr {
    return transformGroupExpr(expr.copy(value = visitExpr(expr.value)))
  }

  final override fun visitInstanceExpr(expr: TypedInstanceExpr): TypedExpr {
    return transformInstanceExpr(
      expr.copy(
        arguments = expr.arguments.entries.associate { (name, value) ->
          visitIdentifier(name) to visitExpr(value)
        },
      )
    )
  }

  final override fun visitSizeofExpr(expr: TypedSizeofExpr): TypedExpr {
    return transformSizeofExpr(expr)
  }

  final override fun visitRefExpr(expr: TypedRefExpr): TypedExpr {
    return transformReferenceExpr(expr.copy(value = visitExpr(expr.value)))
  }

  final override fun visitDerefExpr(expr: TypedDerefExpr): TypedExpr {
    return transformDerefExpr(expr.copy(value = visitExpr(expr.value)))
  }

  final override fun visitMatchExpr(expr: TypedMatchExpr): TypedExpr {
    return transformMatchExpr(
      expr.copy(
        subject = visitExpr(expr.subject),
        patterns = expr.patterns.entries.associate { (pattern, value) ->
          visitPattern(pattern) to visitExpr(value)
        }
      )
    )
  }

  final override fun visitReifyExpr(expr: TypedReifyExpr): TypedExpr {
    return transformReifyExpr(expr.copy(value = visitExpr(expr.value)))
  }

  final override fun visitEraseExpr(expr: TypedEraseExpr): TypedExpr {
    return transformEraseExpr(expr.copy(value = visitExpr(expr.value)))
  }

  final override fun visitNamedTuplePattern(pattern: TypedNamedTuplePattern): TypedPattern {
    return transformNamedTuplePattern(
      pattern.copy(properties = pattern.properties.map { visitPattern(it) })
    )
  }

  final override fun visitIdentPattern(pattern: TypedIdentPattern): TypedPattern {
    return transformIdentPattern(pattern.copy(name = visitIdentifier(pattern.name)))
  }

  final override fun visitIdentifier(identifier: Identifier): Identifier {
    return transformIdentifier(identifier)
  }

  final override fun visitQualifiedPath(path: QualifiedPath): QualifiedPath {
    path.fullPath.forEach { visitIdentifier(it) }

    return transformQualifiedPath(path)
  }

  final override fun visitIntOperationExpr(expr: TypedIntOperationExpr): TypedExpr {
    return transformIntOperationExpr(expr)
  }

  final override fun visitThenBranch(branch: TypedThenBranch): TypedIfBranch {
    return transformThenBranch(branch.copy(value = visitExpr(branch.value)))
  }

  final override fun visitBlockBranch(branch: TypedBlockBranch): TypedIfBranch {
    return transformBlockBranch(
      branch.copy(
        stmts = visitStmts(branch.stmts),
        value = visitExpr(branch.value),
      )
    )
  }
}
