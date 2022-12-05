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
import com.bnorm.piecemeal.plugin.toJavaSetter
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassForStaticMemberAttr
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildBackingField
import org.jetbrains.kotlin.fir.declarations.builder.buildPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.builder.buildProperty
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.expressions.builder.buildConstExpression
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirBackingFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeNullability
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withNullability
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.types.ConstantValueKind

class PiecemealFirGenerationExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session) {
  companion object {
    private val BUILDER_CLASS_NAME = Name.identifier("Builder")

    private val NEW_BUILDER_FUN_NAME = Name.identifier("newBuilder")
    private val BUILD_FUN_NAME = Name.identifier("build")

    private val ClassId.builder get() = createNestedClassId(BUILDER_CLASS_NAME)
  }

  // Symbols for classes which have Piecemeal annotation
  private val piecemealClasses by lazy {
    session.predicateBasedProvider.getSymbolsByPredicate(Piecemeal.ANNOTATION_PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>().toSet()
  }

  // IDs for nested Builder classes
  private val builderClassIds by lazy {
    piecemealClasses.map { it.classId.builder }.toSet()
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    if (callableId.callableName == NEW_BUILDER_FUN_NAME) {
      val classId = callableId.classId ?: return emptyList()
      val builderClassSymbol = session.findClassSymbol(classId.builder) ?: return emptyList()
      val function = buildNewBuilderFunction(callableId, builderClassSymbol.defaultType())
      return listOf(function.symbol)
    } else if (callableId.classId in builderClassIds) {
      val builderClassSymbol = context?.owner ?: return emptyList()
      val function = if (callableId.callableName == BUILD_FUN_NAME) {
        buildBuilderBuildFunction(callableId, builderClassSymbol.outerClass!!.defaultType())
      } else {
        val parameter = getPrimaryConstructorValueParameters(builderClassSymbol.outerClass!!)
          .singleOrNull { it.name.toJavaSetter() == callableId.callableName } ?: return emptyList()
        buildBuilderPropertyFunction(
          callableId,
          parameter.resolvedReturnTypeRef,
          builderClassSymbol.defaultType()
        )
      }
      return listOf(function.symbol)
    } else {
      return emptyList()
    }
  }

  override fun generateProperties(callableId: CallableId, context: MemberGenerationContext?): List<FirPropertySymbol> {
    if (callableId.classId in builderClassIds) {
      val builderClassSymbol = context?.owner ?: return emptyList()

      val parameter = getPrimaryConstructorValueParameters(builderClassSymbol.outerClass!!)
        .singleOrNull { it.name == callableId.callableName } ?: return emptyList()
      val property = buildBuilderProperty(callableId, parameter.resolvedReturnType)

      return listOf(property.symbol)
    }

    return emptyList()
  }

  override fun generateClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    if (classId.shortClassName != BUILDER_CLASS_NAME) return null
    val outerClassId = classId.outerClassId ?: return null
    val outerClass = piecemealClasses.singleOrNull { it.classId == outerClassId } ?: return null

    val parameters = getPrimaryConstructorValueParameters(outerClass)
    return buildBuilderClass(classId).symbol
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    val ownerClassId = context.owner.classId
    assert(ownerClassId in builderClassIds)
    return listOf(buildBuilderConstructor(ownerClassId).symbol)
  }

  override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>): Set<Name> {
    return when {
      classSymbol in piecemealClasses -> setOf(NEW_BUILDER_FUN_NAME)
      classSymbol.classId in builderClassIds -> {
        val parameters = getPrimaryConstructorValueParameters(classSymbol.outerClass!!)
        (parameters.map { it.name } +
          parameters.map { it.name.toJavaSetter() } +
          setOf(SpecialNames.INIT, BUILD_FUN_NAME)).toSet()
      }

      else -> emptySet()
    }
  }

  override fun getTopLevelCallableIds(): Set<CallableId> {
    // TODO add DSL like builder function
    return super.getTopLevelCallableIds()
  }

  private fun getPrimaryConstructorValueParameters(classSymbol: FirClassSymbol<*>): List<FirValueParameterSymbol> {
    val outerPrimaryConstructor = classSymbol.declarationSymbols
      .filterIsInstance<FirConstructorSymbol>()
      .singleOrNull { it.isPrimary } ?: return emptyList()

    return outerPrimaryConstructor.valueParameterSymbols
  }

  private val FirClassSymbol<*>.outerClass
    get() = classId.outerClassId?.let { session.findClassSymbol(it) }

  override fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>): Set<Name> {
    return if (classSymbol in piecemealClasses) setOf(BUILDER_CLASS_NAME) else emptySet()
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(Piecemeal.ANNOTATION_PREDICATE)
  }
}

private fun FirSession.findClassSymbol(classId: ClassId) =
  symbolProvider.getClassLikeSymbolByClassId(classId) as? FirClassSymbol

