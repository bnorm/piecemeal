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

package dev.bnorm.piecemeal.plugin.fir.checkers

import dev.bnorm.piecemeal.plugin.Piecemeal
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor

object PiecemealAnnotationChecker : FirClassChecker(MppCheckerKind.Common) {
  private val PIECEMEAL_NO_PRIMARY_CONSTRUCTOR = KtDiagnosticFactory0(
    "PIECEMEAL_NO_PRIMARY_CONSTRUCTOR",
    Severity.ERROR,
    SourceElementPositioningStrategies.DECLARATION_NAME,
    KtNamedDeclaration::class,
  )

  private val PIECEMEAL_PRIVATE_CONSTRUCTOR = KtDiagnosticFactory0(
    "PIECEMEAL_PRIVATE_CONSTRUCTOR",
    Severity.ERROR,
    SourceElementPositioningStrategies.VISIBILITY_MODIFIER,
    KtPrimaryConstructor::class,
  )

  override fun check(declaration: FirClass, context: CheckerContext, reporter: DiagnosticReporter) {
    if (!declaration.hasAnnotation(Piecemeal.ANNOTATION_CLASS_ID, context.session)) return

    val primaryConstructor = declaration.primaryConstructorIfAny(context.session)
    if (primaryConstructor == null) {
      reporter.reportOn(declaration.source, PIECEMEAL_NO_PRIMARY_CONSTRUCTOR, context)
      return
    }

    // Status transformer uses PrivateToThis to distinguish explicitly-private constructors.
    if (primaryConstructor.visibility == Visibilities.Private) {
      reporter.reportOn(primaryConstructor.source, PIECEMEAL_PRIVATE_CONSTRUCTOR, context)
    }
  }
}
