/*
 * Copyright (C) 2022 Brian Norman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bnorm.piecemeal

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class IrPluginTest {
  @Test
  fun `IR plugin success`() {
    val result = compile(
      SourceFile.kotlin(
        "main.kt", """import com.bnorm.piecemeal.Piecemeal

@Piecemeal
class First

@Piecemeal
class Second

fun box(): String {
    val result1 = First.Builder()
    if (result1 is First.Builder) return "Error: ${'$'}result1"

    val result2 = Second().newBuilder()
    if (result2 is Second.Builder) return "Error: ${'$'}result2"

    return "OK"
}
"""
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
  }
}
