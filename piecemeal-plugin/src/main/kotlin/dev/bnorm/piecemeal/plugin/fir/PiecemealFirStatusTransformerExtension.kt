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

package dev.bnorm.piecemeal.plugin.fir

import dev.bnorm.piecemeal.plugin.Piecemeal
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.copy
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol

class PiecemealFirStatusTransformerExtension(
  session: FirSession,
) : FirStatusTransformerExtension(session) {
  override fun needTransformStatus(declaration: FirDeclaration): Boolean {
    if (declaration is FirConstructor && declaration.isPrimary) {
      val containingClass = declaration.getContainingClass()
      val annotation = containingClass?.getAnnotationByClassId(Piecemeal.ANNOTATION_CLASS_ID, session)
      return annotation != null
    }
    return false
  }

  override fun transformStatus(
    status: FirDeclarationStatus,
    constructor: FirConstructor,
    containingClass: FirClassLikeSymbol<*>?,
    isLocal: Boolean
  ): FirDeclarationStatus {
    constructor.originalVisibility = status.visibility
    return when (status.visibility) {
      Visibilities.Private -> status
      else -> status.copy(visibility = Visibilities.Private)
    }
  }
}

private object OriginalVisibility : FirDeclarationDataKey()

internal var FirDeclaration.originalVisibility: Visibility?
  by FirDeclarationDataRegistry.data(OriginalVisibility)

internal val FirBasedSymbol<FirDeclaration>.originalVisibility: Visibility?
  by FirDeclarationDataRegistry.symbolAccessor(OriginalVisibility)
