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
package com.itsaky.androidide.tooling.impl.sync

import com.android.builder.model.v2.models.Versions
import com.itsaky.androidide.tooling.impl.Main
import com.itsaky.androidide.tooling.impl.util.StopWatch
import com.itsaky.androidide.utils.ILogger
import com.itsaky.androidide.utils.LogUtils
import org.gradle.api.Action
import org.gradle.tooling.BuildController
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.Model

/**
 * Abstract class for [IModelBuilder] implementations.
 *
 * @property androidVariant The name of the variant for which the Android models will be built.
 * @author Akash Yadav
 */
abstract class AbstractModelBuilder<P, R>(protected val androidVariant: String = "") :
  IModelBuilder<P, R> {

  companion object {

    /**
     * Checks the Android Gradle Plugin version from the given [Versions] model and compares
     * it with [Main.MIN_SUPPORTED_AGP_VERSION]. If the version is less than the [Main.MIN_SUPPORTED_AGP_VERSION],
     * throws an [UnsupportedOperationException].
     *
     * @param versions The [Versions] model.
     */
    @JvmStatic
    protected fun assertMinimumAgp(versions: Versions) {
      if (versions.agp < Main.MIN_SUPPORTED_AGP_VERSION) {
        throw ModelBuilderException(
          "Android Gradle Plugin version "
              + versions.agp
              + " is not supported by AndroidIDE. "
              + "Please update your project to use at least v"
              + Main.MIN_SUPPORTED_AGP_VERSION
              + " of Android Gradle Plugin to build this project.")
      }
    }

    /**
     * Get the [Versions] information about Android projects. This returns `null` if
     * the project is not an Android project.
     *
     * @param model      The model element, usually a project.
     * @param controller The build controller that is used for finding the model.
     * @return The [Versions] model if available, `null` otherwise.
     */
    @JvmStatic
    protected fun getAndroidVersions(model: Model, controller: BuildController): Versions? {
      return controller.findModel(model, Versions::class.java)
    }

    /**
     * Fetches a snapshot of the model of the given type. Throws a [ModelBuilderException] if the
     * model could not be fetched. This also logs the time consumed to fetch the model.
     *
     * @param modelType The model type.
     * @param <T> The model type.
     */
    @JvmStatic
    protected fun <T> BuildController.getModelAndLog(modelType: Class<T>): T {
      return withStopWatch(modelType) {
        return@withStopWatch try {
          getModel(modelType)
        } catch (err: UnknownModelException) {
          throw ModelBuilderException("Failed to fetch model for type '${modelType.name}'." +
              " Model not found or the project does not support this model.")
        }
      }
    }

    /**
     * Fetches a snapshot of the model of the given type. Throws a [ModelBuilderException] if the
     * model could not be fetched. This also logs the time consumed to fetch the model.
     *
     * @param target The target element, usually a project.
     * @param modelType The model type.
     * @param <T> The model type.
     */
    @JvmStatic
    protected fun <T> BuildController.getModelAndLog(target: Model, modelType: Class<T>): T {
      return withStopWatch(modelType) {
        return@withStopWatch try {
          getModel(target, modelType)
        } catch (err: UnknownModelException) {
          throw ModelBuilderException("Failed to fetch model for type '${modelType.name}'." +
              " Model not found or the project does not support this model.")
        }
      }
    }

    /**
     * Fetches a snapshot of the model of the given type using the given parameter. Throws a
     * [ModelBuilderException] if the model could not be fetched. This also logs the time consumed
     * to fetch the model.
     *
     * @param target The target element, usually a project.
     * @param modelType The model type.
     * @param parameterType The parameter type.
     * @param <P> The parameter type.
     * @param parameterInitializer Action to configure the parameter
     * @param <T> The model type.
     */
    @JvmStatic
    protected fun <P, T> BuildController.getModelAndLog(
      target: Model,
      modelType: Class<T>,
      parameterType: Class<P>,
      parameterInitializer: Action<in P>
    ): T {
      return withStopWatch(modelType) {
        return@withStopWatch try {
          getModel(target, modelType, parameterType, parameterInitializer)
        } catch (err: UnknownModelException) {
          throw ModelBuilderException("Failed to fetch model for type '${modelType.name}'." +
              " Model not found or the project does not support this model.")
        } catch (err: UnsupportedVersionException) {
          throw ModelBuilderException("Failed to fetch model for type '${modelType.name}'." +
              " Model not supported by project or Gradle version does not support parameterized models.")
        }
      }
    }

    @JvmStatic
    private fun <T> withStopWatch(modelType: Class<T>, action: () -> T): T {
      val stopwatch = StopWatch("Fetch '${modelType.simpleName}' model")
      return action().also {
        stopwatch.writeTo(System.err)
      }
    }

    /**
     * Logs the given objects to the error stream.
     *
     * @param objects The objects to log.
     */
    @JvmStatic
    protected fun log(vararg objects: Any?) {
      System.err.println(generateMessage(*objects))
    }

    /**
     * Generates the log message for the given objects. This works similar to
     * [ generateMessage(Object...)][com.itsaky.androidide.utils.ILogger.generateMessage] in [ILogger][com.itsaky.androidide.utils.ILogger].
     *
     * @param objects The objects to print in the message.
     * @return The generated message.
     */
    protected fun generateMessage(vararg objects: Any?): String {
      val sb = StringBuilder()
      for (msg in objects) {
        sb.append(if (msg is Throwable) "\n" else ILogger.MSG_SEPARATOR)
        sb.append(if (msg is Throwable) LogUtils.getFullStackTrace(
          msg as Throwable?) else msg)
      }
      return sb.toString()
    }
  }
}