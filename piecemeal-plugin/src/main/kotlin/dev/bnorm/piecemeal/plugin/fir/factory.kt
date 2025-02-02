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
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.contracts.description.KtValueParameterReference
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.contracts.builder.FirResolvedContractDescriptionBuilder
import org.jetbrains.kotlin.fir.contracts.builder.buildEffectDeclaration
import org.jetbrains.kotlin.fir.contracts.builder.buildResolvedContractDescription
import org.jetbrains.kotlin.fir.contracts.description.ConeCallsEffectDeclaration
import org.jetbrains.kotlin.fir.declarations.FirContractDescriptionOwner
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.*
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.types.Variance

val MUTABLE_CLASS_NAME = Name.identifier("Mutable")

val TO_MUTABLE_FUN_NAME = Name.identifier("toMutable")
val BUILD_FUN_NAME = Name.identifier("build")
val COPY_FUN_NAME = Name.identifier("copy")

val FUNCTION1 = ClassId(FqName("kotlin"), Name.identifier("Function1"))

val ClassId.mutable: ClassId get() = createNestedClassId(MUTABLE_CLASS_NAME)
val ClassId.companion: ClassId get() = createNestedClassId(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)

private fun Name.toParameterName(): Name {
  return asString().removePrefix("set").let { name ->
    Name.identifier(name[0].lowercase() + name.substring(1))
  }
}

fun FirSession.findClassSymbol(classId: ClassId) =
  symbolProvider.getClassLikeSymbolByClassId(classId) as? FirClassSymbol

private fun fun1Ext(
  session: FirSession,
  receiverType: ConeClassLikeType,
): ConeClassLikeType {
  return FUNCTION1.createConeType(
    session = session,
    typeArguments = arrayOf(
      receiverType,
      session.builtinTypes.unitType.coneType,
    )
  ).withAttributes(ConeAttributes.create(listOf(CompilerConeAttributes.ExtensionFunctionType)))
}

private fun FirContractDescriptionOwner.replaceContractDescription(
  init: FirResolvedContractDescriptionBuilder.() -> Unit = {},
) {
  replaceContractDescription(
    buildResolvedContractDescription {
      init()
    }
  )
}

private fun FirResolvedContractDescriptionBuilder.callsInPlaceExactlyOnce(
  parameterIndex: Int,
  name: String,
) {
  effects += buildEffectDeclaration {
    effect = ConeCallsEffectDeclaration(
      valueParameterReference = KtValueParameterReference(parameterIndex, name),
      kind = EventOccurrencesRange.EXACTLY_ONCE,
    )
  }
}

private fun DeclarationBuildingContext<*>.copyTypeParametersFrom(
  piecemealClassSymbol: FirClassSymbol<*>,
  session: FirSession,
) {
  for (parameter in piecemealClassSymbol.typeParameterSymbols) {
    typeParameter(
      name = parameter.name,
      variance = Variance.INVARIANT, // Type must always be invariant to support read and write access.
    ) {
      for (bound in parameter.resolvedBounds) {
        bound { typeParameters ->
          val arguments = typeParameters.map { it.toConeType() }
          val substitutor = substitutor(piecemealClassSymbol, arguments, session)
          substitutor.substituteOrSelf(bound.coneType)
        }
      }
    }
  }
}

private fun substitutor(
  piecemealClassSymbol: FirClassLikeSymbol<*>,
  mutableClassSymbol: FirClassLikeSymbol<*>,
  session: FirSession,
): ConeSubstitutor {
  val builderArguments = mutableClassSymbol.typeParameterSymbols.map { it.toConeType() }
  return substitutor(piecemealClassSymbol, builderArguments, session)
}

private fun substitutor(
  piecemealClassSymbol: FirClassLikeSymbol<*>,
  builderArguments: List<ConeKotlinType>,
  session: FirSession,
): ConeSubstitutor {
  val piecemealParameters = piecemealClassSymbol.typeParameterSymbols
  return substitutorByMap(piecemealParameters.zip(builderArguments).toMap(), session)
}

fun getPrimaryConstructorValueParameters(
  piecemealClassSymbol: FirClassSymbol<*>,
): List<FirValueParameterSymbol> {
  val outerPrimaryConstructor = piecemealClassSymbol.declarationSymbols
    .filterIsInstance<FirConstructorSymbol>()
    .singleOrNull { it.isPrimary } ?: return emptyList()

  return outerPrimaryConstructor.valueParameterSymbols
}

fun FirExtension.generateBuilderClass(
  piecemealClassSymbol: FirClassSymbol<*>,
): FirRegularClass {
  return createNestedClass(piecemealClassSymbol, MUTABLE_CLASS_NAME, Piecemeal.Key) {
    copyTypeParametersFrom(piecemealClassSymbol, session)
  }
}

