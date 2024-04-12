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

package com.bnorm.piecemeal.plugin.fir

import com.bnorm.piecemeal.plugin.Piecemeal
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.resolve.getContainingClass

class PiecemealFirStatusTransformerExtension(
  session: FirSession,
) : FirStatusTransformerExtension(session) {
  override fun needTransformStatus(declaration: FirDeclaration): Boolean {
    if (declaration is FirConstructor && declaration.isPrimary) {
      val containingClass = declaration.getContainingClass(session)
      val annotation = containingClass?.getAnnotationByClassId(Piecemeal.ANNOTATION_CLASS_ID)
      return annotation != null
    }
    return false
  }

  override fun transformStatus(
    status: FirDeclarationStatus,
    constructor: FirConstructor,
    containingClass: FirClass?,
    isLocal: Boolean,
  ): FirDeclarationStatus {
    return FirDeclarationStatusImpl(Visibilities.Private, status.modality)
  }
}
