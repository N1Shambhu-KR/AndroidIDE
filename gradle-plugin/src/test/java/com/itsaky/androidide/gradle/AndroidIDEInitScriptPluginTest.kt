/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.gradle

import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.buildinfo.BuildInfo
import com.itsaky.androidide.utils.FileProvider
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.junit.jupiter.api.Test
import kotlin.io.path.pathString

/**
 * @author Akash Yadav
 */
class AndroidIDEInitScriptPluginTest {

  @Test
  fun `test plugins are applied and log sender dependency is added properly`() {
    val result = buildProject()
    assertBasics(result)
  }

  @Test
  fun `test behavior on minimum supported version`() {
    val result = buildProject(agpVersion = BuildInfo.AGP_VERSION_MININUM)
    assertBasics(result)
  }

  private fun buildProject(
    agpVersion: String = BuildInfo.AGP_VERSION_LATEST,
    vararg plugins: String
  ): BuildResult {
    val projectRoot = openProject(agpVersion, *plugins)
    val initScript = FileProvider.testHomeDir().resolve(".androidide/init/androidide.init.gradle")
    val mavenLocal = FileProvider.projectRoot().resolve("gradle-plugin/build/maven-local")

    val runner = GradleRunner.create()
      .withProjectDir(projectRoot.toFile())
      .withArguments(
        ":app:tasks", // run any task, as long as it applies the plugins
        "--init-script", initScript.pathString,
        "-Pandroidide.plugins.internal.isTestEnv=true", // plugins should be published to maven local first
        "-Pandroidide.plugins.internal.mavenLocalRepository=${mavenLocal.pathString}",
        "--stacktrace"
      )

    writeInitScript(initScript.toFile(),
      PluginUnderTestMetadataReading.readImplementationClasspath())

    return runner.build()
  }

  private fun assertBasics(result: BuildResult) {
    // These plugins must be applied to the
    for ((project, plugins) in mapOf(
      ":app" to arrayOf(AndroidIDEGradlePlugin::class, LogSenderPlugin::class))) {
      for (plugin in plugins) {
        assertThat(result.output).contains(
          "Applying ${plugin.simpleName} to project '${project}'"
        )
      }
    }

    // LogSender should be applied to these
    for ((project, variants) in mapOf(":app" to arrayOf("demoDebug", "fullDebug"))) {
      for (variant in variants) {
        assertThat(result.output).contains(
          "Adding LogSender dependency (version '${BuildInfo.VERSION_NAME_SIMPLE}') to variant '${variant}' of project '${project}'"
        )
      }
    }

    // LogSender should not be applied to these
    for ((project, variants) in mapOf(":app" to arrayOf("demoRelease", "fullRelease"))) {
      for (variant in variants) {
        assertThat(result.output).doesNotContain(
          "Adding LogSender dependency to variant '${variant}' of project '${project}'"
        )
      }
    }
  }
}