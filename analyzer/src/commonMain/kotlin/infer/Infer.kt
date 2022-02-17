@file:Suppress("MaxLineLength", "MaximumLineLength")

package org.plank.analyzer.infer

import org.plank.analyzer.BindingViolation
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
import org.plank.analyzer.element.TypedBlockExpr
import org.plank.analyzer.element.TypedCallExpr
import org.plank.analyzer.element.TypedConstExpr
import org.plank.analyzer.element.TypedDerefExpr
import org.plank.analyzer.element.TypedExpr
import org.plank.analyzer.element.TypedGetExpr
import org.plank.analyzer.element.TypedGroupExpr
import org.plank.analyzer.element.TypedIdentPattern
import org.plank.analyzer.element.TypedIfExpr
import org.plank.analyzer.element.TypedInstanceExpr
import org.plank.analyzer.element.TypedMatchExpr
import org.plank.analyzer.element.TypedNamedTuplePattern
import org.plank.analyzer.element.TypedPattern
import org.plank.analyzer.element.TypedRefExpr
import org.plank.analyzer.element.TypedSetExpr
import org.plank.analyzer.element.TypedSizeofExpr
import org.plank.shared.depthFirstSearch
import org.plank.syntax.element.AccessExpr
import org.plank.syntax.element.AccessTypeRef
import org.plank.syntax.element.ArrayTypeRef
import org.plank.syntax.element.AssignExpr
import org.plank.syntax.element.BlockExpr
import org.plank.syntax.element.CallExpr
import org.plank.syntax.element.CodeBody
import org.plank.syntax.element.ConstExpr
import org.plank.syntax.element.DerefExpr
import org.plank.syntax.element.EnumDecl
import org.plank.syntax.element.Expr
import org.plank.syntax.element.ExprBody
import org.plank.syntax.element.ExprStmt
import org.plank.syntax.element.FunDecl
import org.plank.syntax.element.FunctionBody
import org.plank.syntax.element.FunctionTypeRef
import org.plank.syntax.element.GetExpr
import org.plank.syntax.element.GroupExpr
import org.plank.syntax.element.IdentPattern
import org.plank.syntax.element.Identifier
import org.plank.syntax.element.IfExpr
import org.plank.syntax.element.InstanceExpr
import org.plank.syntax.element.LetDecl
import org.plank.syntax.element.Location
import org.plank.syntax.element.MatchExpr
import org.plank.syntax.element.ModuleDecl
import org.plank.syntax.element.NamedTuplePattern
import org.plank.syntax.element.NoBody
import org.plank.syntax.element.Pattern
import org.plank.syntax.element.PlankElement
import org.plank.syntax.element.PlankFile
import org.plank.syntax.element.PointerTypeRef
import org.plank.syntax.element.RefExpr
import org.plank.syntax.element.ReturnStmt
import org.plank.syntax.element.SetExpr
import org.plank.syntax.element.SizeofExpr
import org.plank.syntax.element.Stmt
import org.plank.syntax.element.StructDecl
import org.plank.syntax.element.TreeWalker
import org.plank.syntax.element.TypeRef
import org.plank.syntax.element.UnitTypeRef
import org.plank.syntax.element.UseDecl
import org.plank.syntax.element.toIdentifier
import pw.binom.Stack

