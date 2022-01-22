package com.gabrielleeg1.plank.compiler

import org.junit.jupiter.api.Test

class HelloWorldTests {
  @Test
  fun `test hello world`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        fun main(argc: Int32, argv: **Char): Void {
          println("Hello, world!");
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }
}