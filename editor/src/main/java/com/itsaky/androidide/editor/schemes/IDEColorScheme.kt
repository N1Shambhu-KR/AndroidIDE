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

package com.itsaky.androidide.editor.schemes

import com.itsaky.androidide.utils.ILogger
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.util.TreeSet

class IDEColorScheme : EditorColorScheme() {

  internal val colorIds = mutableMapOf<Int, Int>()
  internal val editorScheme = mutableMapOf<Int, Int>()
  internal val languages = mutableMapOf<String, LanguageScheme>()

  private var colorId = END_COLOR_ID

  var isDarkScheme: Boolean = false
    internal set

  var definitions: Map<String, Int> = emptyMap()
    internal set
  
  fun getLanguageScheme(type: String): LanguageScheme? {
    return this.languages[type]
  }

  internal fun putColor(color: Int): Int {
    this.colorIds[++colorId] = color
    return colorId
  }

  @Suppress("UNNECESSARY_SAFE_CALL")
  override fun getColor(type: Int): Int {
    // getColor is called in superclass constructor
    // in this case, the below properties will be null
    val result = this.editorScheme?.get(type) ?: this.colorIds?.get(type) ?: super.getColor(type)
    println("getColor($type) = $result")
    return result
  }
}

/**
 * Color scheme for a language.
 *
 * @property files The file types for this language color scheme.
 * @property styles The highlight styles.
 * @author Akash Yadav
 */
class LanguageScheme() {
  internal val files = mutableListOf<String>()
  internal val styles = mutableMapOf<String, StyleDef>()
  internal val localScopes = TreeSet<String>()
  internal val localDefs = TreeSet<String>()
  internal val localDefVals = TreeSet<String>()
  internal val localRefs = TreeSet<String>()
  
  fun getFileTypes() : List<String> = files
  fun getStyles(): Map<String, StyleDef> = styles
  
  fun isLocalScope(capture: String) : Boolean {
    return localScopes.contains(capture)
  }
  
  fun isLocalDef(capture: String) : Boolean {
    return localDefs.contains(capture)
  }
  
  fun isLocalDefVal(capture: String) : Boolean {
    return localDefVals.contains(capture)
  }
  fun isLocalRef(capture: String) : Boolean {
    return localRefs.contains(capture)
  }
  
}

/**
 * A color scheme style definition.
 *
 * @property fg The foreground color.
 * @property bg The background color.
 * @property bold Whether the highlighted region should have bold text.
 * @property italic Whether the highlighted region should have italic text.
 * @property strikeThrough Whether the highlighted region should have strikethrough text.
 * @property completion Whether code completions can be performed in the highlighted region.
 */
data class StyleDef(
  var fg: Int,
  var bg: Int = 0,
  var bold: Boolean = false,
  var italic: Boolean = false,
  var strikeThrough: Boolean = false,
  var completion: Boolean = true
) {
  
  private val log = ILogger.newInstance("StyleDef")
  fun makeStyle() : Long {
    val style = TextStyle.makeStyle(this.fg, this.bg, this.bold, this.italic, this.strikeThrough, !this.completion)
    log.debug("fg: ${TextStyle.getForegroundColorId(style)}")
    return style
  }
}
