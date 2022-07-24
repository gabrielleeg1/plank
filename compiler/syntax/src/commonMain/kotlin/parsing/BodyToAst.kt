package org.plank.syntax.parsing

import org.plank.parser.PlankParser.CodeBodyContext
import org.plank.parser.PlankParser.ExprBodyContext
import org.plank.parser.PlankParser.FunctionBodyContext
import org.plank.parser.PlankParser.NoBodyContext
import org.plank.syntax.element.CodeBody
import org.plank.syntax.element.ExprBody
import org.plank.syntax.element.FunctionBody
import org.plank.syntax.element.NoBody
import org.plank.syntax.element.PlankFile

fun FunctionBodyContext.bodyToAst(file: PlankFile): FunctionBody = when (this) {
  is NoBodyContext -> NoBody(treeLoc(file))
  is ExprBodyContext -> ExprBody(value!!.exprToAst(file), treeLoc(file))
  is CodeBodyContext -> {
    CodeBody(findStmt().map { it.stmtToAst(file) }, value?.exprToAst(file), treeLoc(file))
  }

  else -> error("Unsupported function body ${this::class.simpleName}")
}