private fun FirDeclarationGenerationExtension.buildBuilderClass(
  classId: ClassId,
): FirRegularClass {
  val built = buildRegularClass {
    resolvePhase = FirResolvePhase.BODY_RESOLVE
    moduleData = session.moduleData
    origin = Piecemeal.Key.origin
    classKind = ClassKind.CLASS
    scopeProvider = session.kotlinScopeProvider
    status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
    name = classId.shortClassName
    symbol = FirRegularClassSymbol(classId)
    superTypeRefs += session.builtinTypes.anyType
//    declarations.addAll(properties.map { buildProperty(it) })
  }
  return built
}

private fun FirDeclarationGenerationExtension.buildNewBuilderFunction(
  callableId: CallableId,
  returnType: ConeKotlinType,
): FirSimpleFunction {
  return buildSimpleFunction {
    resolvePhase = FirResolvePhase.BODY_RESOLVE
    moduleData = session.moduleData
    origin = Piecemeal.Key.origin
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

private fun FirDeclarationGenerationExtension.buildBuilderPropertyFunction(
  callableId: CallableId,
  parameterType: FirTypeRef,
  returnType: ConeKotlinType,
): FirSimpleFunction {
  val parameterName = callableId.callableName.asString().removePrefix("set").let { name ->
    Name.identifier(name[0].lowercase() + name.substring(1))
  }

  return buildSimpleFunction {
    resolvePhase = FirResolvePhase.BODY_RESOLVE
    moduleData = session.moduleData
    origin = Piecemeal.Key.origin
    status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
    valueParameters.add(
      buildValueParameter {
        moduleData = session.moduleData
        origin = Piecemeal.Key.origin
        returnTypeRef = parameterType
        name = parameterName
        symbol = FirValueParameterSymbol(parameterName)
        isCrossinline = false
        isNoinline = false
        isVararg = false
      }
    )
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

private fun FirDeclarationGenerationExtension.buildBuilderProperty(
  callableId: CallableId,
  propertyType: ConeKotlinType,
): FirProperty {
  val propertySymbol = FirPropertySymbol(callableId.callableName)
  val propertyTypeRef = buildResolvedTypeRef {
    type = propertyType.withNullability(ConeNullability.NULLABLE, session.typeContext)
  }
  val dispatchReceiverType = callableId.classId?.let {
    session.findClassSymbol(it)?.defaultType()
  }

  val built = buildProperty {
    moduleData = session.moduleData
    origin = Piecemeal.Key.origin
    this.status =
      FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
    returnTypeRef = propertyTypeRef
    this.dispatchReceiverType = dispatchReceiverType
    name = callableId.callableName
    symbol = propertySymbol
    isVar = true
    isLocal = false

    backingField = buildBackingField {
      moduleData = session.moduleData
      origin = Piecemeal.Key.origin
      returnTypeRef = propertyTypeRef
      this.dispatchReceiverType = dispatchReceiverType
      this.status =
        FirResolvedDeclarationStatusImpl(Visibilities.Private, Modality.FINAL, EffectiveVisibility.PrivateInClass)
      name = StandardNames.BACKING_FIELD
      symbol = FirBackingFieldSymbol(CallableId(name))
      this.propertySymbol = propertySymbol
      this.initializer = buildConstExpression(null, ConstantValueKind.Null, null)
      this.isVar = true
      this.isVal = false
    }

    getter = FirDefaultPropertyGetter(
      source = null,
      moduleData = session.moduleData,
      origin = Piecemeal.Key.origin,
      propertyTypeRef = propertyTypeRef,
      visibility = Visibilities.Public,
      propertySymbol = symbol,
      effectiveVisibility = EffectiveVisibility.Public,
    )

    // TODO JvmSynthetic
    setter = FirDefaultPropertySetter(
      source = null,
      moduleData = session.moduleData,
      origin = Piecemeal.Key.origin,
      propertyTypeRef = propertyTypeRef,
      visibility = Visibilities.Public,
      propertySymbol = symbol,
      effectiveVisibility = EffectiveVisibility.Public,
    )
  }
  return built
}

private fun FirDeclarationGenerationExtension.buildBuilderBuildFunction(
  callableId: CallableId,
  returnType: ConeKotlinType,
): FirSimpleFunction {
  return buildSimpleFunction {
    resolvePhase = FirResolvePhase.BODY_RESOLVE
    moduleData = session.moduleData
    origin = Piecemeal.Key.origin
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
    origin = Piecemeal.Key.origin
    returnTypeRef = buildResolvedTypeRef {
      type = ConeClassLikeTypeImpl(lookupTag, emptyArray(), isNullable = false)
    }
    status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
    symbol = FirConstructorSymbol(classId)
  }.also {
    it.containingClassForStaticMemberAttr = lookupTag
  }
}
