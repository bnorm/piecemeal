/*
 * Copyright (C) 2025 Brian Norman
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

package dev.bnorm.piecemeal.plugin.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor

internal object PiecemealErrors : BaseDiagnosticRendererFactory() {
  val PIECEMEAL_NO_PRIMARY_CONSTRUCTOR by error0<KtNamedDeclaration>(SourceElementPositioningStrategies.DECLARATION_NAME)
  val PIECEMEAL_PRIVATE_CONSTRUCTOR by error0<KtPrimaryConstructor>(SourceElementPositioningStrategies.VISIBILITY_MODIFIER)

  override val MAP = KtDiagnosticFactoryToRendererMap("Piecemeal").apply {
    put(PIECEMEAL_NO_PRIMARY_CONSTRUCTOR, "'@Piecemeal' requires a primary constructor.")
  }

  init {
    RootDiagnosticRendererFactory.registerFactory(this)
  }
}
