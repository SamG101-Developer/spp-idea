package com.github.samg101developer.sppidea.diagnostics

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.absolute

/**
 * Offers what can be reached through a "." or a "::".
 *
 * The list is what the compiler found on the type or namespace being accessed, so it is what would actually resolve,
 * inherited members included. As with navigation, only what has already been worked out is used: a compile is seconds
 * and completion is asked for between keystrokes. A file nothing is known about yet starts one, so the list is there
 * the next time.
 */
class SppCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        val path = file.virtualFile?.let { runCatching { it.toNioPath().absolute() }.getOrNull() } ?: return
        val document = file.viewProvider.document ?: return

        // Read from the file as it is on disk rather than the copy completion works on: that copy has a placeholder
        // inserted at the caret, and everything a receiver is found by sits before it anyway.
        val text = document.charsSequence
        val offset = parameters.offset

        val service = SppCompilerDiagnostics.getInstance(file.project)
        val symbols = service.cachedSymbolsFor(path)
        if (symbols.isEmpty() && !service.isAnalysed(path)) {
            file.virtualFile?.let { service.warmUp(it) }
            return
        }

        // The name typed so far decides what is shown, read from the text rather than from the platform's own idea of
        // a prefix, which stops at characters this language allows in a name.
        val access = SppSymbolLookup.accessBefore(text, offset)
        val filtered = result.withPrefixMatcher(access?.prefix ?: SppSymbolLookup.prefixBefore(text, offset))

        // After a "." or a "::", what is reachable through it, and nothing else: a member access takes members.
        if (access != null) {
            val receiver = SppSymbolLookup.receiverOf(symbols, access, document) ?: return
            service.cachedMembersOf(receiver.type).forEach { filtered.addElement(lookupFor(it)) }
            return
        }

        // Between the brackets of a call or an object initializer, the names its arguments can be given. They are
        // offered as "name=", which is how they are written, and before the names in scope, which can be written
        // there too but are almost never what is being reached for.
        namedArgumentsAt(service, symbols, path, text, offset, document)
            .forEach { filtered.addElement(namedArgumentFor(it)) }

        SppSymbolLookup.namesInScopeAt(service.cachedScopesFor(path), offset, document)
            .forEach { filtered.addElement(lookupFor(it)) }
    }

    /** The arguments the call or object initializer around the caret takes, or nothing when it is inside neither. */
    private fun namedArgumentsAt(
        service: SppCompilerDiagnostics,
        symbols: List<SppSymbol>,
        path: Path,
        text: CharSequence,
        offset: Int,
        document: Document,
    ): List<SppMember> {
        val bracket = SppSymbolLookup.enclosingOpenBracket(text, offset) ?: return emptyList()

        // A type before the bracket means a value of it is being built, and the names are its attributes; anything
        // else is a call, and the names are the parameters the overload it resolved to takes.
        val calleeEnd = SppSymbolLookup.calleeEnd(text, bracket)
        val callee = symbols.filter { it.kind == "type" }
            .firstOrNull { SppSymbolLookup.rangeOf(it.use, document)?.endOffset == calleeEnd }

        if (callee != null) {
            return service.cachedMembersOf(callee.type).filter { it.kind == "attribute" }
        }
        return SppSymbolLookup.signatureAt(service.cachedSignaturesFor(path), offset, document)
            ?.params
            .orEmpty()
    }

    /** An argument's name, written the way it is given: "name=". */
    private fun namedArgumentFor(member: SppMember) = LookupElementBuilder.create(member.name + "=")
        .withIcon(AllIcons.Nodes.Parameter)
        .withTypeText(SppSymbolLookup.shortTypeName(member.type), true)
        .withCaseSensitivity(true)

    private fun lookupFor(member: SppMember): LookupElementBuilder {
        val element = LookupElementBuilder.create(member.name)
            .withIcon(iconFor(member.kind))
            .withCaseSensitivity(true)

        // A function is shown as it is called rather than as it is held: a method's own type is the "$" mock standing
        // for it, which says nothing. Where there are several ways to call it, the count says so.
        val signature = member.signatures.firstOrNull()
            ?: return element.withTypeText(SppSymbolLookup.shortTypeName(member.type), true)

        val overloads = if (member.signatures.size > 1) "  +${member.signatures.size - 1}" else ""
        val written = SppSymbolLookup.callParameters(signature).joinToString(", ") {
            SppSymbolLookup.shortTypeName(it)
        }
        val takesArguments = SppSymbolLookup.callTakesArguments(signature)

        return element
            .withTailText("($written)$overloads", true)
            .withTypeText(SppSymbolLookup.shortTypeName(signature.substringAfterLast(") -> ")), true)
            .withInsertHandler { context, _ ->
                // The brackets a call needs, with the caret put between them when there is something to write there.
                context.document.insertString(context.tailOffset, "()")
                context.editor.caretModel.moveToOffset(context.tailOffset - if (takesArguments) 1 else 0)
            }
    }

    private fun iconFor(kind: String): Icon = when (kind) {
        "function" -> AllIcons.Nodes.Method
        "attribute" -> AllIcons.Nodes.Field
        "constant" -> AllIcons.Nodes.Constant
        "namespace" -> AllIcons.Nodes.Package
        else -> AllIcons.Nodes.Variable
    }
}
