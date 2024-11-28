buildscript {
  extra["kotlin_plugin_id"] = "com.bnorm.piecemeal.piecemeal-plugin"
}

plugins {
  kotlin("jvm") version "2.0.21" apply false
  id("org.jetbrains.dokka") version "1.9.20" apply false
  id("com.gradle.plugin-publish") version "1.2.1" apply false
  id("com.github.gmazzo.buildconfig") version "5.3.5" apply false
}

allprojects {
  group = "com.bnorm.piecemeal"
  version = "0.1.0-SNAPSHOT"
}
