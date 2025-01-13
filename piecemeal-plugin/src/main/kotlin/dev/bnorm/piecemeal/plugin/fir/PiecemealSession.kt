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

import dev.bnorm.piecemeal.plugin.PiecemealConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent

internal class PiecemealSession(
  session: FirSession,
  val configuration: PiecemealConfiguration,
) : FirExtensionSessionComponent(session) {
  companion object {
    fun getFactory(configuration: PiecemealConfiguration) = Factory { session ->
      PiecemealSession(session, configuration)
    }
  }
}

internal val FirSession.piecemeal: PiecemealSession by FirSession.sessionComponentAccessor()
