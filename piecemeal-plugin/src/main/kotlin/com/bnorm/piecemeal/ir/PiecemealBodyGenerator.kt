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

package com.bnorm.piecemeal.ir

import com.bnorm.piecemeal.PiecemealKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

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
    if (origin is IrDeclarationOrigin.GeneratedByPlugin && origin.pluginKey == PiecemealKey) {
      require(declaration.body == null)
      declaration.body = generateNewBuilderFunction(declaration)
    }
  }

  override fun visitConstructor(declaration: IrConstructor) {
    val origin = declaration.origin
    if (origin is IrDeclarationOrigin.GeneratedByPlugin && origin.pluginKey == PiecemealKey) {
      require(declaration.body == null)
      declaration.body = generateBuilderConstructor(declaration)
    }
  }

  private fun generateNewBuilderFunction(function: IrSimpleFunction): IrBody? {
    val constructedType = function.returnType as? IrSimpleType ?: return null
    val constructedClassSymbol = constructedType.classifier
    val constructedClass = constructedClassSymbol.owner as? IrClass ?: return null
    val constructor = constructedClass.primaryConstructor ?: return null
    val constructorCall = IrConstructorCallImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      constructedType,
      constructor.symbol,
      typeArgumentsCount = 0,
      constructorTypeArgumentsCount = 0,
      valueArgumentsCount = 0
    )
    val returnStatement =
      IrReturnImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.nothingType, function.symbol, constructorCall)
    return irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(returnStatement))
  }

  private fun generateBuilderConstructor(declaration: IrConstructor): IrBody? {
    val type = declaration.returnType as? IrSimpleType ?: return null
    val symbol = irBuiltIns.anyClass.owner.primaryConstructor?.symbol ?: return null

    val delegatingAnyCall = IrDelegatingConstructorCallImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      irBuiltIns.anyType,
      symbol,
      typeArgumentsCount = 0,
      valueArgumentsCount = 0
    )
    val initializerCall = IrInstanceInitializerCallImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      (declaration.parent as? IrClass)?.symbol ?: return null,
      type
    )


    return irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, listOf(delegatingAnyCall, initializerCall))
  }
}
