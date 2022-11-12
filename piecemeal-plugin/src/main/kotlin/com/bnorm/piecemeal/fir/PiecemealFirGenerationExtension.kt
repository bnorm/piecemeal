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

package com.bnorm.piecemeal.fir

import com.bnorm.piecemeal.PiecemealKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassForStaticMemberAttr
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.annotated
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class PiecemealFirGenerationExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session) {
  companion object {
    private val PREDICATE = annotated(FqName("com.bnorm.piecemeal.Piecemeal"))

    private val NEW_BUILDER_NAME = Name.identifier("newBuilder")
    private val BUILDER_NAME = Name.identifier("Builder")

    private val ClassId.builder get() = createNestedClassId(BUILDER_NAME)
  }

  // Symbols for classes which have Piecemeal annotation
  private val piecemealClasses by lazy {
    session.predicateBasedProvider.getSymbolsByPredicate(PREDICATE).filterIsInstance<FirRegularClassSymbol>().toSet()
  }

  // IDs for nested Builder classes
  private val builderClassIds by lazy {
    piecemealClasses.map { it.classId.builder }.toSet()
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    if (callableId.callableName != NEW_BUILDER_NAME) return emptyList()
    val classId = callableId.classId ?: return emptyList()
    val builderClassSymbol = session.findClassSymbol(classId.builder) ?: return emptyList()
    val function = buildNewBuilderFunction(callableId, builderClassSymbol.defaultType())
    return listOf(function.symbol)
  }

  override fun generateClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    if (classId.shortClassName != BUILDER_NAME) return null
    val parentClassId = classId.parentClassId ?: return null
    if (piecemealClasses.none { it.classId == parentClassId }) return null
    return buildBuilderClass(classId).symbol
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    val ownerClassId = context.owner.classId
    assert(ownerClassId in builderClassIds)
    return listOf(buildBuilderConstructor(ownerClassId).symbol)
  }

  override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>): Set<Name> {
    return when {
      classSymbol in piecemealClasses -> setOf(NEW_BUILDER_NAME)
      classSymbol.classId in builderClassIds -> setOf(SpecialNames.INIT)
      else -> emptySet()
    }
  }

  override fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>): Set<Name> {
    return if (classSymbol in piecemealClasses) setOf(BUILDER_NAME) else emptySet()
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(PREDICATE)
  }
}

private fun FirSession.findClassSymbol(classId: ClassId) =
  symbolProvider.getClassLikeSymbolByClassId(classId) as? FirClassSymbol

private fun FirDeclarationGenerationExtension.buildBuilderClass(classId: ClassId): FirRegularClass {
  return buildRegularClass {
    resolvePhase = FirResolvePhase.BODY_RESOLVE
    moduleData = session.moduleData
    origin = PiecemealKey.origin
    classKind = ClassKind.CLASS
    scopeProvider = session.kotlinScopeProvider
    status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
    name = classId.shortClassName
    symbol = FirRegularClassSymbol(classId)
    superTypeRefs += session.builtinTypes.anyType
  }
}

private fun FirDeclarationGenerationExtension.buildNewBuilderFunction(
  callableId: CallableId,
  returnType: ConeKotlinType,
): FirSimpleFunction {
  return buildSimpleFunction {
    resolvePhase = FirResolvePhase.BODY_RESOLVE
    moduleData = session.moduleData
    origin = PiecemealKey.origin
    status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
    returnTypeRef = buildResolvedTypeRef {
      type = returnType
    }
    name = callableId.callableName
    symbol = FirNamedFunctionSymbol(callableId)
    dispatchReceiverType = callableId.classId?.let {
      session.findClassSymbol(it)?.defaultType()
    }
  }
}

private fun FirDeclarationGenerationExtension.buildBuilderConstructor(classId: ClassId): FirConstructor {
  val lookupTag = ConeClassLikeLookupTagImpl(classId)
  return buildPrimaryConstructor {
    resolvePhase = FirResolvePhase.BODY_RESOLVE
    moduleData = session.moduleData
    origin = PiecemealKey.origin
    returnTypeRef = buildResolvedTypeRef {
      type = ConeClassLikeTypeImpl(lookupTag, emptyArray(), isNullable = false)
    }
    status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
    symbol = FirConstructorSymbol(classId)
  }.also {
    it.containingClassForStaticMemberAttr = lookupTag
  }
}
