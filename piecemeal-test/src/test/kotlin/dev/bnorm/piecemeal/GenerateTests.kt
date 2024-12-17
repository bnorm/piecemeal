package dev.bnorm.piecemeal

import org.jetbrains.kotlin.generators.generateTestGroupSuiteWithJUnit5

fun main() {
  generateTestGroupSuiteWithJUnit5 {
    testGroup(testDataRoot = "piecemeal-test/src/test/data", testsRoot = "piecemeal-test/src/test/java") {
      testClass<AbstractBoxTest> {
        model("box")
      }
      testClass<AbstractDiagnosticTest> {
        model("diagnostic")
      }
    }
  }
}
