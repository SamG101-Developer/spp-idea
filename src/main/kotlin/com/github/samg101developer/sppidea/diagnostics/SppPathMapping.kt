package com.github.samg101developer.sppidea.diagnostics

import com.intellij.execution.wsl.WslPath
import java.nio.file.Path

// Translates between the paths the IDE knows files by and the
// paths the compiler sees. They differ when the IDE runs on
// Windows and the project lives in WSL: the compiler then runs
// inside the distribution and reports Linux paths, such as
// "/home/me/project/src/main.spp", for files the IDE knows as
// "\\wsl.localhost\Debian\home\me\project\src\main.spp".
// Everywhere else the two are the same, and nothing changes.
class SppPathMapping private constructor(private val uncPrefix: String?) {

  // The path the compiler knows [idePath] by.
  fun toCompiler(idePath: Path): String =
    uncPrefix?.let { WslPath.parseWindowsUncPath(idePath.toString())?.linuxPath } ?: idePath.toString()

  // The path the IDE knows [compilerPath] by, in the same form
  // as VirtualFile.toNioPath(), which the caches are keyed by.
  fun toIde(compilerPath: String): String =
    if (uncPrefix == null || !compilerPath.startsWith("/")) compilerPath
    else runCatching { Path.of(uncPrefix + compilerPath).toString() }.getOrDefault(compilerPath)

  companion object {
    // The mapping for a project at [root]: the distribution's
    // UNC prefix (e.g. "\\wsl.localhost\Debian") is whatever
    // precedes the root's own Linux path in the root's UNC path.
    fun forRoot(root: Path): SppPathMapping {
      val wsl = WslPath.parseWindowsUncPath(root.toString()) ?: return SppPathMapping(null)
      val linuxRoot = Path.of(wsl.linuxPath).toString()
      val rootString = root.toString()
      return SppPathMapping(rootString.removeSuffix(linuxRoot).takeIf { rootString.endsWith(linuxRoot) })
    }
  }
}
