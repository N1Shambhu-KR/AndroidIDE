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

import com.itsaky.androidide.tooling.api.IModuleProject

/**
 * Builds models for module projects (either Android app/library or Java library projects).
 *
 * @author Akash Yadav
 */
class ModuleProjectModelBuilder(androidVariant: String = VARIANT_DEBUG) :
  AbstractModelBuilder<BuildControllderAndIdeaModule, IModuleProject>(androidVariant) {

  override fun build(param: BuildControllderAndIdeaModule): IModuleProject {
    val (controller, module) = param
    val isAndroidProject = getAndroidVersions(module, controller) != null
    return if (isAndroidProject) AndroidProjectModelBuilder(androidVariant).build(
      controller to module) else JavaProjectModelBuilder().build(module)
  }
}