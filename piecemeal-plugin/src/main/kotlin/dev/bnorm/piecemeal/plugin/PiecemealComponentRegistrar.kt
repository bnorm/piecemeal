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

package dev.bnorm.piecemeal.plugin

import com.google.auto.service.AutoService
import dev.bnorm.piecemeal.plugin.fir.PiecemealFirExtensionRegistrar
import dev.bnorm.piecemeal.plugin.ir.PiecemealIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
@Suppress("unused") // Used via reflection
@AutoService(CompilerPluginRegistrar::class)
class PiecemealComponentRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val piecemealConfiguration = PiecemealConfiguration.create(configuration)
    FirExtensionRegistrarAdapter.registerExtension(PiecemealFirExtensionRegistrar(piecemealConfiguration))
    IrGenerationExtension.registerExtension(PiecemealIrGenerationExtension())
  }
}
