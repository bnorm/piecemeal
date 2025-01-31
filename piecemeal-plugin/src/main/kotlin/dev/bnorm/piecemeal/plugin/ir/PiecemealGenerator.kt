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
import dev.bnorm.piecemeal.plugin.fir.BUILD_FUN_NAME
import dev.bnorm.piecemeal.plugin.fir.COPY_FUN_NAME
import dev.bnorm.piecemeal.plugin.fir.TO_MUTABLE_FUN_NAME
import dev.bnorm.piecemeal.plugin.toJavaSetter
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(UnsafeDuringIrConstructionAPI::class)
class PiecemealGenerator(
  private val context: IrPluginContext,
) : IrElementVisitorVoid {
  private val nullableStringType = context.irBuiltIns.stringType.makeNullable()
  private val illegalStateExceptionConstructor =
    context.referenceConstructors(ClassId.topLevel(FqName("kotlin.IllegalStateException")))
      .single { constructor ->
        val parameter = constructor.owner.valueParameters.singleOrNull() ?: return@single false
        parameter.type == nullableStringType
      }

  // TODO support class type parameters
  // TODO remove defaults from primary constructor?
  // TODO some things JvmSynthetic

  override fun visitElement(element: IrElement) {
    when (element) {
      is IrDeclaration, is IrFile, is IrModuleFragment -> element.acceptChildrenVoid(this)
      else -> Unit
    }
  }

  override fun visitClass(declaration: IrClass) {
    if (declaration.origin == Piecemeal.ORIGIN) {
      val declarations = declaration.declarations

      val mutablePropertyBackings = declarations
        .filterIsInstance<IrProperty>()
        .map { generateBacking(declaration, it) }

      declarations.addAll(0, mutablePropertyBackings.flatMap { listOf(it.flag, it.holder) })
    }

    declaration.acceptChildrenVoid(this)
  }

  override fun visitSimpleFunction(declaration: IrSimpleFunction) {
    if (declaration.origin == Piecemeal.ORIGIN && declaration.body == null) {
      declaration.body = when (declaration.name) {
        TO_MUTABLE_FUN_NAME -> generateToMutableFunction(declaration)
        BUILD_FUN_NAME -> when {
          (declaration.parent as? IrClass)?.isCompanion == true -> generateBuilderFunction(declaration)
          else -> generateBuildFunction(declaration)
        }

        COPY_FUN_NAME -> generateCopyFunction(declaration)

        else -> generateMutableSetter(declaration)
      }
    }
  }

  override fun visitConstructor(declaration: IrConstructor) {
    if (declaration.origin == Piecemeal.ORIGIN) {
      if (declaration.body == null) {
        declaration.body = generateDefaultConstructor(declaration)
      }
    }
  }

  /**
   * ```kotlin
   * fun toMutable(): Mutable {
   *     val mutable = Mutable()
   *     mutable.name = name
   *     mutable.nickname = nickname
   *     mutable.age = age
   *     return mutable
   * }
   * ```
   */
  private fun generateToMutableFunction(function: IrSimpleFunction): IrBody? {
    val receiver = function.dispatchReceiverParameter ?: return null
    val piecemealClass = function.parent as? IrClass ?: return null
    val mutableClass = function.returnType.classifierOrNull?.owner as? IrClass ?: return null
    val mutableConstructor = mutableClass.primaryConstructor ?: return null

    val properties = piecemealClass.declarations.filterIsInstance<IrProperty>()
      .associateBy { it.name }

    val irBuilder = DeclarationIrBuilder(context, function.symbol)
    return irBuilder.irBlockBody {
      val tmp = irTemporary(nameHint = "mutable", value = irCall(mutableConstructor))

      for (mutableProperty in mutableClass.declarations.filterIsInstance<IrProperty>()) {
        val piecemealProperty = properties[mutableProperty.name] ?: continue
        +irCall(mutableProperty.setter!!).apply {
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
   * fun setName(name: String?): Mutable {
   *     this.name = name
   *     return this
   * }
   * ```
   */
  private fun generateMutableSetter(function: IrSimpleFunction): IrBody? {
    val receiver = function.dispatchReceiverParameter ?: return null
    val mutableClass = function.parent as? IrClass ?: return null
    val property = mutableClass.declarations.filterIsInstance<IrProperty>()
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
    val mutableClass = function.parent as? IrClass ?: return null
    val piecemealClass = function.returnType.classifierOrNull?.owner as? IrClass ?: return null
    val piecemealConstructor = piecemealClass.primaryConstructor ?: return null

    val irBuilder = DeclarationIrBuilder(context, function.symbol)
    return irBuilder.irBlockBody {
      val variables = irMutableConstructorParameters(
        mutableProperties = mutableClass.declarations.filterIsInstance<IrProperty>(),
        buildFunctionReceiverParameter = function.dispatchReceiverParameter!!,
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

  /**
   * ```kotlin
   * fun copy(transform: Person.Mutable.() -> Unit): Person {
   *     val tmp = toMutable()
   *     tmp.transform()
   *     return tmp.build()
   * }
   * ```
   */
  private fun generateCopyFunction(function: IrSimpleFunction): IrBody? {
    val transformLambda = function.valueParameters.single()
    val mutableType = (transformLambda.type as IrSimpleType).arguments[0] as IrType
    val mutableClass = mutableType.classifierOrNull?.owner as? IrClass ?: return null

    val piecemealClass = function.parentAsClass
    val toMutableFunction = piecemealClass.functions.single {
      it.name == TO_MUTABLE_FUN_NAME &&
        it.extensionReceiverParameter == null &&
        it.valueParameters.isEmpty()
    }

    val invoke = transformLambda.type.classOrFail.functions
      .single {
        val owner = it.owner
        owner.name == Name.identifier("invoke") &&
          owner.extensionReceiverParameter == null &&
          owner.valueParameters.size == 1 &&
          owner.valueParameters.single().type.classifierOrNull is IrTypeParameterSymbol
      }

    val build = mutableClass.functions
      .single {
        it.name == BUILD_FUN_NAME &&
          it.extensionReceiverParameter == null &&
          it.valueParameters.isEmpty()
      }

    val irBuilder = DeclarationIrBuilder(context, function.symbol)
    return irBuilder.irBlockBody {
      val tmp = irTemporary(
        value = irCall(toMutableFunction).apply {
          dispatchReceiver = irGet(function.dispatchReceiverParameter!!)
        },
      )

      +irCall(invoke).apply {
        dispatchReceiver = irGet(transformLambda)
        putValueArgument(0, irGet(tmp))
      }

      +irReturn(irCall(build).apply {
        dispatchReceiver = irGet(tmp)
      })
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
   * fun build(builder: Person.Mutable.() -> Unit): Person {
   *     val tmp = Mutable()
   *     tmp.build()
   *     return tmp
   * }
   * ```
   */
  private fun generateBuilderFunction(function: IrSimpleFunction): IrBody? {
    val builderLambda = function.valueParameters.single()
    val builderType = (builderLambda.type as IrSimpleType).arguments[0] as IrType
    val mutableClass = builderType.classifierOrNull?.owner as? IrClass ?: return null
    val mutableConstructor = mutableClass.primaryConstructor ?: return null

    val invoke = builderLambda.type.classOrNull!!.functions
      .filter { !it.owner.isFakeOverride } // TODO best way to find single access method?
      .single()

    val build = mutableClass.functions
      .filter { it.name.asString() == "build" } // TODO best way to find build method?
      .single()

    val irBuilder = DeclarationIrBuilder(context, function.symbol)
    return irBuilder.irBlockBody {
      val tmp = irTemporary(value = irCall(mutableConstructor))

      +irCall(invoke).apply {
        dispatchReceiver = irGet(builderLambda)
        putValueArgument(0, irGet(tmp))
      }

      +irReturn(irCall(build).apply {
        dispatchReceiver = irGet(tmp)
      })
    }
  }

  private fun IrBlockBodyBuilder.irMutableConstructorParameters(
    mutableProperties: List<IrProperty>,
    buildFunctionReceiverParameter: IrValueParameter,
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
      val mutableProperty = mutableProperties.single { it.name == valueParameter.name }.mutablePropertyBacking!!
      variables += irTemporary(nameHint = valueParameter.name.asString(), value = irBlock {
        val defaultValue = valueParameter.defaultValue
        val elsePart = if (defaultValue != null) {
          defaultValue.expression.deepCopyWithoutPatchingParents().transform(transformer, null)
        } else {
          irThrow(irCall(illegalStateExceptionConstructor).apply {
            putValueArgument(0, irString("Uninitialized property '${valueParameter.name}'."))
          })
        }

        +irIfThenElse(
          type = valueParameter.type,
          condition = irGetField(irGet(buildFunctionReceiverParameter), mutableProperty.flag),
          thenPart = irGetField(irGet(buildFunctionReceiverParameter), mutableProperty.holder),
          elsePart = elsePart
        )
      })
    }
    return variables
  }

  private fun generateBacking(
    klass: IrClass,
    property: IrProperty,
  ): MutablePropertyBacking {
    val getter = requireNotNull(property.getter)
    val setter = requireNotNull(property.setter)
    property.backingField = null

    val isPrimitive = getter.returnType.isPrimitiveType()
    val backingType = when {
      isPrimitive -> getter.returnType
      else -> getter.returnType.makeNullable()
    }

    val flagField = context.irFactory.buildField {
      origin = Piecemeal.ORIGIN
      visibility = DescriptorVisibilities.PRIVATE
      name = Name.identifier("${property.name}\$PiecemealFlag")
      type = context.irBuiltIns.booleanType
    }.apply {
      parent = klass
      initializer = context.irFactory.createExpressionBody(
        expression = false.toIrConst(context.irBuiltIns.booleanType)
      )
    }

    val holderField = context.irFactory.buildField {
      origin = Piecemeal.ORIGIN
      visibility = DescriptorVisibilities.PRIVATE
      name = Name.identifier("${property.name}\$PiecemealHolder")
      type = backingType
    }.apply {
      parent = klass
      initializer = context.irFactory.createExpressionBody(
        expression = when (isPrimitive) {
          true -> IrConstImpl.defaultValueForType(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, backingType)
          false -> null.toIrConst(backingType)
        }
      )
    }

    getter.origin = Piecemeal.ORIGIN
    getter.body = DeclarationIrBuilder(context, getter.symbol).irBlockBody {
      val dispatch = getter.dispatchReceiverParameter!!
      +irIfThenElse(
        type = getter.returnType,
        condition = irGetField(irGet(dispatch), flagField),
        thenPart = irReturn(irGetField(irGet(dispatch), holderField)),
        elsePart = irThrow(irCall(illegalStateExceptionConstructor).apply {
          putValueArgument(0, irString("Uninitialized property '${property.name}'."))
        })
      )
    }

    setter.origin = Piecemeal.ORIGIN
    setter.body = DeclarationIrBuilder(context, setter.symbol).irBlockBody {
      val dispatch = setter.dispatchReceiverParameter!!
      +irSetField(irGet(dispatch), holderField, irGet(setter.valueParameters[0]))
      +irSetField(irGet(dispatch), flagField, true.toIrConst(context.irBuiltIns.booleanType))
    }

    val mutablePropertyBacking = MutablePropertyBacking(holderField, flagField)
    property.mutablePropertyBacking = mutablePropertyBacking
    return mutablePropertyBacking
  }
}
