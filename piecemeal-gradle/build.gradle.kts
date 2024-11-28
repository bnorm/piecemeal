import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  id("java-gradle-plugin")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(kotlin("gradle-plugin-api"))
}

buildConfig {
  packageName(project.group.toString())

  val pluginProject = project(":piecemeal-plugin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.extra["kotlin_plugin_id"]}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${pluginProject.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${pluginProject.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${pluginProject.version}\"")

  val libraryProject = project(":piecemeal")
  buildConfigField("String", "SUPPORT_LIBRARY_GROUP", "\"${libraryProject.group}\"")
  buildConfigField("String", "SUPPORT_LIBRARY_NAME", "\"${libraryProject.name}\"")
  buildConfigField("String", "SUPPORT_LIBRARY_VERSION", "\"${libraryProject.version}\"")
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
