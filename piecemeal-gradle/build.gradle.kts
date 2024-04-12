plugins {
  id("java-gradle-plugin")
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  `maven-publish`
}

dependencies {
  implementation(kotlin("gradle-plugin-api"))
}

buildConfig {
  val project = project(":piecemeal-plugin")
  packageName(project.group.toString())
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.extra["kotlin_plugin_id"]}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${project.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${project.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${project.version}\"")

  val supportProject = project(":piecemeal")
  buildConfigField("String", "SUPPORT_LIBRARY_GROUP", "\"${supportProject.group}\"")
  buildConfigField("String", "SUPPORT_LIBRARY_NAME", "\"${supportProject.name}\"")
  buildConfigField("String", "SUPPORT_LIBRARY_VERSION", "\"${supportProject.version}\"")
}

gradlePlugin {
  plugins {
    create("piecemeal") {
      id = rootProject.extra["kotlin_plugin_id"] as String
      displayName = "Piecemeal"
      description = "Piecemeal"
      implementationClass = "com.bnorm.piecemeal.PiecemealGradlePlugin"
    }
  }
}

publishing {
  repositories {
    mavenLocal()
  }
}
