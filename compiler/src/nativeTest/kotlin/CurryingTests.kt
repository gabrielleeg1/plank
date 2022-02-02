package com.gabrielleeg1.plank.compiler

import kotlin.test.Test

class CurryingTests {
  @Test
  fun `test basic currying`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        fun main(argc: Int32, argv: **Char): Void {
          println("Hello, world");
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }

  @Test
  fun `test call currying without call chain`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        fun print_full_name(name: *Char, surname: *Char) {
          print(name);
          print(" ");
          println(surname);
        }

        fun main(argc: Int32, argv: **Char): Void {
          print_full_name("Isabela", "Freitas");
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }

  @Test
  fun `test call currying`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        fun print_full_name(name: *Char, surname: *Char) {
          print(name);
          print(" ");
          println(surname);
        }

        fun main(argc: Int32, argv: **Char): Void {
          print_full_name("Isabela")("Freitas");
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }

  @Test
  fun `test partial apply curried function`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        fun print_full_name(name: *Char, surname: *Char) {
          print(name);
          print(" ");
          println(surname);
        }

        fun main(argc: Int32, argv: **Char): Void {
          let print_surname = print_full_name("Isabela");
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }

  @Test
  fun `test call partial applied curried function`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        fun print_full_name(name: *Char, surname: *Char) {
          print(name);
          print(" ");
          println(surname);
        }

        fun main(argc: Int32, argv: **Char): Void {
          let print_surname = print_full_name("Isabela");
          print_surname("Freitas");
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }

  @Test
  fun `test basic currying with empty parameters function`() {
    TestCompilation
      .of(
        """
        module Main;

        import Std.IO;

        fun empty(): Void {
          println("Hello, world!");
        }

        fun main(argc: Int32, argv: **Char): Void {
          empty();
        }
        """.trimIndent()
      )
      .debugAll()
      .runTest {
        expectSuccess()
      }
  }
}