plugins {
  kotlin("multiplatform") version "2.0.0-RC1"
  id("dev.bnorm.piecemeal.piecemeal-plugin") version "0.1.0-SNAPSHOT"
}

kotlin {
  jvm()
  js(IR) {
    browser()
    nodejs()
  }

  val osName = System.getProperty("os.name")
  val osArch = System.getProperty("os.arch")
  when {
    "Windows" in osName -> mingwX64("native")
    "Mac OS" in osName -> when (osArch) {
      "aarch64" -> macosArm64("native")
      else -> macosX64("native")
    }
    else -> linuxX64("native")
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        // Required for Gradle dependency substitution.
        implementation("dev.bnorm.piecemeal:piecemeal:0.1.0-SNAPSHOT")
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit5"))
      }
    }
    val jsTest by getting {
      dependencies {
        implementation(kotlin("test-js"))
      }
    }
    val nativeMain by getting {
      dependsOn(commonMain)
    }
    val nativeTest by getting {
      dependsOn(commonTest)
    }
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}
