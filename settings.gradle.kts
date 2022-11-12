rootProject.name = "piecemeal-root"

include(":piecemeal")
include(":piecemeal-gradle")
include(":piecemeal-plugin")

includeBuild("../kotlin-compile-testing") {
  dependencySubstitution {
    substitute(module("com.github.tschuchortdev:kotlin-compile-testing")).using(project(":core"))
  }
}
