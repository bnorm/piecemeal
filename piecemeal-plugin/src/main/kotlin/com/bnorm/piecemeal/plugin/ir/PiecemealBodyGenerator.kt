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

package com.bnorm.piecemeal.plugin.ir

import com.bnorm.piecemeal.plugin.Piecemeal
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.isGetter
import org.jetbrains.kotlin.ir.util.isSetter
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.Name

class PiecemealBodyGenerator(
  context: IrPluginContext,
) : IrElementVisitorVoid {
  private val irFactory = context.irFactory
  private val irBuiltIns = context.irBuiltIns

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration, is IrFile, is IrModuleFragment -> element.acceptChildrenVoid(this)
      else -> Unit
    }
  }

  override fun visitSimpleFunction(declaration: IrSimpleFunction) {
    val origin = declaration.origin
    if (origin is IrDeclarationOrigin.GeneratedByPlugin && origin.pluginKey == Piecemeal.Key) {
      require(declaration.body == null)
      declaration.body = when (declaration.name) {
        Name.identifier("newBuilder") -> generateNewBuilderFunction(declaration)
        Name.identifier("build") -> generateBuildFunction(declaration)
        else -> when {
          declaration.isGetter -> generatePropertyGetter(declaration)
          declaration.isSetter -> generatePropertySetter(declaration)
          else -> generateBuilderSetter(declaration)
        }
      }
    }
  }

  override fun visitConstructor(declaration: IrConstructor) {
    val origin = declaration.origin
    if (origin is IrDeclarationOrigin.GeneratedByPlugin && origin.pluginKey == Piecemeal.Key) {
      require(declaration.body == null)
      declaration.body = generateBuilderConstructor(declaration)
    }
  }

  private fun generateNewBuilderFunction(function: IrSimpleFunction): IrBody? {
    val constructedType = function.returnType as? IrSimpleType ?: return null
    val constructedClass = constructedType.classifier.owner as? IrClass ?: return null
    val constructor = constructedClass.primaryConstructor ?: return null

    // TODO call builder with existing instance to load values

    val constructorCall = constructor.irConstructorCall()
    val returnStatement = IrReturnImpl(
      UNDEFINED_OFFSET, UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      function.symbol,
      constructorCall,
    )
    return irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(returnStatement))
  }

  /*
          FUN DEFAULT_PROPERTY_ACCESSOR name:<get-name> visibility:private modality:FINAL <> ($this:<root>.Person.Builder) returnType:kotlin.String?
            correspondingProperty: PROPERTY name:name visibility:private modality:FINAL [var]
            $this: VALUE_PARAMETER name:<this> type:<root>.Person.Builder
            BLOCK_BODY
              RETURN type=kotlin.Nothing from='private final fun <get-name> (): kotlin.String? declared in <root>.Person.Builder'
                GET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:name type:kotlin.String? visibility:private' type=kotlin.String? origin=null
                  receiver: GET_VAR '<this>: <root>.Person.Builder declared in <root>.Person.Builder.<get-name>' type=<root>.Person.Builder origin=null
   */
  private fun generatePropertyGetter(function: IrSimpleFunction): IrBody? {
    val property = function.correspondingPropertySymbol!!.owner
    val backingField = property.backingField ?: return null
    val receiver = function.dispatchReceiverParameter?.irGetValue() ?: return null
    val fieldCall = backingField.irGetField(receiver)
    val returnStatement = IrReturnImpl(
      UNDEFINED_OFFSET, UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      function.symbol,
      fieldCall,
    )
    return irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(returnStatement))
  }

  /*
          FUN DEFAULT_PROPERTY_ACCESSOR name:<set-name> visibility:private modality:FINAL <> ($this:<root>.Person.Builder, <set-?>:kotlin.String?) returnType:kotlin.Unit
            correspondingProperty: PROPERTY name:name visibility:private modality:FINAL [var]
            $this: VALUE_PARAMETER name:<this> type:<root>.Person.Builder
            VALUE_PARAMETER name:<set-?> index:0 type:kotlin.String?
            BLOCK_BODY
              SET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:name type:kotlin.String? visibility:private' type=kotlin.Unit origin=null
                receiver: GET_VAR '<this>: <root>.Person.Builder declared in <root>.Person.Builder.<set-name>' type=<root>.Person.Builder origin=null
                value: GET_VAR '<set-?>: kotlin.String? declared in <root>.Person.Builder.<set-name>' type=kotlin.String? origin=null
   */
  private fun generatePropertySetter(function: IrSimpleFunction): IrBody? {
    val property = function.correspondingPropertySymbol!!.owner
    val receiver = function.dispatchReceiverParameter?.irGetValue() ?: return null
    val value = function.valueParameters[0].irGetValue()
    val setField = IrSetFieldImpl(
      UNDEFINED_OFFSET, UNDEFINED_OFFSET,
      property.backingField!!.symbol,
      receiver,
      value,
      irBuiltIns.unitType,
    )
    return irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(setField))
  }

  /*
        FUN name:name visibility:public modality:FINAL <> ($this:<root>.Person.Builder, name:kotlin.String) returnType:<root>.Person.Builder
          $this: VALUE_PARAMETER name:<this> type:<root>.Person.Builder
          VALUE_PARAMETER name:name index:0 type:kotlin.String
          BLOCK_BODY
            CALL 'private final fun <set-name> (<set-?>: kotlin.String?): kotlin.Unit declared in <root>.Person.Builder' type=kotlin.Unit origin=EQ
              $this: GET_VAR '<this>: <root>.Person.Builder declared in <root>.Person.Builder.name' type=<root>.Person.Builder origin=null
              <set-?>: GET_VAR 'name: kotlin.String declared in <root>.Person.Builder.name' type=kotlin.String origin=null
            RETURN type=kotlin.Nothing from='public final fun name (name: kotlin.String): <root>.Person.Builder declared in <root>.Person.Builder'
              GET_VAR '<this>: <root>.Person.Builder declared in <root>.Person.Builder.name' type=<root>.Person.Builder origin=null
   */
  private fun generateBuilderSetter(function: IrSimpleFunction): IrBody? {
    val receiver = function.dispatchReceiverParameter?.irGetValue() ?: return null

    val builderType = function.parent as? IrClass ?: return null
    val builderProperties = builderType.declarations.filterIsInstance<IrProperty>()
    val setter = builderProperties.single { it.name == function.name }.setter!!

    val propertySet = setter.irCall().apply {
      dispatchReceiver = receiver
      putValueArgument(0, function.valueParameters[0].irGetValue())
    }
    val returnStatement = IrReturnImpl(
      UNDEFINED_OFFSET, UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      function.symbol,
      receiver,
    )
    return irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(propertySet, returnStatement))
  }

  /*
        FUN name:build visibility:public modality:FINAL <> ($this:<root>.Person.Builder) returnType:<root>.Person
          $this: VALUE_PARAMETER name:<this> type:<root>.Person.Builder
          BLOCK_BODY
            RETURN type=kotlin.Nothing from='public final fun build (): <root>.Person declared in <root>.Person.Builder'
              CONSTRUCTOR_CALL 'public constructor <init> (name: kotlin.String) [primary] declared in <root>.Person' type=<root>.Person origin=null
                name: CALL 'public final fun CHECK_NOT_NULL <T0> (arg0: T0 of kotlin.internal.ir.CHECK_NOT_NULL?): {T0 of kotlin.internal.ir.CHECK_NOT_NULL & Any} declared in kotlin.internal.ir' type=kotlin.String origin=EXCLEXCL
                  <T0>: kotlin.String
                  arg0: CALL 'private final fun <get-name> (): kotlin.String? declared in <root>.Person.Builder' type=kotlin.String? origin=GET_PROPERTY
                    $this: GET_VAR '<this>: <root>.Person.Builder declared in <root>.Person.Builder.build' type=<root>.Person.Builder origin=null
   */
  private fun generateBuildFunction(function: IrSimpleFunction): IrBody? {
    val builderType = function.parent as? IrClass ?: return null
    val constructedType = function.returnType as? IrSimpleType ?: return null
    val constructedClass = constructedType.classifier.owner as? IrClass ?: return null
    val constructor = constructedClass.primaryConstructor ?: return null

    val builderProperties = builderType.declarations.filterIsInstance<IrProperty>()
    val constructorCall = constructor.irConstructorCall().apply {
      for ((index, valueParameter) in constructor.valueParameters.withIndex()) {
        // TODO CHECK_NOT_NULL or other uninitialized error
        val getter = builderProperties.single { it.name == valueParameter.name }.getter!!
        putValueArgument(index, getter.irCall().apply {
          dispatchReceiver = getter.dispatchReceiverParameter!!.irGetValue()
        })
      }
    }
    val returnStatement = IrReturnImpl(
      UNDEFINED_OFFSET, UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      function.symbol,
      constructorCall,
    )
    return irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(returnStatement))
  }

  /*
        CONSTRUCTOR visibility:public <> () returnType:<root>.Person.Builder [primary]
          BLOCK_BODY
            DELEGATING_CONSTRUCTOR_CALL 'public constructor <init> () [primary] declared in kotlin.Any'
            INSTANCE_INITIALIZER_CALL classDescriptor='CLASS CLASS name:Builder modality:FINAL visibility:public superTypes:[kotlin.Any]'
   */
  private fun generateBuilderConstructor(declaration: IrConstructor): IrBody? {
    val type = declaration.returnType as? IrSimpleType ?: return null
    val anySymbol = irBuiltIns.anyClass.owner.primaryConstructor?.symbol ?: return null
    val classSymbol = (declaration.parent as? IrClass)?.symbol ?: return null

    val delegatingAnyCall = IrDelegatingConstructorCallImpl(
      UNDEFINED_OFFSET, UNDEFINED_OFFSET,
      irBuiltIns.anyType,
      anySymbol,
      typeArgumentsCount = 0,
      valueArgumentsCount = 0
    )
    val initializerCall = IrInstanceInitializerCallImpl(
      UNDEFINED_OFFSET, UNDEFINED_OFFSET,
      classSymbol,
      type,
    )
    return irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(delegatingAnyCall, initializerCall))
  }
}

