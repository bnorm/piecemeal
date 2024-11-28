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
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.plugin.createConstructor
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
      .filterIsInstance<FirRegularClassSymbol>().toSet()
  }

  // IDs for nested Piecemeal annotated classes.
  private val piecemealClassIds by lazy {
    piecemealClasses.map { it.classId }.toSet()
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
    val owner = context?.owner
    val piecemealClass = piecemealClasses.singleOrNull {
      it.name == callableId.callableName && it.classId.packageFqName == callableId.packageName
    }

    val function = when {
      piecemealClass != null -> createFunPiecemealDsl(
        piecemealClassSymbol = piecemealClass,
        callableId = callableId,
      )

      callableId.classId in piecemealClassIds && callableId.callableName == NEW_BUILDER_FUN_NAME ->
        createFunNewBuilder(
          piecemealClassSymbol = owner ?: return emptyList(),
          callableId = callableId,
        )

      callableId.classId in builderClassIds ->
        when (callableId.callableName) {
          BUILD_FUN_NAME -> createFunBuilderBuild(
            builderClassSymbol = owner ?: return emptyList(),
            callableId = callableId,
          )

          else -> createFunBuilderSetter(
            builderClassSymbol = owner ?: return emptyList(),
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
        val builderClassId = classSymbol.classId.outerClassId!!
        val piecemealClass = session.findClassSymbol(builderClassId)!!
        val parameters = getPrimaryConstructorValueParameters(piecemealClass)
        (parameters.map { it.name } +
          parameters.map { it.name.toJavaSetter() } +
          setOf(SpecialNames.INIT, BUILD_FUN_NAME)).toSet()
      }

      else -> emptySet()
    }
  }

  override fun getTopLevelCallableIds(): Set<CallableId> {
    // TODO what about nested classes? nest within parent class?
    return piecemealClasses
      .filter { !it.classId.isNestedClass }
      .map { CallableId(it.classId.packageFqName, it.classId.shortClassName) }
      .toSet()
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    return when (classSymbol) {
      in piecemealClasses -> setOf(BUILDER_CLASS_NAME)
      else -> emptySet()
    }
  }
}
