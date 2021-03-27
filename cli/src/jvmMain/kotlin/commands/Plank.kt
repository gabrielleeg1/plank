package com.lorenzoog.plank.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.lorenzoog.plank.analyzer.DefaultBindingContext
import com.lorenzoog.plank.analyzer.render
import com.lorenzoog.plank.cli.compiler.CompileError
import com.lorenzoog.plank.cli.compiler.CompilerOptions
import com.lorenzoog.plank.cli.compiler.PlankCompiler
import com.lorenzoog.plank.cli.compiler.Target.Llvm
import com.lorenzoog.plank.cli.message.ColoredMessageRenderer
import com.lorenzoog.plank.cli.pkg.Package
import com.lorenzoog.plank.cli.utils.asFile
import com.lorenzoog.plank.compiler.PlankLLVM
import com.lorenzoog.plank.grammar.element.PlankFile
import com.lorenzoog.plank.grammar.mapper.render
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import pw.binom.io.file.File
import pw.binom.io.file.asBFile

@ExperimentalPathApi
class Plank : CliktCommand() {
  private val file by argument("file")
    .help("The target file")
    .convert { File(it) }

  private val target by option("--target")
    .convert { target ->
      when (target) {
        "llvm" -> Llvm
        else -> fail("Unreconized target $target")
      }
    }
    .default(Llvm)

  private val pkgName by option("--pkg-name")
    .help("The package name")
    .default("Main")

  private val pkgKind by option("--pkg-kind")
    .help("The package kind")
    .convert { type ->
      when (type) {
        "lib" -> Package.Kind.Library
        "bin" -> Package.Kind.Binary
        else -> fail("Invalid package kind: $type")
      }
    }
    .default(Package.Kind.Binary)

  private val output by option("--output", "-O")
    .help("Output file")
    .file()
    .convert { it.asBFile }
    .required()

  private val debug by option("--debug", "-D")
    .help("Sets the compiler on debug mode")
    .flag()

  private val emitIR by option("--emit-ir")
    .help("Emits the ir code when compiling")
    .flag()

  private val include by option("--include", "-I")
    .help("Include files")
    .convert { path -> PlankFile.of(File(path)) }
    .multiple()

  override fun run() {
    val renderer = ColoredMessageRenderer(flush = true)

    val plankHome = System.getenv("PLANK_HOME")
      ?.let { File(it) }
      ?: return renderer.severe("Define the PLANK_HOME before compile")

    val options = CompilerOptions(plankHome).apply {
      debug = this@Plank.debug
      emitIR = this@Plank.emitIR
      dist = "build_${pkgName}_${System.currentTimeMillis()}"
        .let(::createTempDirectory)
        .asFile()

      output = this@Plank.output
    }

    val pkg = Package(
      name = pkgName,
      options = options,
      kind = when (pkgKind) {
        Package.Kind.Binary -> pkgKind
        Package.Kind.Library -> TODO("unsupported library kind yet")
      },
      main = PlankFile.of(file),
      include = include + options.stdlib
    )

    val context = DefaultBindingContext(pkg.tree)
    val llvm = PlankLLVM(pkg.tree, context)

    val compiler = PlankCompiler(pkg, context, llvm, renderer)
    try {
      compiler.compile()
      renderer.info("Successfully compiled $output")
    } catch (error: Throwable) {
      when (error) {
        is CompileError.BindingViolations -> {
          renderer.severe("Please resolve the following issues before compile:")
          error.violations.render(renderer)
        }

        is CompileError.IRViolations -> {
          renderer.severe("Internal compiler error, please open an issue.")
          error.violations.forEach { (element, message) ->
            renderer.severe(message, element?.location)
          }
          if (debug) {
            renderer.info("LLVM Module:")
            println(error.module.getAsString())
          }
        }

        is CompileError.SyntaxViolations -> {
          renderer.severe("Please resolve the following issues before compile:")
          error.violations.render(renderer)
        }

        is CompileError.FailedCommand -> {
          renderer.severe("Could not execute '${error.command}'. Failed with exit code: ${error.exitCode}") // ktlint-disable max-line-length
        }
        else -> {
          renderer.severe("${error::class.simpleName}: ${error.message}")
        }
      }
    }
  }
}
