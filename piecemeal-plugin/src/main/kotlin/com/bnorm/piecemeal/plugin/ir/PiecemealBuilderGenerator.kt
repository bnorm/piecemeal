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
import com.bnorm.piecemeal.plugin.toJavaSetter
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
import org.jetbrains.kotlin.ir.declarations.isPropertyAccessor
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

class PiecemealBuilderGenerator(
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
          declaration.isPropertyAccessor -> return
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

  /**
   * ```kotlin
   * fun newBuilder(): Builder {
   *     val builder = Builder()
   *     builder.name = name
   *     builder.nickname = nickname
   *     builder.age = age
   *     return builder
   * }
   * ```
   */
  private fun generateNewBuilderFunction(function: IrSimpleFunction): IrBody? {
    val constructedType = function.returnType as? IrSimpleType ?: return null
    val constructedClass = constructedType.classifier.owner as? IrClass ?: return null
    val constructor = constructedClass.primaryConstructor ?: return null

    // TODO load values from current instance properties

    val constructorCall = constructor.irConstructorCall()
    val returnStatement = IrReturnImpl(
      UNDEFINED_OFFSET, UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      function.symbol,
      constructorCall,
    )
    return irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(returnStatement))
  }

  /**
   * ```kotlin
   * fun setName(name: String?): Builder {
   *     this.name = name
   *     return this
   * }
   * ```
   */
  private fun generateBuilderSetter(function: IrSimpleFunction): IrBody? {
    val receiver = function.dispatchReceiverParameter?.irGetValue() ?: return null

    val builderType = function.parent as? IrClass ?: return null
    val builderProperties = builderType.declarations.filterIsInstance<IrProperty>()
    val setter = builderProperties.single { it.name.toJavaSetter() == function.name }.setter!!

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

  /**
   * ```kotlin
   * fun build(): Person {
   *     val name = name ?: throw IllegalStateException("Missing required parameter 'name'.")
   *     val nickname = nickname ?: name
   *     val age = age ?: 0
   *
   *     return Person(
   *         name = name,
   *         nickname = nickname ?: name,
   *         age = age,
   *     )
   * }
   * ```
   */
  private fun generateBuildFunction(function: IrSimpleFunction): IrBody? {
    val builderType = function.parent as? IrClass ?: return null
    val constructedType = function.returnType as? IrSimpleType ?: return null
    val constructedClass = constructedType.classifier.owner as? IrClass ?: return null
    val constructor = constructedClass.primaryConstructor ?: return null

    // TODO check and non-null and add constructor defaults
    // TODO remove defaults from primary constructor?

    val builderProperties = builderType.declarations.filterIsInstance<IrProperty>()
    val constructorCall = constructor.irConstructorCall().apply {
      for ((index, valueParameter) in constructor.valueParameters.withIndex()) {
        // TODO CHECK_NOT_NULL or other uninitialized error
        val getter = builderProperties.single { it.name == valueParameter.name }.getter!!
        putValueArgument(index, getter.irCall().apply {
          dispatchReceiver = function.dispatchReceiverParameter!!.irGetValue()
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
