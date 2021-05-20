package com.lorenzoog.plank.analyzer

import com.lorenzoog.plank.grammar.element.Decl
import com.lorenzoog.plank.grammar.element.Decl.FunDecl.Modifier
import com.lorenzoog.plank.grammar.element.Expr
import com.lorenzoog.plank.grammar.element.Expr.Binary.Operation.Add
import com.lorenzoog.plank.grammar.element.Expr.Binary.Operation.Div
import com.lorenzoog.plank.grammar.element.Expr.Binary.Operation.Mul
import com.lorenzoog.plank.grammar.element.Expr.Binary.Operation.Sub
import com.lorenzoog.plank.grammar.element.Expr.Logical.Operation.Equals
import com.lorenzoog.plank.grammar.element.Expr.Logical.Operation.Greater
import com.lorenzoog.plank.grammar.element.Expr.Logical.Operation.GreaterEquals
import com.lorenzoog.plank.grammar.element.Expr.Logical.Operation.Less
import com.lorenzoog.plank.grammar.element.Expr.Logical.Operation.LessEquals
import com.lorenzoog.plank.grammar.element.Expr.Logical.Operation.NotEquals
import com.lorenzoog.plank.grammar.element.Expr.Unary.Operation.Bang
import com.lorenzoog.plank.grammar.element.Expr.Unary.Operation.Neg
import com.lorenzoog.plank.grammar.element.Location
import com.lorenzoog.plank.grammar.element.PlankElement
import com.lorenzoog.plank.grammar.element.PlankFile
import com.lorenzoog.plank.grammar.element.Stmt
import com.lorenzoog.plank.grammar.element.TypeDef
import com.lorenzoog.plank.grammar.element.visit
import com.lorenzoog.plank.grammar.tree.TreeWalker
import com.lorenzoog.plank.shared.depthFirstSearch
import pw.binom.Stack

