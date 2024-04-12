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
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class IrPluginTest {

  @Test
  fun `IR plugin target`() {
    val result = compile(
      SourceFile.kotlin(
        "main.kt", """class Person private constructor(
    val name: String,
    val nickname: String? = name,
    val age: Int = 0,
) {
    fun newBuilder(): Builder {
        val builder = Builder()
        builder.name = name
        builder.nickname = nickname
        builder.age = age
        return builder
    }

    class Builder {
        @set:JvmSynthetic // Hide 'void' setter from Java.
        var name: String? = null

        @set:JvmSynthetic // Hide 'void' setter from Java.
        var nickname: String? = null

        @set:JvmSynthetic // Hide 'void' setter from Java.
        var age: Int? = null

        fun build(): Person {
            val name = name ?: throw IllegalStateException("Missing required parameter 'name'.")
            val nickname = nickname ?: name
            val age = age ?: 0
            return Person(
                name = name,
                nickname = nickname,
                age = age,
            )
        }

        fun setName(name: String): Builder {
            this.name = name
            return this
        }

        fun setNickname(nickname: String?): Builder {
            this.nickname = nickname
            return this
        }

        fun setAge(age: Int): Builder {
            this.age = age
            return this
        }
    }
}

@JvmSynthetic // Hide from Java callers who should use Builder.
fun Person(builder: Person.Builder.() -> Unit): Person {
    return Person.Builder().apply(builder).build()
}
"""
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
  }

  @Test
  fun `IR plugin success`() {
    val result = compile(
      SourceFile.kotlin(
        "main.kt", """import com.bnorm.piecemeal.Piecemeal

@Piecemeal
class Person(
    val name: String,
    val nickname: String? = name,
    val age: Int = 0,
)

fun person1(): Person {
    return Person.Builder()
        .setName("John")
        .build()
}

fun person2(): Person {
    return Person.Builder().apply {
        name = "John"
    }.build()
}
"""
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
  }
}
