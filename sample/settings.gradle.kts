pluginManagement {
  plugins {
    kotlin("multiplatform") version "2.0.21"
    id("dev.bnorm.piecemeal") version "0.1.0-SNAPSHOT"
    id("dev.drewhamilton.poko") version "0.18.2"
  }
  repositories {
    mavenCentral()
    google()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
  }
}

rootProject.name = "piecemeal-sample"

includeBuild("..")
