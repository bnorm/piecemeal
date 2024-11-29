pluginManagement {
  repositories {
    maven("$rootDir/../../../../../build/testMaven")
    mavenCentral()
    google()
  }
  plugins {
    kotlin("multiplatform") version "2.0.21"
    id("dev.bnorm.piecemeal") version "${extra.properties["piecemealVersion"] ?: "0.1.0-SNAPSHOT"}"
  }
}

dependencyResolutionManagement {
  repositories {
    maven("$rootDir/../../../../../build/testMaven")
    mavenCentral()
    google()
  }
}
