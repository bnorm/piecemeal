plugins {
  kotlin("multiplatform")
  id("dev.bnorm.piecemeal")
  id("dev.drewhamilton.poko")
}

kotlin {
  jvm()
  js {
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
}

tasks.withType<Test> {
  useJUnitPlatform()
}

piecemeal {
  enableJavaSetters = true
}
