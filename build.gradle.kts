buildscript {
  extra["kotlin_plugin_id"] = "dev.bnorm.piecemeal.piecemeal-plugin"
}

plugins {
  kotlin("jvm") version "2.0.21" apply false
  id("org.jetbrains.dokka") version "1.9.20" apply false
  id("com.vanniktech.maven.publish.base") version "0.30.0" apply false
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3" apply false
  id("com.gradle.plugin-publish") version "1.2.1" apply false
  id("com.github.gmazzo.buildconfig") version "5.3.5" apply false
}

allprojects {
  group = "dev.bnorm.piecemeal"
  version = "0.1.0-SNAPSHOT"
}
