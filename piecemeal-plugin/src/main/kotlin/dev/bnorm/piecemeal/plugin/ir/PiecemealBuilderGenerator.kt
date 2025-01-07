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

package dev.bnorm.piecemeal.plugin.ir

import dev.bnorm.piecemeal.plugin.Piecemeal
import dev.bnorm.piecemeal.plugin.toJavaSetter
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.deepCopyWithoutPatchingParents
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
class PiecemealBuilderGenerator(
  private val context: IrPluginContext,
) : IrElementVisitorVoid {

  // TODO support class type parameters
  // TODO remove defaults from primary constructor?
  // TODO some things JvmSynthetic

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
        Name.identifier("build") -> when {
          (declaration.parent as? IrClass)?.isCompanion == true -> generateBuilderFunction(declaration)
          else -> generateBuildFunction(declaration)
        }

        else -> generateBuilderSetter(declaration)
      }
    }
  }

  override fun visitConstructor(declaration: IrConstructor) {
    val origin = declaration.origin
    if (origin is IrDeclarationOrigin.GeneratedByPlugin && origin.pluginKey == Piecemeal.Key) {
      if (declaration.body == null) {
        declaration.body = generateDefaultConstructor(declaration)
      }
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
    val receiver = function.dispatchReceiverParameter ?: return null
    val piecemealClass = function.parent as? IrClass ?: return null
    val builderClass = function.returnType.classifierOrNull?.owner as? IrClass ?: return null
    val builderConstructor = builderClass.primaryConstructor ?: return null

    val properties = piecemealClass.declarations.filterIsInstance<IrProperty>()
      .associateBy { it.name }

    val irBuilder = DeclarationIrBuilder(context, function.symbol)
    return irBuilder.irBlockBody {
      val tmp = irTemporary(nameHint = "builder", value = irCall(builderConstructor))

      for (builderProperty in builderClass.declarations.filterIsInstance<IrProperty>()) {
        val piecemealProperty = properties[builderProperty.name] ?: continue
        +irCall(builderProperty.setter!!).apply {
          dispatchReceiver = irGet(tmp)
          putValueArgument(0, irCall(piecemealProperty.getter!!).apply {
            dispatchReceiver = irGet(receiver)
          })
        }
      }

      +irReturn(irGet(tmp))
    }
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
    val receiver = function.dispatchReceiverParameter ?: return null
    val builderClass = function.parent as? IrClass ?: return null
    val property = builderClass.declarations.filterIsInstance<IrProperty>()
      .single { it.name.toJavaSetter() == function.name }

    val irBuilder = DeclarationIrBuilder(context, function.symbol)
    return irBuilder.irBlockBody {
      val propertySet = irCall(property.setter!!).apply {
        dispatchReceiver = irGet(receiver)
        putValueArgument(0, irGet(function.valueParameters[0]))
      }

      +propertySet
      +irReturn(irGet(receiver))
    }
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
   *         nickname = nickname,
   *         age = age,
   *     )
   * }
   * ```
   */
  private fun generateBuildFunction(function: IrSimpleFunction): IrBody? {
    val builderClass = function.parent as? IrClass ?: return null
    val piecemealClass = function.returnType.classifierOrNull?.owner as? IrClass ?: return null
    val piecemealConstructor = piecemealClass.primaryConstructor ?: return null

    val irBuilder = DeclarationIrBuilder(context, function.symbol)
    return irBuilder.irBlockBody {
      val variables = irBuilderParameters(
        builderProperties = builderClass.declarations.filterIsInstance<IrProperty>(),
        builderParameter = function.dispatchReceiverParameter!!,
        constructorParameters = piecemealConstructor.valueParameters
      )

      val constructorCall = irCall(piecemealConstructor).apply {
        variables.forEachIndexed { index, variable ->
          putValueArgument(index, irGet(variable))
        }
      }

      +irReturn(constructorCall)
    }
  }

  private fun generateDefaultConstructor(declaration: IrConstructor): IrBody? {
    val returnType = declaration.returnType as? IrSimpleType ?: return null
    val parentClass = declaration.parent as? IrClass ?: return null
    val anySymbol = context.irBuiltIns.anyClass.owner.primaryConstructor?.symbol ?: return null

    val irBuilder = DeclarationIrBuilder(context, declaration.symbol)
    return irBuilder.irBlockBody {
      +IrDelegatingConstructorCallImpl(
        UNDEFINED_OFFSET, UNDEFINED_OFFSET,
        context.irBuiltIns.anyType,
        anySymbol,
        typeArgumentsCount = 0,
      )
      +IrInstanceInitializerCallImpl(
        UNDEFINED_OFFSET, UNDEFINED_OFFSET,
        parentClass.symbol,
        returnType,
      )
    }
  }

  /**
   * ```kotlin
   * fun build(builder: Person.Builder.() -> Unit): Person {
   *     val tmp = Builder()
   *     tmp.builder()
   *     return tmp
   * }
   * ```
   */
  private fun generateBuilderFunction(function: IrSimpleFunction): IrBody? {
    val builderLambda = function.valueParameters.single()
    val builderType = (builderLambda.type as IrSimpleType).arguments[0] as IrType
    val builderClass = builderType.classifierOrNull?.owner as? IrClass ?: return null
    val builderConstructor = builderClass.primaryConstructor ?: return null

    val invoke = builderLambda.type.classOrNull!!.functions
      .filter { !it.owner.isFakeOverride } // TODO best way to find single access method?
      .single()

    val build = builderClass.functions
      .filter { it.name.asString() == "build" } // TODO best way to find build method?
      .single()

    val irBuilder = DeclarationIrBuilder(context, function.symbol)
    return irBuilder.irBlockBody {
      val tmp = irTemporary(value = irCall(builderConstructor))

      +irCall(invoke).apply {
        dispatchReceiver = irGet(builderLambda)
        putValueArgument(0, irGet(tmp))
      }

      +irReturn(irCall(build).apply {
        dispatchReceiver = irGet(tmp)
      })
    }
  }

  private fun IrBlockBodyBuilder.irBuilderParameters(
    builderProperties: List<IrProperty>,
    builderParameter: IrValueParameter,
    constructorParameters: List<IrValueParameter>
  ): MutableList<IrVariable> {
    val variables = mutableListOf<IrVariable>()
    val transformer = object : IrElementTransformerVoid() {
      override fun visitGetValue(expression: IrGetValue): IrExpression {
        val index = constructorParameters.indexOfFirst { it.symbol == expression.symbol }
        if (index != -1) {
          return irGet(variables[index])
        }
        return super.visitGetValue(expression)
      }
    }

    for (valueParameter in constructorParameters) {
      val getter = builderProperties.single { it.name == valueParameter.name }.getter!!
      variables += irTemporary(nameHint = valueParameter.name.asString(), value = irBlock {
        val value = irTemporary(irCall(getter).apply {
          dispatchReceiver = irGet(builderParameter)
        })

        val defaultValue = valueParameter.defaultValue
        val whenNullValue = if (defaultValue != null) {
          defaultValue.expression.deepCopyWithoutPatchingParents().transform(transformer, null)
        } else {
          // TODO IllegalStateException
          irThrow(irCall(context.irBuiltIns.illegalArgumentExceptionSymbol).apply {
            putValueArgument(0, irString("Missing required parameter '${valueParameter.name}'."))
          })
        }

        +irIfNull(getter.returnType, irGet(value), whenNullValue, irGet(value))
      })
    }
    return variables
  }
}
