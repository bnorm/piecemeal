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

package dev.bnorm.piecemeal.plugin.fir

import dev.bnorm.piecemeal.plugin.Piecemeal
import dev.bnorm.piecemeal.plugin.toJavaSetter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val BUILDER_CLASS_NAME = Name.identifier("Builder")

val NEW_BUILDER_FUN_NAME = Name.identifier("newBuilder")
val BUILD_FUN_NAME = Name.identifier("build")

val FUNCTION1 = ClassId(FqName("kotlin"), Name.identifier("Function1"))

val ClassId.builder get() = createNestedClassId(BUILDER_CLASS_NAME)

fun Name.toParameterName(): Name {
  return asString().removePrefix("set").let { name ->
    Name.identifier(name[0].lowercase() + name.substring(1))
  }
}

fun FirSession.findClassSymbol(classId: ClassId) =
  symbolProvider.getClassLikeSymbolByClassId(classId) as? FirClassSymbol

fun fun1Ext(
  session: FirSession,
  receiver: FirClassSymbol<*>,
): ConeClassLikeType {
  return FUNCTION1.createConeType(
    session = session,
    typeArguments = arrayOf(
      receiver.constructStarProjectedType(),
      session.builtinTypes.unitType.type,
    )
  ).withAttributes(ConeAttributes.create(listOf(CompilerConeAttributes.ExtensionFunctionType)))
}

fun getPrimaryConstructorValueParameters(
  piecemealClassSymbol: FirClassSymbol<*>,
): List<FirValueParameterSymbol> {
  val outerPrimaryConstructor = piecemealClassSymbol.declarationSymbols
    .filterIsInstance<FirConstructorSymbol>()
    .singleOrNull { it.isPrimary } ?: return emptyList()

  return outerPrimaryConstructor.valueParameterSymbols
}

fun FirExtension.createFunNewBuilder(
  piecemealClassSymbol: FirClassSymbol<*>,
  callableId: CallableId,
): FirSimpleFunction? {
  val piecemealClassId = callableId.classId ?: return null
  val builderClassSymbol = session.findClassSymbol(piecemealClassId.builder) ?: return null
  return createMemberFunction(
    owner = piecemealClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnType = builderClassSymbol.constructStarProjectedType(),
  )
}

fun FirExtension.createFunBuilderBuild(
  builderClassSymbol: FirClassSymbol<*>,
  callableId: CallableId,
): FirSimpleFunction? {
  val piecemealClassId = builderClassSymbol.classId.outerClassId!!
  val piecemealClassSymbol = session.findClassSymbol(piecemealClassId)!!
  return createMemberFunction(
    owner = builderClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnType = piecemealClassSymbol.constructStarProjectedType(),
  )
}

fun FirExtension.createFunBuilderSetter(
  builderClassSymbol: FirClassSymbol<*>,
  callableId: CallableId,
): FirSimpleFunction? {
  val piecemealClassId = builderClassSymbol.classId.outerClassId!!
  val piecemealClassSymbol = session.findClassSymbol(piecemealClassId)!!
  val parameterSymbol = getPrimaryConstructorValueParameters(piecemealClassSymbol)
    .singleOrNull { it.name.toJavaSetter() == callableId.callableName } ?: return null
  return createMemberFunction(
    owner = builderClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnType = builderClassSymbol.constructStarProjectedType(),
  ) {
    valueParameter(callableId.callableName.toParameterName(), parameterSymbol.resolvedReturnType)
  }
}

fun FirExtension.createPropertyBuilderValue(
  builderClassSymbol: FirClassSymbol<*>,
  callableId: CallableId
): FirProperty? {
  val piecemealClassId = builderClassSymbol.classId.outerClassId!!
  val piecemealClassSymbol = session.findClassSymbol(piecemealClassId)!!

  val parameter = getPrimaryConstructorValueParameters(piecemealClassSymbol)
    .singleOrNull { it.name == callableId.callableName } ?: return null
  val property = createMemberProperty(
    owner = builderClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnType = parameter.resolvedReturnType.withNullability(ConeNullability.NULLABLE, session.typeContext),
    isVal = false
  )

  return property
}

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
fun FirExtension.createFunPiecemealDsl(
  piecemealClassSymbol: FirRegularClassSymbol,
  callableId: CallableId,
): FirSimpleFunction? {
  val builderClassId = piecemealClassSymbol.classId.builder
  val builderClassSymbol = session.findClassSymbol(builderClassId) ?: return null
  val builderType = fun1Ext(session, receiver = builderClassSymbol)
  return createTopLevelFunction(
    key = Piecemeal.Key,
    callableId = callableId,
    returnType = piecemealClassSymbol.constructStarProjectedType(),
  ) {
    valueParameter(Name.identifier("builder"), builderType)
  }
}
