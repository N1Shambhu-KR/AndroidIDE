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

package com.itsaky.androidide.projects

import androidx.annotation.RestrictTo
import com.google.auto.service.AutoService
import com.itsaky.androidide.eventbus.events.EventReceiver
import com.itsaky.androidide.eventbus.events.editor.DocumentSaveEvent
import com.itsaky.androidide.eventbus.events.file.FileCreationEvent
import com.itsaky.androidide.eventbus.events.file.FileDeletionEvent
import com.itsaky.androidide.eventbus.events.file.FileEvent
import com.itsaky.androidide.eventbus.events.file.FileRenameEvent
import com.itsaky.androidide.eventbus.events.project.ProjectInitializedEvent
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.api.AndroidModule
import com.itsaky.androidide.projects.api.ModuleProject
import com.itsaky.androidide.projects.api.Project
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.projects.util.ProjectTransformer
import com.itsaky.androidide.tasks.executeAsync
import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.utils.DocumentUtils
import com.itsaky.androidide.utils.ILogger
import com.itsaky.androidide.utils.flashError
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

/**
 * Internal implementation of [IProjectManager].
 *
 * @author Akash Yadav
 */
@AutoService(IProjectManager::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
class ProjectManagerImpl : IProjectManager, EventReceiver {

  lateinit var projectPath: String
  var projectInitialized: Boolean = false
  var cachedInitResult: InitializeResult? = null

  override var rootProject: Project? = null
  override var app: AndroidModule? = null

  override val projectDirPath: String
    get() = projectPath

  companion object {

    private val log = ILogger.newInstance("ProjectManagerImpl")

    @JvmStatic
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    fun getInstance(): ProjectManagerImpl {
      return IProjectManager.getInstance() as ProjectManagerImpl
    }
  }

  override fun setupProject(project: IProject) {
    this.rootProject = ProjectTransformer().transform(CachingProject(project))
    if (this.rootProject == null) {
      return
    }

    this.app = this.rootProject!!.findFirstAndroidAppModule()
    this.rootProject!!.subProjects.filterIsInstance(ModuleProject::class.java).forEach {
      it.indexSourcesAndClasspaths()
      if (it is AndroidModule) {
        it.readResources()
      }
    }
  }

  override fun findModuleForFile(file: File, checkExistance: Boolean): ModuleProject? {
    if (!checkInit()) {
      return null
    }

    return this.rootProject!!.findModuleForFile(file, checkExistance)
  }

  override fun containsSourceFile(file: Path): Boolean {
    if (!checkInit()) {
      return false
    }

    if (!Files.exists(file)) {
      return false
    }

    for (module in this.rootProject!!.subProjects) {
      if (module !is ModuleProject) {
        continue
      }

      val source = module.compileJavaSourceClasses.findSource(file)
      if (source != null) {
        return true
      }
    }

    return false
  }

  override fun isAndroidResource(file: File): Boolean {
    val module = findModuleForFile(file) ?: return false
    if (module is AndroidModule) {
      return module.getResourceDirectories().find { file.path.startsWith(it.path) } != null
    }
    return true
  }

  override fun destroy() {
    log.info("Destroying project manager")
    this.rootProject = null
    this.app = null
    this.cachedInitResult = null
    this.projectInitialized = false
  }

  @JvmOverloads
  fun generateSources(
    builder: BuildService? = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
  ) {
    if (builder == null) {
      log.warn("Cannot generate sources. BuildService is null.")
      return
    }

    if (!builder.isToolingServerStarted()) {
      flashError(R.string.msg_tooling_server_unavailable)
      return
    }

    if (app == null) {
      log.warn("Cannot run resource and source generation task. No application module found.")
      return
    }

    val debug = app!!.getVariant("debug")
    if (debug == null) {
      log.warn("No debug variant found in application project ${app!!.name}")
      return
    }

    val mainArtifact = debug.mainArtifact
    val genResourcesTask = mainArtifact.resGenTaskName
    val genSourcesTask = mainArtifact.sourceGenTaskName
    val genDataBinding = // If view binding is enabled, generate the view binding classes too
      if (app!!.viewBindingOptions.isEnabled) {
        "dataBindingGenBaseClassesDebug"
      } else {
        ""
      }
    builder
      .executeProjectTasks(
        app!!.path,
        genResourcesTask ?: "",
        genSourcesTask,
        "processDebugResources",
        genDataBinding
      )
      .whenComplete { result, taskErr ->
        if (taskErr != null || !result.isSuccessful) {
          log.warn(
            "Execution for tasks '$genResourcesTask' and '$genSourcesTask' failed.",
            taskErr ?: ""
          )
        } else {
          notifyProjectUpdate()
        }
      }
  }

  fun notifyProjectUpdate() {

    executeAsync {
      rootProject?.apply {
        subProjects.forEach { subproject ->
          if (subproject is ModuleProject) {
            subproject.indexSources()
          }
        }
      }

      val event = ProjectInitializedEvent()
      event.put(Project::class.java, rootProject)
      EventBus.getDefault().post(event)
    }
  }

  private fun isInitialized() = rootProject != null

  private fun checkInit(): Boolean {
    if (isInitialized()) {
      return true
    }

    log.warn("GradleProject is not initialized yet!")
    return false
  }

  private fun generateSourcesIfNecessary(event: FileEvent) {
    val builder = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE) ?: return
    val file = event.file
    if (!isAndroidResource(file)) {
      return
    }

    generateSources(builder)
  }

  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.ASYNC)
  fun onFileSaved(event: DocumentSaveEvent) {
    event.file.apply {
      if (isDirectory()) {
        return@apply
      }

      if (extension != "xml") {
        return@apply
      }

      val module = IProjectManager.getInstance().findModuleForFile(this, false) ?: return@apply
      if (module !is AndroidModule) {
        return@apply
      }

      val isResource =
        module.mainSourceSet?.sourceProvider?.resDirectories?.any {
          this.pathString.contains(it.path)
        }
          ?: false

      if (isResource) {
        module.updateResourceTable()
      }
    }
  }

  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun onFileCreated(event: FileCreationEvent) {
    generateSourcesIfNecessary(event)

    if (DocumentUtils.isJavaFile(event.file.toPath())) {
      IProjectManager.getInstance().findModuleForFile(event.file, false)?.let {
        val sourceRoot = it.findSourceRoot(event.file) ?: return@let

        // add the source node entry
        it.compileJavaSourceClasses.append(event.file.toPath(), sourceRoot)
      }
    }
  }

  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun onFileDeleted(event: FileDeletionEvent) {
    generateSourcesIfNecessary(event)

    // Remove the source node entry
    // Do not check for Java file DocumentUtils.isJavaFile(...) as it checks for file existence as
    // well. As the file is already deleted, it will always return false
    if (event.file.extension == "java") {
      IProjectManager.getInstance().findModuleForFile(event.file, false)
        ?.compileJavaSourceClasses
        ?.findSource(event.file.toPath())
        ?.let { it.parent?.removeChild(it) }
    }
  }

  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun onFileRenamed(event: FileRenameEvent) {
    generateSourcesIfNecessary(event)

    // Do not check for Java file DocumentUtils.isJavaFile(...) as it checks for file existence as
    // well. As the file is already renamed to another filename, it will always return false
    if (event.file.extension == "java") {
      // remove the source node entry
      IProjectManager.getInstance().findModuleForFile(event.file, false)
        ?.compileJavaSourceClasses
        ?.findSource(event.file.toPath())
        ?.let { it.parent?.removeChild(it) }
    }

    if (DocumentUtils.isJavaFile(event.newFile.toPath())) {
      IProjectManager.getInstance().findModuleForFile(event.newFile, false)?.let {
        val sourceRoot = it.findSourceRoot(event.newFile) ?: return@let
        // add the new source node entry
        it.compileJavaSourceClasses.append(event.newFile.toPath(), sourceRoot)
      }
    }
  }
}