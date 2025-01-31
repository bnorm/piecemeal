@file:OptIn(
  ExperimentalBuildToolsApi::class,
  ExperimentalKotlinGradlePluginApi::class,
)

import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  id("java-gradle-plugin")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(kotlin("gradle-plugin-api"))

  testImplementation(kotlin("test-junit5"))
}

tasks.withType<Test> {
  useJUnitPlatform()

  systemProperty("piecemealVersion", project.version)
  systemProperty("kotlinVersion", kotlin.compilerVersion.get())
  dependsOn(":piecemeal:publishAllPublicationsToTestMavenRepository")
  dependsOn(":piecemeal-plugin:publishAllPublicationsToTestMavenRepository")
  dependsOn(":piecemeal-gradle:publishAllPublicationsToTestMavenRepository")
}

buildConfig {
  packageName(project.group.toString())

  val pluginProject = project(":piecemeal-plugin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.extra["kotlin_plugin_id"]}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${pluginProject.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${pluginProject.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${pluginProject.version}\"")

  val libraryProject = project(":piecemeal")
  val libraryCoordinates = "${libraryProject.group}:${libraryProject.name}:${libraryProject.version}"
  buildConfigField("String", "SUPPORT_LIBRARY_COORDINATES", "\"$libraryCoordinates\"")
}

gradlePlugin {
  plugins {
    create("piecemeal") {
      id = rootProject.extra["kotlin_plugin_id"] as String
      displayName = "Piecemeal"
      description = "Piecemeal"
      implementationClass = "dev.bnorm.piecemeal.PiecemealGradlePlugin"
    }
  }
}

mavenPublishing {
  configure(
    GradlePlugin(javadocJar = JavadocJar.Empty())
  )
}
