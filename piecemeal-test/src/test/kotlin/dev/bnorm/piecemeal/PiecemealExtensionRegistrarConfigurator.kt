package dev.bnorm.piecemeal

import dev.bnorm.piecemeal.plugin.PiecemealConfiguration
import dev.bnorm.piecemeal.plugin.fir.PiecemealFirExtensionRegistrar
import dev.bnorm.piecemeal.plugin.ir.PiecemealIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

fun TestConfigurationBuilder.configurePlugin() {
  useConfigurators(
    ::PiecemealExtensionRegistrarConfigurator,
    ::RuntimeEnvironmentConfigurator,
  )

  useDirectives(PiecemealDirectives)

  useCustomRuntimeClasspathProviders(
    ::RuntimeRuntimeClassPathProvider,
  )
}

class PiecemealExtensionRegistrarConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
  @OptIn(ExperimentalCompilerApi::class)
  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration,
  ) {
    val configuration = PiecemealConfiguration(
      enableJavaSetters = PiecemealDirectives.ENABLE_JAVA_SETTERS in module.directives,
    )
    FirExtensionRegistrarAdapter.registerExtension(PiecemealFirExtensionRegistrar(configuration))
    IrGenerationExtension.registerExtension(PiecemealIrGenerationExtension())
  }
}