class BindingContext(moduleTree: ModuleTree) :
  Stmt.Visitor<PlankType>,
  Expr.Visitor<PlankType>,
  TypeDef.Visitor<PlankType>,
  PlankFile.Visitor<PlankType> {
  private val bindings = mutableMapOf<PlankElement, Scope>()

  private val scopes = Stack<Scope>().also { stack ->
    stack.pushLast(GlobalScope(moduleTree))
  }

  private val _violations = mutableSetOf<BindingViolation>()
  val violations: List<BindingViolation>
    get() = _violations.toList()

  val isValid: Boolean get() = violations.isEmpty()

  fun analyze(file: PlankFile): Boolean {
    val globalScope = scopes.peekLast()
    val fileModule = currentModuleTree
      .createModule(file.module, globalScope, file.program)
      .apply {
        scope = FileScope(file, globalScope)
      }

    file.searchDependencies(file.module)
      .also { scopes.pushLast(fileModule.scope) }
      .map(Module::scope)
      .filterIsInstance<FileScope>()
      .map(FileScope::file)
      .asReversed()
      .forEach(this::visit)

    return isValid
  }

  private fun PlankElement.searchDependencies(name: String): List<Module> {
    return currentModuleTree.dependencies
      .apply {
        addVertex(name)

        val dependencyTreeWalker = object : TreeWalker() {
          override fun visitImportDecl(importDecl: Decl.ImportDecl) {
            addEdge(name, importDecl.module.text)
          }
        }

        dependencyTreeWalker.walk(this@searchDependencies)
      }
      .depthFirstSearch(name)
      .mapNotNull(currentModuleTree::findModule)
  }

  override fun visitIfExpr(anIf: Expr.If): PlankType = anIf.bind {
    val cond = visit(anIf.cond)
    if (!Builtin.Bool.isAssignableBy(cond)) {
      _violations += TypeViolation(Builtin.Bool, cond, anIf.cond.location)
    }

    val thenBranch = visit(anIf.thenBranch).lastOrNull() ?: return@bind Builtin.Void

    visit(anIf.elseBranch).lastOrNull() ?: run {
      if (!Builtin.Void.isAssignableBy(thenBranch)) {
        _violations += TypeViolation(thenBranch, Builtin.Void, anIf.location)
      }

      thenBranch
    }
  }

  override fun visitConstExpr(const: Expr.Const): PlankType = const.bind {
    when (const.value) {
      is Boolean -> Builtin.Bool
      is Unit -> Builtin.Void
      is String -> Builtin.Char.pointer
      is Int,
      is Short,
      is Byte -> Builtin.Int
      is Double,
      is Long,
      is Float -> Builtin.Double
      else -> Builtin.Any
    }
  }

  override fun visitLogicalExpr(logical: Expr.Logical): PlankType = logical.bind {
    val lhs = visit(logical.lhs)
    val op = when (logical.op) {
      Equals -> Builtin.Any
      NotEquals -> Builtin.Any
      Greater -> Builtin.Numeric
      GreaterEquals -> Builtin.Numeric
      Less -> Builtin.Numeric
      LessEquals -> Builtin.Numeric
    }
    val rhs = visit(logical.rhs)

    if (!op.isAssignableBy(lhs)) {
      _violations += TypeViolation(op, lhs, logical.lhs.location)
    }

    if (!op.isAssignableBy(rhs)) {
      _violations += TypeViolation(op, rhs, logical.rhs.location)
    }

    Builtin.Bool
  }

  override fun visitConcatExpr(concat: Expr.Concat): PlankType {
    val lhs = visit(concat.lhs)
    val rhs = visit(concat.rhs)

    if (!Builtin.String.isAssignableBy(lhs) || !Builtin.String.isAssignableBy(rhs)) {
      _violations += TypeViolation(Builtin.String, lhs, concat.location)
    }

    return Builtin.String
  }

  override fun visitBinaryExpr(binary: Expr.Binary): PlankType = binary.bind {
    val lhs = visit(binary.lhs)
    val op = when (binary.op) {
      Add -> Builtin.Numeric
      Sub -> Builtin.Numeric
      Mul -> Builtin.Numeric
      Div -> Builtin.Numeric
    }
    val rhs = visit(binary.rhs)

    if (!op.isAssignableBy(lhs)) {
      _violations += TypeViolation(op, lhs, binary.lhs.location)
    }

    if (!op.isAssignableBy(rhs)) {
      _violations += TypeViolation(op, rhs, binary.rhs.location)
    }

    when {
      Builtin.Int.isAssignableBy(lhs) && Builtin.Int.isAssignableBy(rhs) -> Builtin.Int
      Builtin.Char.pointer.isAssignableBy(rhs) -> Builtin.Char.pointer
      else -> Builtin.Double
    }
  }

  override fun visitUnaryExpr(unary: Expr.Unary): PlankType = unary.bind {
    val op = when (unary.op) {
      Neg -> Builtin.Numeric
      Bang -> Builtin.Bool
    }
    val rhs = visit(unary.rhs)

    if (!op.isAssignableBy(rhs)) {
      _violations += TypeViolation(op, rhs, unary.rhs.location)
    }

    op
  }

  override fun visitCallExpr(call: Expr.Call): PlankType = call.bind {
    val callee = findCallee(call.callee) ?: return@bind Builtin.Any

    call.arguments.forEachIndexed { index, argument ->
      val expected = callee.parameters[index]
      val found = visit(argument)

      if (!expected.isAssignableBy(found)) {
        _violations += TypeViolation(expected, found, call.location)
      }
    }

    callee.returnType
  }

  override fun visitAssignExpr(assign: Expr.Assign): PlankType = assign.bind {
    val name = assign.name.text
    val variable = scopes.peekLast().findVariable(name) ?: return@bind run {
      _violations += UnresolvedVariableViolation(name, assign.location)

      Builtin.Any
    }
    val actual = visit(assign.value)

    if (!variable.mutable) {
      _violations += AssignImmutableViolation(name, assign.location)
    }

    if (!variable.type.isAssignableBy(actual)) {
      _violations += TypeViolation(variable, actual, assign.location)
    }

    variable.type
  }

  override fun visitSetExpr(set: Expr.Set): PlankType = set.bind {
    val member = set.member.text
    val structure = findReceiver(set.receiver)
    val expected = structure[member] ?: return@bind run {
      _violations += TypeViolation(Builtin.Any, Builtin.Void, set.location)

      Builtin.Any
    }
    val actual = visit(set.value)

    if (!expected.mutable) {
      _violations += AssignImmutableViolation(member, set.location)
    }

    if (!expected.type.isAssignableBy(actual)) {
      _violations += TypeViolation(expected, actual, set.location)
    }

    expected.type
  }

  override fun visitGetExpr(get: Expr.Get): PlankType = get.bind {
    val member = get.member.text
    val structure = findReceiver(get.receiver)

    structure[member]?.type ?: run {
      _violations += TypeViolation(Builtin.Any, Builtin.Void, get.location)

      Builtin.Any
    }
  }

  private fun findReceiver(expr: Expr): PlankType = when (expr) {
    is Expr.Access -> currentScope.findVariable(expr.name.text)?.type
      ?: run {
        val module = findModule(expr) ?: return@run run {
          _violations += UnresolvedModuleViolation(expr.name.text, expr.location)

          Builtin.Any
        }

        module.type
      }
    else -> visit(expr)
  }

  override fun visitGroupExpr(group: Expr.Group): PlankType = group.bind {
    visit(group.expr)
  }

  override fun visitInstanceExpr(instance: Expr.Instance): PlankType = instance.bind {
    val structure = findStruct(Expr.Access(instance.name, instance.location))
      ?: return@bind Builtin.Any

    instance.arguments.forEach { (token, value) ->
      val name = token.text.orEmpty()
      val expected = structure.fields.find { it.name == name }?.type ?: run {
        _violations += UnresolvedVariableViolation(name, value.location)

        Builtin.Any
      }
      val actual = visit(value)

      if (!expected.isAssignableBy(actual)) {
        _violations += TypeViolation(expected, actual, value.location)
      }
    }

    structure
  }

  override fun visitSizeofExpr(sizeof: Expr.Sizeof): PlankType = sizeof.bind {
    findStruct(Expr.Access(sizeof.name, sizeof.location))

    Builtin.Int
  }

  override fun visitReferenceExpr(reference: Expr.Reference): PlankType = reference.bind {
    PlankType.Pointer(visit(reference.expr))
  }

  override fun visitValueExpr(value: Expr.Value): PlankType = value.bind {
    val ptr = visit(value.expr)
    if (ptr !is PlankType.Pointer) {
      _violations += TypeViolation("ptr", ptr, value.location)

      return@bind Builtin.Any
    }

    ptr.inner
  }

  override fun visitGenericAccess(access: TypeDef.GenericAccess): PlankType = access.bind {
    Builtin.Any
  }

  override fun visitGenericUse(use: TypeDef.GenericUse): PlankType = use.bind {
    val receiver = visit(use.receiver)
    val arguments = visit(use.arguments)

    if (receiver.genericArity > arguments.size) {
      _violations += UnexpectedGenericArgument(receiver.genericArity, arguments.size, use.location)
    }

    PlankType.Generic(receiver, arguments)
  }

  override fun visitExprStmt(exprStmt: Stmt.ExprStmt): PlankType = exprStmt.bind {
    visit(exprStmt.expr)
  }

  override fun visitReturnStmt(returnStmt: Stmt.ReturnStmt): PlankType = returnStmt.bind {
    visit(returnStmt.value ?: return@bind Builtin.Void)
  }

  override fun visitModuleDecl(moduleDecl: Decl.ModuleDecl): PlankType = moduleDecl.bind {
    val module = currentModuleTree
      .createModule(moduleDecl.name.text, scopes.peekLast(), moduleDecl.content)

    scoped(module.scope) {
      visit(moduleDecl.content)
    }

    Builtin.Void
  }

  override fun visitStructDecl(structDecl: Decl.StructDecl): PlankType = structDecl.bind {
    val struct = PlankType.Delegate(PlankType.Struct(structDecl.name.text))

    scopes.peekLast().create(structDecl.name.text, struct)

    struct.delegate = PlankType.Struct(
      structDecl.name.text,
      structDecl.fields.map {
        PlankType.Struct.Field(it.mutable, it.name.text, visit(it.type))
      }
    )

    struct.delegate!!
  }

  override fun visitEnumDecl(enumDecl: Decl.EnumDecl): PlankType = enumDecl.bind {
    val enum = PlankType.Delegate(PlankType.Pointer(PlankType.Set(enumDecl.name.text)))

    currentScope.create(enumDecl.name.text, enum)

    enum.delegate = PlankType.Set(
      enumDecl.name.text,
      enumDecl.members.map { (name, fields) ->
        currentScope.declare(name.text, PlankType.Callable(visit(fields), enum))

        PlankType.Set.Member(name.text, visit(fields))
      }
    )

    enum
  }

  override fun visitFunDecl(funDecl: Decl.FunDecl): PlankType = funDecl.bind {
    val name = funDecl.name.text
    val returnType = funDecl.returnType?.let { visit(it) } ?: Builtin.Void
    val type = PlankType.Callable(
      parameters = funDecl.parameters.map { visit(it) },
      returnType = funDecl.returnType?.let { visit(it) } ?: Builtin.Void
    )

    scopes.peekLast().declare(name, type)

    scoped(name, FunctionScope(name, scopes.peekLast(), type)) { scope ->
      funDecl.realParameters
        .mapKeys { it.key.text.orEmpty() }
        .forEach { (name, type) ->
          scope.declare(name, visit(type))
        }

      val body = visit(funDecl.body)

      if (Modifier.Native !in funDecl.modifiers) {
        _violations += body
          .filterIsInstance<Stmt.ReturnStmt>()
          .filterNot { returnType.isAssignableBy(visit(it)) }
          .map { stmt ->
            TypeViolation(returnType, visit(stmt), stmt.location)
          }
      }

      type
    }
  }

  override fun visitLetDecl(letDecl: Decl.LetDecl): PlankType = letDecl.bind {
    val name = letDecl.name.text

    scopes.peekLast().declare(
      name,
      visit(letDecl.type) { visit(letDecl.value) },
      letDecl.mutable
    )

    Builtin.Void
  }

  override fun visitNameTypeDef(name: TypeDef.Name): PlankType = name.bind {
    val text = name.name.text

    scopes.peekLast().findType(text) ?: return@bind run {
      _violations += UnresolvedTypeViolation(text, name.location)

      Builtin.Void
    }
  }

  override fun visitPtrTypeDef(ptr: TypeDef.Ptr): PlankType = ptr.bind {
    PlankType.Pointer(visit(ptr.type))
  }

  override fun visitArrayTypeDef(array: TypeDef.Array): PlankType = array.bind {
    PlankType.Array(visit(array.type))
  }

  override fun visitFunctionTypeDef(function: TypeDef.Function): PlankType = function.bind {
    PlankType.Callable(
      parameters = function.parameters.map { visit(it) },
      returnType = function.returnType?.let { visit(it) } ?: Builtin.Void
    )
  }

  override fun visitPlankFile(file: PlankFile): PlankType = file.bind {
    visit(file.program)

    Builtin.Void
  }

  override fun visitImportDecl(importDecl: Decl.ImportDecl): PlankType = importDecl.bind {
    val name = importDecl.module.text

    val module = findModule(name, importDecl.location) ?: return@bind run {
      _violations += UnresolvedModuleViolation(name, importDecl.location)

      Builtin.Void
    }

    scopes.peekLast().expand(module.scope)

    Builtin.Void
  }

  override fun visitAccessExpr(access: Expr.Access): PlankType = access.bind {
    val name = access.name.text

    scopes.peekLast().findVariable(name)?.type ?: run {
      _violations += UnresolvedVariableViolation(name, access.location)

      Builtin.Any
    }
  }

  // utils
  private val currentScope get() = scopes.peekLast()
  private val currentModuleTree get() = scopes.peekLast().moduleTree

  private fun PlankElement.bind(genType: () -> PlankType): PlankType {
    val type = genType()
    bindings[this] = currentScope
    return type
  }

  private inline fun <T> scoped(
    scope: Scope,
    body: (Scope) -> T
  ): T {
    scopes.pushLast(scope)
    val result = body(scope)
    scopes.popLast()

    return result
  }

  private inline fun <T> scoped(
    name: String = "anonymous",
    scope: Scope = ClosureScope(name, scopes.peekLast()),
    body: (Scope) -> T
  ): T {
    scopes.pushLast(scope)
    val result = body(scope)
    scopes.popLast()

    return result
  }

  private fun findCallee(expr: Expr): PlankType.Callable? {
    return when (expr) {
      is Expr.Access -> scopes.peekLast().findFunction(expr.name.text).also {
        if (it == null) {
          _violations += UnresolvedVariableViolation(expr.name.text, expr.location)
        } else {
          expr.bind { it }
        }
      }
      else -> {
        val actual = expr.bind { visit(expr) }
        if (actual is PlankType.Callable) {
          actual
        } else {
          _violations += TypeViolation("PkCallable", actual, expr.location)

          return null
        }
      }
    }
  }

  fun findStruct(expr: Expr): PlankType? {
    return when (expr) {
      is Expr.Access -> scopes.peekLast().findStructure(expr.name.text).also {
        if (it == null) {
          _violations += UnresolvedTypeViolation(expr.name.text, expr.location)
        } else {
          expr.bind { it }
        }
      }
      else -> {
        val value = expr.bind { visit(expr) }
        if (value is PlankType.Struct) {
          value
        } else {
          _violations += TypeViolation("PkStructure", value, expr.location)

          return null
        }
      }
    }
  }

  private fun findModule(name: String, location: Location): Module? {
    return currentScope.findModule(name) ?: run {
      _violations += UnresolvedModuleViolation(name, location)

      null
    }
  }

  private fun findModule(expr: Expr): Module? {
    return when (expr) {
      is Expr.Access -> findModule(expr.name.text, expr.location)
      else -> null
    }
  }
}
