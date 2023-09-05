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

package com.itsaky.androidide.utils

import android.content.Context
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.createGraph
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.FragmentNavigatorDestinationBuilder
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.get
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.actions.FillMenuParams
import com.itsaky.androidide.actions.SidebarActionItem
import com.itsaky.androidide.actions.internal.DefaultActionsRegistry
import com.itsaky.androidide.actions.sidebar.CloseProjectSidebarAction
import com.itsaky.androidide.actions.sidebar.FileTreeSidebarAction
import com.itsaky.androidide.actions.sidebar.TerminalSidebarAction
import com.itsaky.androidide.fragments.sidebar.EditorSidebarFragment


/**
 * Sets up the actions that are shown in the
 * [EditorActivityKt][com.itsaky.androidide.activities.editor.EditorActivityKt]'s drawer's sidebar.
 *
 * @author Akash Yadav
 */
internal object EditorSidebarActions {

  @JvmStatic
  fun registerActions(context: Context) {
    val registry = ActionsRegistry.getInstance()
    registry.registerAction(FileTreeSidebarAction(context))
    registry.registerAction(TerminalSidebarAction(context))
    registry.registerAction(CloseProjectSidebarAction(context))
  }

  @JvmStatic
  fun setup(sidebarFragment: EditorSidebarFragment) {
    val binding = sidebarFragment.getBinding() ?: return
    val controller = binding.fragmentContainer.getFragment<NavHostFragment>().navController
    val context = sidebarFragment.requireContext()
    val rail = binding.navigation

    val registry = ActionsRegistry.getInstance()
    val actions = registry.getActions(ActionItem.Location.EDITOR_SIDEBAR)
    if (actions.isEmpty()) {
      return
    }

    rail.background = (rail.background as MaterialShapeDrawable).apply {
      shapeAppearanceModel = shapeAppearanceModel.roundedOnRight()
    }

    rail.menu.clear()

    val data = ActionData()
    data.put(Context::class.java, context) // needed for inflating the menu

    val params = FillMenuParams(data, ActionItem.Location.EDITOR_SIDEBAR,
      rail.menu) { actionsRegistry, action, item, actionsData ->

      action as SidebarActionItem

      if (action.fragmentClass == null) {
        // this action does not show any fragment
        // execute the action instead
        (actionsRegistry as DefaultActionsRegistry).executeAction(action, actionsData)
        return@FillMenuParams true
      }

      return@FillMenuParams try {
        controller.navigate(action.id, navOptions {
          launchSingleTop = true
          restoreState = true

          popUpTo(controller.graph.findStartDestination().id) {
            inclusive = false
            saveState = true
          }
        })

        // Return true only if the destination we've navigated to matches the MenuItem
        val result = controller.currentDestination?.matchDestination(action.id) == true
        if (result) {
          item.isChecked = true
        }
        result
      } catch (e: IllegalArgumentException) {
        false
      }
    }

    registry.fillMenu(params)

    controller.graph = controller.createGraph(startDestination = FileTreeSidebarAction.ID) {
      actions.forEach { (actionId, action) ->
        if (action !is SidebarActionItem) {
          throw IllegalStateException(
            "Actions registered at location ${ActionItem.Location.EDITOR_SIDEBAR}" +
                " must implement ${SidebarActionItem::class.java.simpleName}")
        }

        val fragment = action.fragmentClass ?: return@forEach

        val builder = FragmentNavigatorDestinationBuilder(
          provider[FragmentNavigator::class],
          actionId,
          fragment
        )

        builder.apply {
          action.apply { buildNavigation() }
        }

        destination(builder)
      }
    }

    rail.setupWithNavController(controller)

    // make sure the 'Home' item is checked by default
    rail.menu.findItem(FileTreeSidebarAction.ID.hashCode())?.isChecked = true
  }

  /**
   * Determines whether the given `route` matches the NavDestination. This handles
   * both the default case (the destination's route matches the given route) and the nested case where
   * the given route is a parent/grandparent/etc of the destination.
   */
  @JvmStatic
  internal fun NavDestination.matchDestination(route: String): Boolean =
    hierarchy.any { it.route == route }

  @JvmStatic
  internal fun ShapeAppearanceModel.roundedOnRight(cornerSize: Float = 28f): ShapeAppearanceModel {
    return toBuilder().run {
      setTopRightCorner(CornerFamily.ROUNDED, cornerSize)
      setBottomRightCorner(CornerFamily.ROUNDED, cornerSize)
      build()
    }
  }
}