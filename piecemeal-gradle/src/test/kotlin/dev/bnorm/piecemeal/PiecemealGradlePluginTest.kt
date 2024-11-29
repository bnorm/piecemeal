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
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertTrue

class PiecemealGradlePluginTest {
  @Test
  fun testMultiplatform() {
    val projectDir = Path("src/test/projects/multiplatform")

    val taskName = ":check"
    val result = runBuild(projectDir, taskName).build()
    val task = result.task(taskName)!!
    assertTrue(task.outcome in SUCCESS_OUTCOMES)
  }

  @OptIn(ExperimentalPathApi::class)
  private fun runBuild(
    projectDir: Path,
    vararg taskNames: String,
  ): GradleRunner {
    projectDir.resolve("kotlin-js-store/yarn.lock").deleteIfExists()
    val arguments = arrayOf("--info", "--stacktrace", "--continue")
    return GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withArguments(*arguments, *taskNames, versionProperty)
  }

  companion object {
    val SUCCESS_OUTCOMES = arrayOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
    val versionProperty = "-PpiecemealVersion=${System.getProperty("piecemealVersion")}"
  }
}
