package org.plank.codegen

import kotlin.test.Test

class GlobalVariableTests {
  @Test
  fun `test create global variable`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        let x = "hello, world";

        fun main(argc: Int32, argv: **Char): Void {
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }

  @Test
  fun `test create and use global variable`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        let x = "hello, world";

        fun main(argc: Int32, argv: **Char): Void {
          println(x);
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }

  @Test
  fun `test create and use global variable 2 times`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        let x = "hello, world";

        fun main(argc: Int32, argv: **Char): Void {
          println(x);
          println(x);
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }

  @Test
  fun `test create and use global variable with function`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        fun buildString(): *Char {
          return "hello, world";
        }

        let x = buildString();

        fun main(argc: Int32, argv: **Char): Void {
          println(x);
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }
}