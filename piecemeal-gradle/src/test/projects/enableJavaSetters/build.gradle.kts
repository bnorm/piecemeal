import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  kotlin("multiplatform")
  id("dev.bnorm.piecemeal")
}

kotlin {
  jvm()
  js {
    nodejs()
  }

  linuxX64()
  macosArm64()

  sourceSets {
    commonTest {
      dependencies {
        implementation(kotlin("test"))
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  testLogging {
    events = setOf(TestLogEvent.STARTED, TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.PASSED)
    exceptionFormat = TestExceptionFormat.FULL
  }
}

piecemeal {
  enableJavaSetters = true
}
