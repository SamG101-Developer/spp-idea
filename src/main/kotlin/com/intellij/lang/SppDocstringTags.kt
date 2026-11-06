package com.intellij.lang

// The docstring tags S++ understands, and where each one may be
// used. The annotator validates against these, the documentation
// provider renders them, and the enter handler generates them, so
// a new tag only needs adding here (and rendering in the provider).
object SppDocstringTags {
  // A parameter (function) or attribute (class): "@let name: ...".
  const val LET = "let"

  // A generic type parameter or sup-block type: "@type T: ...".
  const val TYPE = "type"

  // A compile-time generic or sup-block constant: "@cmp n: ...".
  const val CMP = "cmp"

  // The return value of a function: "@ret: ...".
  const val RET = "ret"

  // Every tag that means something somewhere.
  val ALL: Set<String> = linkedSetOf(LET, TYPE, CMP, RET)

  // The tags valid in each kind of docstring.
  val FUNCTION: Set<String> = linkedSetOf(LET, TYPE, CMP, RET)
  val CLASS: Set<String> = linkedSetOf(LET, TYPE, CMP)
  val SUP: Set<String> = linkedSetOf(TYPE, CMP)
}
