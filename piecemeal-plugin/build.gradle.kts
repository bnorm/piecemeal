import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("com.github.gmazzo.buildconfig")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")

  kapt("com.google.auto.service:auto-service:1.0.1")
  compileOnly("com.google.auto.service:auto-service-annotations:1.0.1")
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
  }

  packageName(group.toString())
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.extra["kotlin_plugin_id"]}\"")
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Empty())
  )
}
