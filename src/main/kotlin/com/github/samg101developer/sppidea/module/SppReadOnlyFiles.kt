package com.github.samg101developer.sppidea.module

import com.intellij.lang.SppFileType
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.io.path.isRegularFile

// Locks the S++ files a project only reads, so they are not edited
// by accident: the dependencies under "vcs", and anything else that
// is not in the project's own "src", "tst" or "ffi". Typing into one
// shows the platform's "this file is protected" banner, which
// unlocks it on request, so an edit is still possible on purpose.
// Files outside any S++ project (no "spp.toml" above them) are left
// alone.
class SppReadOnlyFiles : NonProjectFileWritingAccessExtension {
  override fun isNotWritable(file: VirtualFile): Boolean {
    if (file.isDirectory || file.fileType != SppFileType.INSTANCE) return false
    val path = runCatching { file.toNioPath() }.getOrNull() ?: return false
    return isLocked(path) { it.resolve(MANIFEST).isRegularFile() }
  }

  companion object {
    private const val MANIFEST = "spp.toml"
    private const val VCS_FOLDER = "vcs"
    private val EDITABLE_FOLDERS = setOf("src", "tst", "ffi")

    // Whether "file" is locked. A project is a folder holding an
    // "spp.toml". The file is editable when the nearest project
    // above it holds it in one of its own editable folders, and no
    // project further up holds it under "vcs" (a dependency that
    // has a manifest of its own is a project too, and its "src"
    // must still be locked).
    internal fun isLocked(file: Path, isProject: (Path) -> Boolean): Boolean {
      val roots = generateSequence(file.parent) { it.parent }.filter(isProject).toList()
      val nearest = roots.firstOrNull() ?: return false
      if (roots.any { firstFolder(it, file) == VCS_FOLDER }) return true
      return firstFolder(nearest, file) !in EDITABLE_FOLDERS
    }

    // The first folder "file" is in under "root", or null for a
    // file directly in "root".
    private fun firstFolder(root: Path, file: Path): String? {
      val relative = root.relativize(file)
      return if (relative.nameCount > 1) relative.getName(0).toString() else null
    }
  }
}
