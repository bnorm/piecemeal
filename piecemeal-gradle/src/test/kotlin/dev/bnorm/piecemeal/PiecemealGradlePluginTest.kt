/*
 * Copyright (C) 2024 Brian Norman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.bnorm.piecemeal

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals

class PiecemealGradlePluginTest {
  @Test
  fun testEnableJavaSetters() = forProject("enableJavaSetters") { projectDir ->
    val taskName = ":check"
    val result = runBuild(projectDir, taskName).build()
    val task = result.task(taskName)!!
    assertEquals(task.outcome, TaskOutcome.SUCCESS)
  }

  @Test
  fun testMultiplatform() = forProject("multiplatform") { projectDir ->
    val taskName = ":check"
    val result = runBuild(projectDir, taskName).build()
    val task = result.task(taskName)!!
    assertEquals(task.outcome, TaskOutcome.SUCCESS)
  }

  @OptIn(ExperimentalPathApi::class)
  private fun forProject(name: String, block: (projectDir: Path) -> Unit) {
    val projectDir = Path("src/test/projects/", name)

    fun clean() {
      projectDir.resolve(".gradle").deleteRecursively()
      projectDir.resolve(".kotlin").deleteRecursively()
      projectDir.resolve("build").deleteRecursively()
      projectDir.resolve("kotlin-js-store").deleteRecursively()
    }

    clean() // Clean before just to be sure.
    block(projectDir)
    clean() // Clean after if successful.
  }

  private fun runBuild(
    projectDir: Path,
    vararg taskNames: String,
  ): GradleRunner {
    val arguments = arrayOf("--info", "--stacktrace", "--continue")
    return GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .forwardOutput()
      .withArguments(*arguments, *taskNames, versionProperty, kotlinProperty)
  }

  companion object {
    private val versionProperty = "-PpiecemealVersion=${System.getProperty("piecemealVersion")}"
    private val kotlinProperty = "-PkotlinVersion=${System.getProperty("kotlinVersion")}"
  }
}
