package com.lorenzoog.plank.grammar.mapper

import com.lorenzoog.plank.grammar.element.PlankFile
import com.lorenzoog.plank.grammar.utils.location
import org.antlr.v4.kotlinruntime.BaseErrorListener
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.Recognizer

class SyntaxErrorListener(private val file: PlankFile) : BaseErrorListener() {
  private val _violations = mutableListOf<SyntaxViolation>()

  val violations: List<SyntaxViolation> get() = _violations

  override fun syntaxError(
    recognizer: Recognizer<*, *>,
    offendingSymbol: Any?,
    line: Int,
    charPositionInLine: Int,
    msg: String,
    e: RecognitionException?
  ) {
    if (e != null) {
      _violations += RecognitionViolation(msg, e.offendingToken.location(file))
    }
  }
}