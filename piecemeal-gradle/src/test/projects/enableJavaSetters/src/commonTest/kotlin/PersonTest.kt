/*
 * Copyright (C) 2024 Brian Norman
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

import kotlin.test.Test
import kotlin.test.assertEquals

class PersonTest {
  @Test
  fun testFluentMutable() {
    val person = Person.Mutable()
      .setName("Sam")
      .build()
    assertEquals(person.toString(), "Person{name=Sam, nickname=Sam, age=0}")
  }

  @Test
  fun testApplyMutable() {
    val person = Person.Mutable().apply {
      name = "Sam"
    }.build()
    assertEquals(person.toString(), "Person{name=Sam, nickname=Sam, age=0}")
  }

  @Test
  fun testCompanionBuild() {
    val person = Person.build {
      name = "Sam"
    }
    assertEquals(person.toString(), "Person{name=Sam, nickname=Sam, age=0}")
  }
}
