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

import com.bnorm.piecemeal.plugin.PiecemealComponentRegistrar
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile

fun compile(vararg sourceFiles: SourceFile) = compile(sourceFiles.toList())

fun compile(
  sourceFiles: List<SourceFile>,
): KotlinCompilation.Result {
  return KotlinCompilation().apply {
    sources = sourceFiles
    useK2 = true
    supportsK2 = true
    compilerPlugins = listOf(PiecemealComponentRegistrar())
    inheritClassPath = true
  }.compile()
}
