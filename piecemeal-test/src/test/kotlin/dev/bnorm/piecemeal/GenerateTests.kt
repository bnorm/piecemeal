package dev.bnorm.piecemeal

import org.jetbrains.kotlin.generators.generateTestGroupSuiteWithJUnit5

fun main() {
  generateTestGroupSuiteWithJUnit5 {
    testGroup(testDataRoot = "src/test/data", testsRoot = "src/test/java") {
      testClass<AbstractBoxTest> {
        model("box")
      }
      testClass<AbstractDiagnosticTest> {
        model("diagnostic")
      }
    }
  }
}