// TODO: add call parameters check
@Suppress("UnusedPrivateMember")
class Infer(tree: ModuleTree) :
  PlankFile.Visitor<ResolvedPlankFile>,
  Expr.Visitor<TypedExpr>,
  Stmt.Visitor<ResolvedStmt>,
  Pattern.Visitor<TypedPattern>,
  FunctionBody.Visitor<ResolvedFunctionBody>,
  TypeRef.Visitor<Ty> {
  fun analyze(file: PlankFile): ResolvedPlankFile {
    val globalScope = currentScope
    val fileModule = currentModuleTree
      .createModule(file.module, globalScope, file.program)
      .apply {
        scope = FileScope(file, globalScope)
      }

    return file
      .searchDependencies(file.module)
      .also { scopes.pushLast(fileModule.scope) }
      .map(Module::scope)
      .filterIsInstance<FileScope>()
      .map(FileScope::file)
      .asReversed()
      .map(this::visitPlankFile)
      .let { dependencies ->
        dependencies.last().copy(dependencies = dependencies.take(dependencies.size - 1))
      }
  }

  private fun PlankFile.searchDependencies(name: Identifier): List<Module> {
    return currentModuleTree.dependencies
      .apply {
        addVertex(name)

        val dependencyTreeWalker = object : TreeWalker() {
          override fun visitUseDecl(decl: UseDecl) {
            addEdge(name, decl.path.toIdentifier())
          }
        }

        dependencyTreeWalker.walk(this@searchDependencies)
      }
      .depthFirstSearch(name)
      .mapNotNull(currentModuleTree::findModule)
  }

  override fun visitPlankFile(file: PlankFile): ResolvedPlankFile {
    val program = visitStmts(file.program).filterIsInstance<ResolvedDecl>()

    return ResolvedPlankFile(file, program, bindingViolations = violations.toList())
  }

  override fun visitBlockExpr(expr: BlockExpr): TypedExpr {
    return scoped {
      val value = expr.value?.let(::visitExpr) ?: unitValue()
      val stmts = visitStmts(expr.stmts)

      TypedBlockExpr(stmts, value, references, value.ty, expr.location)
    }
  }

  override fun visitMatchExpr(expr: MatchExpr): TypedExpr {
    val subject = visitExpr(expr.subject)

    val patterns = expr.patterns
      .entries
      .associate { (pattern, value) ->
        scoped(ClosureScope(Identifier("match"), currentScope)) {
          deconstruct(
            pattern,
            subject,
            findTyInfo(subject.ty) ?: return pattern.violate("Unresolved type ${subject.ty}"),
          )

          visitPattern(pattern) to visitExpr(value)
        }
      }

    val value = patterns.values.reduce { acc, next ->
      if (acc.ty != next.ty) {
        return next.violate("Mismatch types: expecting ${acc.ty}, but got ${next.ty}")
      }

      next
    }

    return TypedMatchExpr(subject, patterns, value.ty, expr.location)
  }

  override fun visitIfExpr(expr: IfExpr): TypedExpr {
    val cond = visitExpr(expr.cond)

    if (cond.ty != boolTy) {
      return cond.violate("Mismatch types: expecting $boolTy, but got ${cond.ty}")
    }

    val thenBranch = visitExpr(expr.thenBranch)
    val elseBranch = expr.elseBranch?.let { visitExpr(it) }

    if (elseBranch == null) {
      return TypedIfExpr(cond, thenBranch, elseBranch, thenBranch.ty, expr.location)
    }

    if (thenBranch.ty != elseBranch.ty) {
      return expr.violate("Mismatch types: expecting if type ${thenBranch.ty}, but got ${elseBranch.ty}")
    }

    return TypedIfExpr(cond, thenBranch, elseBranch, thenBranch.ty, expr.location)
  }

  override fun visitConstExpr(expr: ConstExpr): TypedExpr {
    val type = when (val value = expr.value) {
      is Boolean -> boolTy
      is Unit -> unitTy
      is Int -> i32Ty
      is Short -> i16Ty
      is Byte -> i8Ty
      is Double -> doubleTy
      is Float -> floatTy
      is String -> PtrTy(charTy)
      else -> return expr.violate("Unsupported type ${value::class.simpleName}")
    }

    return TypedConstExpr(expr.value, type, expr.location)
  }

  override fun visitAccessExpr(expr: AccessExpr): TypedExpr {
    val variable = findVariable(expr.path.toIdentifier())

    if (
      !variable.isInScope &&
      variable.declaredIn !is FileScope &&
      variable.declaredIn !is GlobalScope
    ) {
      currentScope.references[variable.name] = variable.ty
    }

    return TypedAccessExpr(null, variable, expr.location)
  }

  override fun visitCallExpr(expr: CallExpr): TypedExpr {
    val callee = visitExpr(expr.callee)

    if (callee.ty !is FunTy) {
      return callee.violate("Type ${callee.ty} is not callable")
    }

    val ty = callee.ty as FunTy
    val parameters = ty.chainParameters()

    val arguments = visitExprs(expr.arguments).ifEmpty { listOf(unitValue()) }

    return arguments.foldIndexed(callee) { i, acc, argument ->
      val parameter = parameters.elementAtOrNull(i)

      if (parameter == null) {
        argument.violate("Unexpected $i arity for function $callee")
      }

      if (argument.ty != parameter) {
        argument.violate("Mismatch types: expecting $parameter but got ${argument.ty}")
      }

      TypedCallExpr(acc, argument, ty.nest(i), expr.location)
    }
  }

  override fun visitAssignExpr(expr: AssignExpr): TypedExpr {
    val variable = findVariable(expr.name)
    val value = visitExpr(expr.value)

    if (!variable.mutable) {
      return expr.violate("Can not reassign immutable variable `${variable.name.text}`")
    }

    if (variable.ty != value.ty) {
      return value.violate("Mismatch types: expecting ${variable.ty} but got ${value.ty}")
    }

    return TypedAssignExpr(null, variable.name, value, value.ty, expr.location)
  }

  override fun visitSetExpr(expr: SetExpr): TypedExpr {
    val newValue = visitExpr(expr.value)

    val receiver = when (val receiver = expr.receiver) {
      is AccessExpr -> {
        val name = receiver.path.toIdentifier()

        when (val value = currentScope.findVariable(name) ?: currentScope.findModule(name)) {
          is Module -> {
            val variable = value.scope.findVariable(expr.property)
              ?: return expr.property.violate("Unresolved property `${expr.property.text}` in module `${value.name}`")

            return TypedAccessExpr(value, variable, receiver.location)
          }
          else -> visitExpr(receiver)
        }
      }
      else -> visitExpr(receiver)
    }

    val info = findTyInfo(receiver.ty)
      ?: return receiver.violate("Unresolved type ${receiver.ty}")

    val struct = info.getAs<StructInfo>()
      ?: return receiver.violate("Can not get property `${expr.property.text}` from type $info because it is not a struct or a module")

    val property = struct.members[expr.property]
      ?: return expr.property.violate("Unresolved property `${expr.property.text}` in struct $struct")

    if (!property.mutable) {
      return expr.violate("Can not reassign immutable property `${property.name.text}` of struct $struct")
    }

    if (property.ty != newValue.ty) {
      return newValue.violate("Mismatch types: expecting ${property.ty} but got ${newValue.ty}")
    }

    return TypedSetExpr(receiver, property.name, newValue, struct, property.ty, expr.location)
  }

  override fun visitGetExpr(expr: GetExpr): TypedExpr {
    val receiver = when (val receiver = expr.receiver) {
      is AccessExpr -> {
        val name = receiver.path.toIdentifier()

        when (val value = currentScope.findVariable(name) ?: currentScope.findModule(name)) {
          is Module -> {
            val variable = value.scope.findVariable(expr.property)
              ?: return expr.property.violate("Unresolved property `${expr.property.text}` in module `${value.name}`")

            return TypedAccessExpr(value, variable, receiver.location)
          }
          else -> visitExpr(receiver)
        }
      }
      else -> visitExpr(receiver)
    }

    val info = findTyInfo(receiver.ty)
      ?: return receiver.violate("Unresolved type ${receiver.ty}")

    val struct = info.getAs<StructInfo>()
      ?: return receiver.violate("Can not get property `${expr.property.text}` from type $info because it is not a struct or a module")

    val property = struct.members[expr.property]
      ?: return expr.property.violate("Unresolved property `${expr.property.text}` in struct $struct")

    return TypedGetExpr(receiver, property.name, struct, property.ty, expr.location)
  }

  override fun visitGroupExpr(expr: GroupExpr): TypedExpr {
    return TypedGroupExpr(visitExpr(expr.value), expr.location)
  }

  override fun visitInstanceExpr(expr: InstanceExpr): TypedExpr {
    val info = findTyInfo(visitTypeRef(expr.type))
      ?: return expr.type.violate("Unresolved type ${expr.type}")

    val struct = info.getAs<StructInfo>()
      ?: return expr.type.violate("Type ${expr.type} is not a struct")

    val arguments = expr.arguments.mapValues { (name, expr) ->
      val value = visitExpr(expr)
      val property = struct.members[name]
        ?: return name.violate("Unresolved property `${name.text}` in struct $info")

      if (property.ty != value.ty) {
        return value.violate("Mismatch types: expecting ${property.ty} but got ${value.ty}")
      }

      value
    }

    return TypedInstanceExpr(arguments, struct, ConstTy(struct.name.text), expr.location)
  }

  override fun visitSizeofExpr(expr: SizeofExpr): TypedExpr {
    return TypedSizeofExpr(visitTypeRef(expr.type), expr.location)
  }

  override fun visitRefExpr(expr: RefExpr): TypedExpr {
    return TypedRefExpr(visitExpr(expr.value), expr.location)
  }

  override fun visitDerefExpr(expr: DerefExpr): TypedExpr {
    val value = visitExpr(expr.value)

    val ty = value.ty.unapply()
      ?: return expr.value.violate("Type ${value.ty} is not a pointer and can not be dereferenced")

    return TypedDerefExpr(value, ty, expr.location)
  }

  override fun visitNoBody(body: NoBody): ResolvedFunctionBody {
    return ResolvedNoBody(body.location)
  }

  override fun visitExprBody(body: ExprBody): ResolvedFunctionBody {
    return ResolvedExprBody(visitExpr(body.expr), body.location)
  }

  override fun visitCodeBody(body: CodeBody): ResolvedFunctionBody {
    return ResolvedCodeBody(visitStmts(body.stmts), body.value?.let(::visitExpr), body.location)
  }

  override fun visitNamedTuplePattern(pattern: NamedTuplePattern): TypedPattern {
    val info = currentScope.findTyInfo(pattern.type.toIdentifier())
      ?: return pattern.type.violatedPattern("Unresolved type reference `${pattern.type.text}`")

    val enum = info.getAs<EnumMemberInfo>()
      ?: return pattern.type.violatedPattern("Type $info can not be destructured")

    val properties = visitPatterns(pattern.properties)

    return TypedNamedTuplePattern(properties, enum, pattern.location)
  }

  override fun visitIdentPattern(pattern: IdentPattern): TypedPattern {
    val variable = findVariable(pattern.name)

    return TypedIdentPattern(pattern.name, variable.ty, pattern.location)
  }

  override fun visitExprStmt(stmt: ExprStmt): ResolvedStmt {
    return ResolvedExprStmt(visitExpr(stmt.expr), stmt.location)
  }

  override fun visitReturnStmt(stmt: ReturnStmt): ResolvedStmt {
    val expr = stmt.value?.let(::visitExpr) ?: unitValue()

    val functionScope = currentScope as? FunctionScope
      ?: return stmt
        .violate("Can not return in not function scope `${currentScope.name.text}`")
        .stmt()

    if (functionScope.function.returnTy != expr.ty) {
      return stmt
        .violate("Mismatch types: expecting return type ${functionScope.returnTy}, but got ${expr.ty}")
        .stmt()
    }

    return ResolvedReturnStmt(expr, stmt.location)
  }

  override fun visitUseDecl(decl: UseDecl): ResolvedStmt {
    val module = currentScope.findModule(decl.path.toIdentifier())
      ?: return decl.violate("Unresolved module `${decl.path.text}`").stmt()

    return ResolvedUseDecl(module, decl.location)
  }

  override fun visitModuleDecl(decl: ModuleDecl): ResolvedStmt {
    val module = currentModuleTree.createModule(
      name = decl.path.toIdentifier(),
      enclosing = currentScope,
      content = decl.content,
    )

    val content = scoped(module.scope) {
      visitStmts(decl.content).filterIsInstance<ResolvedDecl>()
    }

    return ResolvedModuleDecl(decl.path, content, decl.location)
  }

  override fun visitEnumDecl(decl: EnumDecl): ResolvedStmt {
    currentScope.create(EnumInfo(decl.name))

    val enum = ConstTy(decl.name.text)

    val members = decl.members.associate { (name, parameters) ->
      val types = visitTypeRefs(parameters)

      val memberInfo = currentScope.create(EnumMemberInfo(name, types, FunTy(enum, types)))

      if (types.isEmpty()) {
        currentScope.declare(name, enum)
      } else {
        currentScope.declare(name, FunTy(enum, types))
      }

      name to memberInfo
    }

    val info = currentScope.create(EnumInfo(decl.name, members))

    return ResolvedEnumDecl(info, enum, decl.location)
  }

  override fun visitStructDecl(decl: StructDecl): ResolvedStmt {
    currentScope.create(StructInfo(decl.name))

    val properties = decl.properties.associate { (mutable, name, type) ->
      name to StructMemberInfo(name, visitTypeRef(type), mutable)
    }

    val info = currentScope.create(StructInfo(decl.name, properties))

    return ResolvedStructDecl(info, decl.location)
  }

  override fun visitFunDecl(decl: FunDecl): ResolvedStmt {
    val name = decl.name
    val attributes = decl.attributes // todo validate

    val parameters = decl.parameters
      .mapValues { visitTypeRef(it.value) }
      .ifEmpty { mapOf(Identifier("___") to unitTy) }

    val returnType = visitTypeRef(decl.returnType)

    val info = FunctionInfo(name, returnType, parameters)
    val ty = FunTy(returnType, parameters.values)

    val isNested = !currentScope.isTopLevelScope
    val references = linkedMapOf<Identifier, Ty>()

    currentScope.declare(name, ty)

    val scope = FunctionScope(info, name, currentScope, currentModuleTree, references)
    val body = scoped(name, scope) {
      decl.parameters
        .mapKeys { it.key }
        .forEach { (name, type) ->
          declare(name, visitTypeRef(type))
        }

      visitFunctionBody(decl.body)
    }

    return ResolvedFunDecl(body, attributes, references, info, isNested, ty, decl.location)
  }

  override fun visitLetDecl(decl: LetDecl): ResolvedStmt {
    val name = decl.name
    val mutable = decl.mutable
    val value = visitExpr(decl.value)
    val ty = decl.type?.let(::visitTypeRef) ?: value.ty
    val isNested = !currentScope.isTopLevelScope

    if (ty != value.ty) {
      return value
        .violate("Mismatch types: expecting $ty but got ${value.ty}")
        .stmt()
    }

    currentScope.declare(name, value, mutable)

    return ResolvedLetDecl(name, mutable, value, isNested, value.ty, decl.location)
  }

  override fun visitAccessTypeRef(ref: AccessTypeRef): Ty {
    val path = ref.path.toIdentifier()

    if (currentScope.findTyInfo(path) == null) {
      return ref.violate("Type $path is not defined").ty
    }

    return ConstTy(ref.path.text)
  }

  override fun visitPointerTypeRef(ref: PointerTypeRef): Ty {
    return PtrTy(visitTypeRef(ref.type))
  }

  override fun visitArrayTypeRef(ref: ArrayTypeRef): Ty {
    return ArrTy(visitTypeRef(ref.type))
  }

  override fun visitFunctionTypeRef(ref: FunctionTypeRef): Ty {
    return FunTy(visitTypeRef(ref.returnType), visitTypeRef(ref.returnType))
  }

  override fun visitUnitTypeRef(ref: UnitTypeRef): Ty {
    return unitTy
  }

  private fun unitValue(): TypedExpr {
    return TypedConstExpr(Unit, unitTy, Location.Generated)
  }

  private fun PlankElement.violatedPattern(message: String): TypedPattern {
    violations += BindingViolation(message, location)

    return TypedIdentPattern(Identifier("<error>"), undefTy, location)
  }

  private fun PlankElement.violate(message: String): TypedExpr {
    violations += BindingViolation(message, location)

    return TypedConstExpr(Unit, undefTy, location)
  }

  private fun undeclared(ty: Ty): TypedExpr {
    return TypedConstExpr(Unit, ty, Location.Generated)
  }

  private val scopes = Stack<Scope>().also { stack ->
    stack.pushLast(GlobalScope(tree))
  }

  private val violations = mutableSetOf<BindingViolation>()

  private val currentScope get() = scopes.peekLast()
  private val currentModuleTree get() = scopes.peekLast().moduleTree

  private fun Scope.deconstruct(
    pattern: Pattern,
    subject: TypedExpr,
    info: TyInfo,
  ) {
    when (pattern) {
      is IdentPattern -> {
        info.getAs<EnumInfo>()?.members?.get(pattern.name) ?: return declare(pattern.name, subject)
      }
      is NamedTuplePattern -> {
        val enum = info.getAs<EnumInfo>() ?: return run {
          subject.violate("Expecting a enum type with named tuple pattern, but got ${subject.ty}")
        }

        val member = enum.members[pattern.type.toIdentifier()] ?: return run {
          pattern.type.violate("Unresolved enum variant `${pattern.type.text}` of `${name.text}`")
        }

        pattern.properties.forEachIndexed { index, subPattern ->
          val subType = member.parameters.getOrNull(index) ?: return run {
            subPattern.violatedPattern("Expecting ${member.parameters.size} fields when matching `${member.name.text}`, but got $index fields instead")
          }

          deconstruct(subPattern, undeclared(subType), enum)
        }
      }
    }
  }

  private fun findTyInfo(ty: Ty): TyInfo? {
    return when (ty) {
      is AppTy -> null
      is FunTy -> null
      is PtrTy -> null
      is ArrTy -> null
      is ConstTy -> currentScope.findTyInfo(ty.name.toIdentifier())
      is VarTy -> currentScope.findTyInfo(ty.name.toIdentifier())
    }
  }

  private fun findVariable(name: Identifier): Variable {
    return currentScope.findVariable(name)
      ?: Variable(false, name, name.violate("Unresolved variable `${name.text}`").ty, currentScope)
  }

  private inline fun <T> scoped(scope: Scope, body: Scope.() -> T): T {
    scopes.pushLast(scope)
    val result = body(scope)
    scopes.popLast()

    return result
  }

  private inline fun <T> scoped(
    name: Identifier = Identifier("anonymous"),
    scope: Scope = ClosureScope(name, scopes.peekLast()),
    body: Scope.() -> T
  ): T {
    scopes.pushLast(scope)
    val result = body(scope)
    scopes.popLast()

    return result
  }
}
