dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

rootProject.name = "piecemeal-root"

include(":piecemeal")
include(":piecemeal-gradle")
include(":piecemeal-plugin")
include(":piecemeal-test")
