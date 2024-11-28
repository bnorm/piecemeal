@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

kotlin {
  explicitApi()

  androidNativeArm32()
  androidNativeArm64()
  androidNativeX64()
  androidNativeX86()

  iosArm64()
  iosSimulatorArm64()
  iosX64()

  js().nodejs()

  jvm()

  linuxArm64()
  linuxX64()

  macosArm64()
  macosX64()

  mingwX64()

  tvosArm64()
  tvosSimulatorArm64()
  tvosX64()

  wasmJs().nodejs()
  wasmWasi().nodejs()

  watchosArm32()
  watchosArm64()
  watchosDeviceArm64()
  watchosSimulatorArm64()
  watchosX64()

  applyDefaultHierarchyTemplate()
}

//configure<MavenPublishBaseExtension> {
//  configure(
//    KotlinMultiplatform(javadocJar = JavadocJar.Empty())
//  )
//}
