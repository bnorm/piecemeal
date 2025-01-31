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
import dev.bnorm.piecemeal.plugin.toJavaSetter
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
class PiecemealFirGenerationExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session) {
  // Symbols for classes which have Piecemeal annotation.
  private val piecemealClasses by lazy {
    session.predicateBasedProvider.getSymbolsByPredicate(Piecemeal.ANNOTATION_PREDICATE)
      .filterIsInstance<FirRegularClassSymbol>()
      .filter {
        val constructor = it.declarationSymbols.filterIsInstance<FirConstructorSymbol>().singleOrNull { it.isPrimary }
        constructor != null && constructor.rawStatus.visibility != Visibilities.Private
      }
      .toSet()
  }

  // IDs for nested Piecemeal annotated classes.
  private val piecemealClassIds by lazy {
    piecemealClasses.map { it.classId }.toSet()
  }

  // IDs for nested Builder classes.
  private val piecemealCompanionClassIds by lazy {
    piecemealClasses.map { it.classId.companion }.toSet()
  }

  // IDs for nested Builder classes.
  private val builderClassIds by lazy {
    piecemealClasses.map { it.classId.builder }.toSet()
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(Piecemeal.ANNOTATION_PREDICATE)
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    val owner = context?.owner ?: return emptyList()

    val function = when {

      callableId.classId in piecemealClassIds -> when (callableId.callableName) {
        NEW_BUILDER_FUN_NAME ->
          createFunNewBuilder(
            piecemealClassSymbol = owner,
            callableId = callableId,
          )

        COPY_FUN_NAME ->
          createFunCopy(
            piecemealClassSymbol = owner,
            callableId = callableId,
          )

        else -> null
      }

      callableId.classId in piecemealCompanionClassIds && callableId.callableName == BUILD_FUN_NAME ->
        createFunPiecemealDsl(
          companionClassSymbol = owner,
          callableId = callableId,
        )

      callableId.classId in builderClassIds ->
        when (callableId.callableName) {
          BUILD_FUN_NAME -> createFunBuilderBuild(
            builderClassSymbol = owner,
            callableId = callableId,
          )

          else -> createFunBuilderSetter(
            builderClassSymbol = owner,
            callableId = callableId,
          )
        }

      else -> null
    }

    if (function == null) return emptyList()
    return listOf(function.symbol)
  }

  override fun generateProperties(callableId: CallableId, context: MemberGenerationContext?): List<FirPropertySymbol> {
    val owner = context?.owner ?: return emptyList()

    if (callableId.classId in builderClassIds) {
      val property = createPropertyBuilderValue(owner, callableId) ?: return emptyList()
      return listOf(property.symbol)
    }

    return emptyList()
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
    val ownerClassId = context.owner.classId
    val constructor = when {
      ownerClassId in builderClassIds ->
        createConstructor(context.owner, Piecemeal.Key, isPrimary = true)

      ownerClassId in piecemealCompanionClassIds ->
        createDefaultPrivateConstructor(context.owner, Piecemeal.Key)

      else -> error("Can't generate constructor for ${ownerClassId.asSingleFqName()}")
    }
    return listOf(constructor.symbol)
  }

  override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
    return when {
      classSymbol in piecemealClasses -> setOf(NEW_BUILDER_FUN_NAME, COPY_FUN_NAME)
      classSymbol.classId in piecemealCompanionClassIds -> setOf(BUILD_FUN_NAME, SpecialNames.INIT)
      classSymbol.classId in builderClassIds -> {
        val builderClassId = classSymbol.classId.outerClassId!!
        val piecemealClass = session.findClassSymbol(builderClassId)!!
        val parameters = getPrimaryConstructorValueParameters(piecemealClass)
        buildSet {
          add(SpecialNames.INIT)
          addAll(parameters.map { it.name })
          if (session.piecemeal.configuration.enableJavaSetters) {
            addAll(parameters.map { it.name.toJavaSetter() })
          }
          add(BUILD_FUN_NAME)
        }
      }

      else -> emptySet()
    }
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    return when (classSymbol) {
      in piecemealClasses -> setOf(BUILDER_CLASS_NAME, SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
      else -> emptySet()
    }
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext
  ): FirClassLikeSymbol<*>? {
    if (owner !is FirRegularClassSymbol) return null
    if (owner !in piecemealClasses) return null
    return when (name) {
      BUILDER_CLASS_NAME -> createNestedClass(owner, name, Piecemeal.Key).symbol
      SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT -> generateCompanionDeclaration(owner)
      else -> error("Can't generate class ${owner.classId.createNestedClassId(name).asSingleFqName()}")
    }
  }

  private fun generateCompanionDeclaration(owner: FirRegularClassSymbol): FirRegularClassSymbol? {
    if (owner.companionObjectSymbol != null) return null
    val companion = createCompanionObject(owner, Piecemeal.Key)
    return companion.symbol
  }
}