fun FirExtension.createFunToMutable(
  piecemealClassSymbol: FirClassSymbol<*>,
  callableId: CallableId,
): FirSimpleFunction? {
  val piecemealClassId = callableId.classId ?: return null
  val mutableClassSymbol = session.findClassSymbol(piecemealClassId.mutable) ?: return null
  val typeArguments = piecemealClassSymbol.typeParameterSymbols.map { it.toConeType() }
  return createMemberFunction(
    owner = piecemealClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnType = mutableClassSymbol.constructType(typeArguments.toTypedArray())
  )
}

fun FirExtension.createFunCopy(
  piecemealClassSymbol: FirClassSymbol<*>,
  callableId: CallableId,
): FirSimpleFunction? {
  val piecemealClassId = piecemealClassSymbol.classId
  val mutableClassSymbol = session.findClassSymbol(piecemealClassId.mutable) ?: return null
  val typeArguments = piecemealClassSymbol.typeParameterSymbols.map { it.toConeType() }

  val parameterName = "transform"
  return createMemberFunction(
    owner = piecemealClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnType = piecemealClassSymbol.constructType(typeArguments.toTypedArray()),
  ) {
    status {
      isInline = true
    }
    valueParameter(
      name = Name.identifier(parameterName),
      type = fun1Ext(session, receiverType = mutableClassSymbol.constructType(typeArguments.toTypedArray())),
    )
  }.apply {
    replaceContractDescription {
      callsInPlaceExactlyOnce(parameterIndex = 0, name = parameterName)
    }
  }
}

fun FirExtension.createFunMutableBuild(
  mutableClassSymbol: FirClassSymbol<*>,
  callableId: CallableId,
): FirSimpleFunction? {
  val piecemealClassId = mutableClassSymbol.classId.outerClassId!!
  val piecemealClassSymbol = session.findClassSymbol(piecemealClassId)!!
  val typeArguments = mutableClassSymbol.typeParameterSymbols.map { it.toConeType() }
  return createMemberFunction(
    owner = mutableClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnType = piecemealClassSymbol.constructType(typeArguments.toTypedArray()),
  )
}

fun FirExtension.createFunMutableSetter(
  mutableClassSymbol: FirClassSymbol<*>,
  callableId: CallableId,
): FirSimpleFunction? {
  val piecemealClassId = mutableClassSymbol.classId.outerClassId!!
  val piecemealClassSymbol = session.findClassSymbol(piecemealClassId)!!
  val typeArguments = mutableClassSymbol.typeParameterSymbols.map { it.toConeType() }

  val parameterSymbol = getPrimaryConstructorValueParameters(piecemealClassSymbol)
    .singleOrNull { it.name.toJavaSetter() == callableId.callableName } ?: return null
  val substitutor = substitutor(piecemealClassSymbol, mutableClassSymbol, session)
  return createMemberFunction(
    owner = mutableClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnType = mutableClassSymbol.constructType(typeArguments.toTypedArray()),
  ) {
    valueParameter(
      name = callableId.callableName.toParameterName(),
      type = substitutor.substituteOrSelf(parameterSymbol.resolvedReturnType),
    )
  }
}

fun FirExtension.createPropertyMutableValue(
  mutableClassSymbol: FirClassSymbol<*>,
  callableId: CallableId
): FirProperty? {
  val piecemealClassId = mutableClassSymbol.classId.outerClassId!!
  val piecemealClassSymbol = session.findClassSymbol(piecemealClassId)!!

  val parameter = getPrimaryConstructorValueParameters(piecemealClassSymbol)
    .singleOrNull { it.name == callableId.callableName } ?: return null
  val substitutor = substitutor(piecemealClassSymbol, mutableClassSymbol, session)
  return createMemberProperty(
    owner = mutableClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnType = substitutor.substituteOrSelf(parameter.resolvedReturnType),
    isVal = false,
    hasBackingField = false,
  )
}

fun FirExtension.createFunPiecemealDsl(
  companionClassSymbol: FirClassSymbol<*>,
  callableId: CallableId,
): FirSimpleFunction? {
  val piecemealClassId = companionClassSymbol.classId.outerClassId!!
  val piecemealClassSymbol = session.findClassSymbol(piecemealClassId)!!
  val mutableClassSymbol = session.findClassSymbol(piecemealClassId.mutable) ?: return null

  val paramName = "builderAction"
  return createMemberFunction(
    owner = companionClassSymbol,
    key = Piecemeal.Key,
    name = callableId.callableName,
    returnTypeProvider = { typeParameters ->
      piecemealClassSymbol.constructType(typeParameters.map { it.toConeType() }.toTypedArray())
    },
  ) {
    status {
      isInline = true
    }

    copyTypeParametersFrom(piecemealClassSymbol, session)

    valueParameter(
      name = Name.identifier(paramName),
      typeProvider = { typeParameters ->
        val builderType = mutableClassSymbol.constructType(typeParameters.map { it.toConeType() }.toTypedArray())
        fun1Ext(session, receiverType = builderType)
      },
    )
  }.apply {
    replaceContractDescription {
      callsInPlaceExactlyOnce(parameterIndex = 0, name = paramName)
    }
  }
}
