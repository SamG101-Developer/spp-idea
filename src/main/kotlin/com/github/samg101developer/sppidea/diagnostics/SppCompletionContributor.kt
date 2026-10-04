package com.github.samg101developer.sppidea.diagnostics

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.ui.IconManager
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.absolute

// The completion contributor fills the completion menu for
// ".", "::", and other areas where suggestions can be made.
class SppCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    // Resolve the file, its path, and the document to read
    // from.
    val file = parameters.originalFile
    val path = file.virtualFile?.let { runCatching { it.toNioPath().absolute() }.getOrNull() } ?: return
    val document = file.viewProvider.document ?: return

    // The text and the caret's offset in it, which is where
    // the completion is being asked for.
    val text = document.charsSequence
    val offset = parameters.offset

    // From the diagnostic service, get the symbols known for
    // this file. If none are known, it has not been analysed
    // yet, so fire up a background analysis and return: the
    // completion will be filled in once that is done.
    val service = SppCompilerDiagnostics.getInstance(file.project)
    val symbols = service.cachedSymbolsFor(path)
    if (symbols.isEmpty() && !service.isAnalysed(path)) {
      file.virtualFile?.let { service.warmUp(it) }
      return
    }

    // The name typed so far decides what is shown, read from
    // the text rather than from the platform's own idea of a
    // prefix, which stops at characters this language allows
    // in a name.
    val access = SppSymbolLookup.accessBefore(text, offset)
    val filtered = result.withPrefixMatcher(
      access?.prefix ?: SppSymbolLookup.prefixBefore(text, offset)
    )

    // After a "." or a "::", what is reachable through it,
    // and nothing else: a member access takes members.
    if (access != null) {
      val receiver = SppSymbolLookup.receiverOf(symbols, access, document) ?: return
      service.cachedMembersOf(receiver.type).forEach { lookupsFor(it).forEach(filtered::addElement) }
      return
    }

    // Handle object initialisation and function calls by
    // offering the names of the arguments they take.
    namedArgumentsAt(service, symbols, path, text, offset, document)
      .forEach { filtered.addElement(namedArgumentFor(it)) }

    // Todo: What about for generic parameters vs arguments?

    SppSymbolLookup.namesInScopeAt(service.cachedScopesFor(path), offset, document)
      .forEach { lookupsFor(it).forEach(filtered::addElement) }
  }

  // The arguments the call or object initialiser around the
  // caret takes, or nothing when it is inside neither.
  private fun namedArgumentsAt(
    service: SppCompilerDiagnostics,
    symbols: List<SppSymbol>,
    path: Path,
    text: CharSequence,
    offset: Int,
    document: Document
  ): List<SppMember> {
    // Get the left side parenthesis that encloses the caret,
    // which is what the arguments are for. If there is none,
    // then there are no arguments to offer.
    val bracket = SppSymbolLookup.enclosingOpenBracket(text, offset) ?: return emptyList()

    // A type before the bracket means a value of it is being
    // built, and the names are its attributes; anything else
    // is a call, and the names are the parameters the overload
    // it resolved to takes.
    val calleeEnd = SppSymbolLookup.calleeEnd(text, bracket)
    val callee = symbols.filter { it.kind == "type" }
      .firstOrNull { SppSymbolLookup.rangeOf(it.use, document)?.endOffset == calleeEnd }

    // If this is an object initialisation, retrieve the list
    // of fields from the type's symbol.
    if (callee != null) {
      return service.cachedMembersOf(callee.type).filter { it.kind == "attribute" }
    }

    // Otherwise, this is for a function call, so retrieve the
    // signature that was resolved to, and return its parameters.
    return SppSymbolLookup.signatureAt(service.cachedSignaturesFor(path), offset, document)
      ?.params
      .orEmpty()
  }

  // An argument's name, written the way it is given: "name=".
  private fun namedArgumentFor(member: SppMember) =
    LookupElementBuilder.create(member.name + "=")
      .withIcon(withVisibility(AllIcons.Nodes.Parameter, member.visibility))
      .withTypeText(SppSymbolLookup.shortTypeName(member.type), true)
      .withCaseSensitivity(true)

  // Off a field, or a method. If we are offering a method, it
  // is offered once per overload, each with its own signature
  // and return type. If we are offering a field, it is offered
  // once with its type.
  private fun lookupsFor(member: SppMember): List<LookupElementBuilder> {
    // A member with no signatures isn't a method, so it is
    // offered once with its type shown. This is like a field
    // or nested type.
    if (member.signatures.isEmpty()) {
      return listOf(baseLookupFor(member, member.name).withTypeText(SppSymbolLookup.shortTypeName(member.type), true))
    }

    // Otherwise, a function is offered once per overload, each
    // shown as it is called rather than as it is held. This gets
    // the actual signature, not the $MockType.
    return member.signatures.map { signature -> overloadLookupFor(member, signature) }
  }

  // The name of a member, with its icon and visibility marker,
  // as it is held in the symbol table.
  private fun baseLookupFor(member: SppMember, identity: String) =
    LookupElementBuilder.create(identity, member.name)
      .withIcon(withVisibility(iconFor(member.kind), member.visibility))
      .withCaseSensitivity(true)

  private fun overloadLookupFor(member: SppMember, signature: String): LookupElementBuilder {
    // Given the parameters, join their types with commas to
    // form the input part of the signature.
    val written = SppSymbolLookup.callParameters(signature).joinToString(", ") {
      SppSymbolLookup.shortTypeName(it)
    }

    // If this function call takes arguments, the caret needs to
    // go between the parenthesis, otherwise it goes after them.
    val takesArguments = SppSymbolLookup.callTakesArguments(signature)
    return baseLookupFor(member, "${member.name}$signature")
      .withTailText("($written)", true)
      .withTypeText(SppSymbolLookup.shortTypeName(signature.substringAfterLast(") -> ")), true)
      .withInsertHandler { context, _ ->
        context.document.insertString(context.tailOffset, "()")
        context.editor.caretModel.moveToOffset(context.tailOffset - if (takesArguments) 1 else 0)
      }
  }

  // Add a second symbol to the icon, showing the visibility
  // of the member. This will be seen on the completion menu for
  // "self" for example (the completion menu is pre-visibility
  // filtered anyway).
  private fun withVisibility(kind: Icon, visibility: String): Icon {
    val marker = when (visibility) {
      "public" -> AllIcons.Nodes.Public
      "package" -> AllIcons.Nodes.PackageLocal
      "protected" -> AllIcons.Nodes.Protected
      "private" -> AllIcons.Nodes.Private
      else -> return kind
    }
    return IconManager.getInstance().createRowIcon(kind, marker)
  }

  // The icon for each member type, to show alongside the member
  // name in the completion menu.
  private fun iconFor(kind: String): Icon = when (kind) {
    "function" -> AllIcons.Nodes.Method
    "attribute" -> AllIcons.Nodes.Field
    "constant" -> AllIcons.Nodes.Constant
    "namespace" -> AllIcons.Nodes.Package
    else -> AllIcons.Nodes.Variable
  }
}
