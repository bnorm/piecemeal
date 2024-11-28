import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.net.URL

buildscript {
  extra["kotlin_plugin_id"] = "dev.bnorm.piecemeal"
}

plugins {
  kotlin("multiplatform") version "2.0.21" apply false
  kotlin("jvm") version "2.0.21" apply false
  id("org.jetbrains.dokka") version "1.9.20"
  id("com.github.gmazzo.buildconfig") version "5.3.5"
  id("com.vanniktech.maven.publish.base") version "0.30.0"
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3" apply false
}

allprojects {
  group = "dev.bnorm.piecemeal"
  version = "0.1.0-SNAPSHOT"
}

subprojects {
  tasks.withType(Test::class).configureEach {
    testLogging {
      if (System.getenv("CI") == "true") {
        events = setOf(TestLogEvent.STARTED, TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.PASSED)
      }
      exceptionFormat = TestExceptionFormat.FULL
    }
  }
}

tasks.named("dokkaHtmlMultiModule", DokkaMultiModuleTask::class.java).configure {
  moduleName.set("Piecemeal")
}

allprojects {
  tasks.withType<DokkaTaskPartial>().configureEach {
    dokkaSourceSets.configureEach {
      documentedVisibilities.set(
        setOf(
          Visibility.PUBLIC,
          Visibility.PROTECTED
        )
      )
      reportUndocumented.set(false)
      jdkVersion.set(8)

      perPackageOption {
        matchingRegex.set("dev\\.bnorm\\.piecemeal\\.internal\\..*")
        suppress.set(true)
      }
      sourceLink {
        localDirectory.set(rootProject.projectDir)
        remoteUrl.set(URL("https://github.com/bnorm/piecemeal/tree/main/"))
        remoteLineSuffix.set("#L")
      }
    }
  }

  // Don't attempt to sign anything if we don't have an in-memory key. Otherwise, the 'build' task
  // triggers 'signJsPublication' even when we aren't publishing (and so don't have signing keys).
  tasks.withType<Sign>().configureEach {
    enabled = project.findProperty("signingInMemoryKey") != null
  }

  val javaVersion = JavaVersion.VERSION_1_8

  plugins.withId("org.jetbrains.kotlin.multiplatform") {
    val kotlin = extensions.getByName("kotlin") as KotlinMultiplatformExtension
    kotlin.targets.withType(KotlinJvmTarget::class.java) {
      compilerOptions {
        freeCompilerArgs.add("-Xjdk-release=$javaVersion")
      }
    }
  }

  tasks.withType(JavaCompile::class.java).configureEach {
    sourceCompatibility = javaVersion.toString()
    targetCompatibility = javaVersion.toString()
  }

  tasks.withType(KotlinJvmCompile::class.java).configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
    }
  }

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<PublishingExtension> {
      repositories {
        maven {
          name = "testMaven"
          url = rootProject.layout.buildDirectory.dir("testMaven").get().asFile.toURI()
        }
      }
    }
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
      signAllPublications()
      pom {
        description.set("TODO")
        name.set(project.name)
        url.set("https://github.com/bnorm/piecemeal/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        developers {
          developer {
            id.set("bnorm")
            name.set("Brian Norman")
          }
        }
        scm {
          url.set("https://github.com/bnorm/piecemeal/")
          connection.set("scm:git:https://github.com/bnorm/piecemeal.git")
          developerConnection.set("scm:git:ssh://git@github.com/bnorm/piecemeal.git")
        }
      }
    }
  }
}
