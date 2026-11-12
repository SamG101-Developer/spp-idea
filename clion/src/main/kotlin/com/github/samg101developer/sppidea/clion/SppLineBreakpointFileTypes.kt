package com.github.samg101developer.sppidea.clion

import com.intellij.lang.SppFileType
import com.intellij.openapi.fileTypes.FileType
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrLineBreakpointFileTypesProvider

// Lets CLion's line breakpoints be put in S++ files. The native
// debugger matches them to the program's line tables by file and
// line, as for C++.
class SppLineBreakpointFileTypes : CidrLineBreakpointFileTypesProvider {
  override fun getFileTypes(): Set<FileType> = setOf(SppFileType.INSTANCE)
}
