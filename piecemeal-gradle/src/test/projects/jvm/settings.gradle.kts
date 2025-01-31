pluginManagement {
  repositories {
    maven("$rootDir/../../../../../build/testMaven")
    mavenCentral()
    google()
  }
  plugins {
    kotlin("jvm") version "${extra.properties["kotlinVersion"]}"
    id("dev.bnorm.piecemeal") version "${extra.properties["piecemealVersion"]}"
  }
}

dependencyResolutionManagement {
  repositories {
    maven("$rootDir/../../../../../build/testMaven")
    mavenCentral()
    google()
  }
}