private fun IrValueDeclaration.irGetValue(
  startOffset: Int = UNDEFINED_OFFSET,
  endOffset: Int = UNDEFINED_OFFSET,
): IrGetValue = IrGetValueImpl(
  startOffset = startOffset,
  endOffset = endOffset,
  type = type,
  symbol = symbol,
)

private fun IrField.irGetField(
  receiver: IrExpression? = null,
  startOffset: Int = UNDEFINED_OFFSET,
  endOffset: Int = UNDEFINED_OFFSET,
): IrGetField = IrGetFieldImpl(
  startOffset = startOffset,
  endOffset = endOffset,
  symbol = symbol,
  type = type,
  receiver = receiver,
)

private fun IrSimpleFunction.irCall(
  startOffset: Int = UNDEFINED_OFFSET,
  endOffset: Int = UNDEFINED_OFFSET,
): IrCall = IrCallImpl(
  startOffset = startOffset,
  endOffset = endOffset,
  type = returnType,
  symbol = symbol,
  typeArgumentsCount = typeParameters.size,
  valueArgumentsCount = valueParameters.size,
)

private fun IrConstructor.irConstructorCall(
  startOffset: Int = UNDEFINED_OFFSET,
  endOffset: Int = UNDEFINED_OFFSET,
): IrConstructorCall {
  val classTypeParametersCount = parentAsClass.typeParameters.size
  val constructorTypeParametersCount = typeParameters.size
  return IrConstructorCallImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = returnType,
    symbol = symbol,
    typeArgumentsCount = classTypeParametersCount + constructorTypeParametersCount,
    constructorTypeArgumentsCount = constructorTypeParametersCount,
    valueArgumentsCount = valueParameters.size
  )
}
