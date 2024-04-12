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
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeNullability
import org.jetbrains.kotlin.fir.types.constructStarProjectedType
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withNullability
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
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
    val owner = context?.owner ?: return emptyList()

    if (callableId.callableName == NEW_BUILDER_FUN_NAME) {
      val classId = callableId.classId ?: return emptyList()
      val builderClassSymbol = session.findClassSymbol(classId.builder) ?: return emptyList()
      val function = createMemberFunction(
        owner,
        Piecemeal.Key,
        callableId.callableName,
        builderClassSymbol.constructStarProjectedType()
      )
      return listOf(function.symbol)
    } else if (callableId.classId in builderClassIds) {
      val piecemealClass = owner.outerClass!!
      val function = if (callableId.callableName == BUILD_FUN_NAME) {
        createMemberFunction(
          owner,
          Piecemeal.Key,
          callableId.callableName,
          piecemealClass.constructStarProjectedType()
        )
      } else {
        val parameter = getPrimaryConstructorValueParameters(piecemealClass)
          .singleOrNull { it.name.toJavaSetter() == callableId.callableName } ?: return emptyList()
        createMemberFunction(
          owner,
          Piecemeal.Key,
          callableId.callableName,
          owner.constructStarProjectedType()
        ) {
          valueParameter(callableId.callableName.toParameterName(), parameter.resolvedReturnType)
        }
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
      val property = createMemberProperty(
        owner = builderClassSymbol,
        key = Piecemeal.Key,
        name = callableId.callableName,
        returnType = parameter.resolvedReturnType.withNullability(ConeNullability.NULLABLE, session.typeContext),
        isVal = false
      )

      return listOf(property.symbol)
    }

    return emptyList()
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext
  ): FirClassLikeSymbol<*>? {
    if (owner !in piecemealClasses) return null
    return createNestedClass(owner, name, Piecemeal.Key).symbol
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    val ownerClassId = context.owner.classId
    assert(ownerClassId in builderClassIds)
    return listOf(createConstructor(context.owner, Piecemeal.Key, isPrimary = true).symbol)
  }

  override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
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

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    return if (classSymbol in piecemealClasses) setOf(BUILDER_CLASS_NAME) else emptySet()
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(Piecemeal.ANNOTATION_PREDICATE)
  }
}

fun Name.toParameterName(): Name {
  return asString().removePrefix("set").let { name ->
    Name.identifier(name[0].lowercase() + name.substring(1))
  }
}

private fun FirSession.findClassSymbol(classId: ClassId) =
  symbolProvider.getClassLikeSymbolByClassId(classId) as? FirClassSymbol
