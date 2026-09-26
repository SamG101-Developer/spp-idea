package com.intellij.lang

// Specify which tokens are used for line and block comments
// in S++ code. In this case, S++ uses `#` for line comments
// and does not have block comments, but highlighting multiple
// lines and doing ctrl+/ comments/uncomments all the lines.
class SppCommenter : Commenter {
  override fun getLineCommentPrefix(): String = "# "
  override fun getBlockCommentPrefix(): String? = null
  override fun getBlockCommentSuffix(): String? = null
  override fun getCommentedBlockCommentPrefix(): String? = null
  override fun getCommentedBlockCommentSuffix(): String? = null
}
