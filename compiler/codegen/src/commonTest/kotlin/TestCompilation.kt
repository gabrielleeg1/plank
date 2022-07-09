@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package org.plank.codegen

import okio.Path.Companion.toPath
import org.plank.analyzer.checker.CheckViolation
import org.plank.codegen.pkg.AnalyzerError
import org.plank.codegen.pkg.Command
import org.plank.codegen.pkg.CommandFailedException
import org.plank.codegen.pkg.Package
import org.plank.codegen.pkg.SyntaxError
import org.plank.codegen.pkg.child
import org.plank.codegen.pkg.compileBinary
import org.plank.codegen.pkg.createTempDirectory
import org.plank.codegen.pkg.exec
import org.plank.codegen.pkg.locateBinary
import org.plank.llvm4k.LLVMError
import org.plank.syntax.SyntaxViolation
import org.plank.syntax.message.CompilerLogger
import org.plank.syntax.message.lineSeparator
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class TestCompilation(
  private val pkg: Package,
  private val syntaxViolations: Set<SyntaxViolation>,
  private val checkViolations: Set<CheckViolation>,
  private val exitCode: Int,
) {
  fun expectSyntaxViolation(message: String): TestCompilation = apply {
    assertNotNull(syntaxViolations.find { it.message == message })
  }

  fun expectBindingViolation(message: String): TestCompilation = apply {
    assertNotNull(checkViolations.find { it.message == message })
  }

  fun expectExitCode(actual: Int): TestCompilation = apply {
    assertEquals(actual, exitCode)
  }

  fun expectSuccess(): TestCompilation = apply {
    if (syntaxViolations.isNotEmpty()) {
      pkg.logger.severe("Syntax violations:")
      syntaxViolations.forEach { it.render(pkg.logger) }
      pkg.logger.severe()
    }

    if (checkViolations.isNotEmpty()) {
      pkg.logger.severe("Binding violations:")
      checkViolations.forEach { it.render(pkg.logger) }
      pkg.logger.severe()
    }

    expectExitCode(0)

    if (checkViolations.isNotEmpty() || syntaxViolations.isNotEmpty()) {
      fail("Compilation failed")
    }
  }

  class Builder(private val code: String) {
    private val options = DebugOptions()

    fun debugTree(): Builder = apply { options.treeDebug = true }
    fun debugPlainAst(): Builder = apply { options.plainAstDebug = true }
    fun debugResolvedTree(): Builder = apply { options.resolvedTreeDebug = true }
    fun debugPretty(): Builder = apply { options.prettyDebug = true }
    fun debugLlvmIR(): Builder = apply { options.llvmIrDebug = true }
    fun debugParser(): Builder = apply { options.parserDebug = true }
    fun debugCompilation(): Builder = apply { options.compilationDebug = true }
    fun linkerVerbose(): Builder = apply { options.linkerVerbose = true }

    fun debugAll(): Builder = apply {
      debugPretty()
      debugLlvmIR()
      debugCompilation()
    }

    @Suppress("PrintStackTrace", "TooGenericExceptionCaught")
    fun runTest(compilation: TestCompilation.() -> Unit = {}): TestCompilation {
      installDebugPretty()

      val pkg = Package(code, "../..".toPath()) {
        linker = locateBinary("clang++")
        workingDir = createTempDirectory("plank-test")
        output = workingDir.child("main")
        debug = options
        logger = CompilerLogger(debug = true, verbose = true)
      }

      var syntaxViolations: Set<SyntaxViolation> = emptySet()
      var analyzerViolations: Set<CheckViolation> = emptySet()
      var exitCode = 0

      try {
        val binary = pkg.compileBinary()

        Command
          .of(binary).exec()
          .split(lineSeparator)
          .forEach {
            pkg.info(it)
          }
      } catch (error: CommandFailedException) {
        exitCode = error.exitCode
      } catch (error: AnalyzerError) {
        analyzerViolations = error.violations
      } catch (error: SyntaxError) {
        syntaxViolations = error.violations
      } catch (error: CodegenError) {
        pkg.severe("Codegen Error: ${error.message}:")
        pkg.severe(error.context.currentModule.toString())
        runCatching { error.context.currentModule.verify() }.onFailure { failure ->
          when (failure) {
            is LLVMError -> {
              pkg.severe()
              pkg.severe("LLVM Error:")
              pkg.severe(failure.message)
            }
          }
        }
        throw error
      } catch (error: Throwable) {
        error.printStackTrace()
        throw error
      }

      return TestCompilation(pkg, syntaxViolations, analyzerViolations, exitCode)
        .apply(compilation)
    }
  }

  companion object {
    fun of(code: String): Builder {
      return Builder(code)
    }
  }
}