plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("com.github.gmazzo.buildconfig")
  `maven-publish`
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")

  kapt("com.google.auto.service:auto-service:1.0.1")
  compileOnly("com.google.auto.service:auto-service-annotations:1.0.1")

  testImplementation(kotlin("test-junit"))
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
  testImplementation("dev.zacsweers.kctfork:core:0.5.0-alpha06")

  testImplementation(project(":piecemeal"))
}

buildConfig {
  packageName(group.toString())
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.extra["kotlin_plugin_id"]}\"")
}

tasks {
  compileTestKotlin {
    compilerOptions {
      freeCompilerArgs.add("-Xskip-prerelease-check")
    }
  }
}

publishing {
  publications {
    create<MavenPublication>("default") {
      from(components["java"])
    }
  }

  repositories {
    mavenLocal()
  }
}
