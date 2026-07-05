package com.intellij.lang

/**
 * The `SppCommenter` class provides methods on how to define a comment, or what syntactically looks like a comment. For
 * S++, there are only line comments, with keywboard shortcuts to mass line-comment. Block comment remain undefined.
 */
class SppCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "# "
    override fun getBlockCommentPrefix(): String? = null
    override fun getBlockCommentSuffix(): String? = null
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}