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

package com.bnorm.piecemeal.plugin

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate.BuilderContext.annotated
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object Piecemeal {
  val ANNOTATION_FQ_NAME = FqName("com.bnorm.piecemeal.Piecemeal")
  val ANNOTATION_CLASS_ID = ClassId.topLevel(ANNOTATION_FQ_NAME)
  val ANNOTATION_PREDICATE = annotated(ANNOTATION_FQ_NAME)

  object Key : GeneratedDeclarationKey() {
    override fun toString(): String {
      return "PiecemealKey"
    }
  }
}

fun Name.toJavaSetter(): Name {
  val name = asString()
  return Name.identifier("set" + name[0].uppercase() + name.substring(1))
}
