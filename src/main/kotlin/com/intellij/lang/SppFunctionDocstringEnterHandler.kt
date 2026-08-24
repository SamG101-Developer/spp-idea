package com.intellij.lang

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.lang.psi.*
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil

class SppFunctionDocstringEnterHandler : EnterHandlerDelegateAdapter() {
    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): EnterHandlerDelegate.Result {
        // Only apply this to the S++ language spec. Commit the document
        // beforehand, for history purposes (undo, redo, etc).
        if (file.language != SppLanguage.INSTANCE) return EnterHandlerDelegate.Result.Continue
        PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)

        // Determine commonly needed information from teh editor and file
        // for the generation of docstrings across multiple ast nodes,
        // listed below.
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
            ?: file.findElementAt(offset - 1)
            ?: return EnterHandlerDelegate.Result.Continue

        // There are a few different auto docstrings supported, including
        // and limited to functions, sup-function blocks and classes. More
        // may be introduced should the need arise. They only fire when the
        // user has just typed a bare "#" as the first line of the body and
        // pressed Enter - plain Enter inside braces otherwise just makes a
        // new line, same as anywhere else.
        val curLineNum = editor.document.getLineNumber(offset)
        val inserted = tryFunction(element, editor, offset)
            ?: trySupFunctions(element, editor, offset)
            ?: tryClass(element, editor, offset)

        // Autoformat to apply indentation rules, then reposition the caret to the end of the bare-hash line
        // the user typed - the rest of the framework was filled in below it.
        if (inserted != null) {
            PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
            CodeStyleManager.getInstance(file.project).reformatText(file, inserted.startOffset, inserted.endOffset)
            if (curLineNum > 0) editor.caretModel.moveToOffset(editor.document.getLineEndOffset(curLineNum - 1))
        } else {
            continueComment(editor, offset)
        }

        return EnterHandlerDelegate.Result.Continue
    }

    private fun tryFunction(
        element: PsiElement,
        editor: Editor,
        offset: Int
    ): TextRange? {
        // Ast node check, for a function implementation. From here, we can
        // get the prototype information, but we must be inside the function
        // implementation to activate the docstring.
        val funcImpl = PsiTreeUtil.getParentOfType(element, SppFunctionImplementation::class.java) ?: return null
        if (!hasBareHashStarter(funcImpl, editor, offset)) return null

        // If this is beneath existing members, don't add the docs string.
        // Only at the top of the function.
        if (funcImpl.functionMemberList.isNotEmpty()) {
            val firstMember = funcImpl.functionMemberList.first()
            if (offset > firstMember.textRange.startOffset) return null
        }

        val proto = funcImpl.parent
        if (proto !is SppSubroutinePrototype && proto !is SppCoroutinePrototype) return null

        // Extract the generic parameter list from the function prototype.
        val genericParams: List<SppGenericParameter> = when (proto) {
            is SppSubroutinePrototype -> proto.genericParameterGroup?.genericParameterList
            is SppCoroutinePrototype -> proto.genericParameterGroup?.genericParameterList
            else -> null
        } ?: emptyList()

        // Extract the function parameter list from the function prototype.
        val funcParams: List<SppFunctionParameter> = when (proto) {
            is SppSubroutinePrototype -> (proto.functionParameterGroup ?: return null).functionParameterList
            is SppCoroutinePrototype -> (proto.functionParameterGroup ?: return null).functionParameterList
            else -> emptyList()
        }

        // Build the docstring from the nodes gathered above. The description line was already typed by the
        // user (the bare "#" that triggered this), so only the tag lines need to be filled in.
        val text = buildString {
            for (gp in genericParams) append(genericParamLine(gp))
            for (fp in funcParams) append(funcParamLine(fp))
            append(funcReturnLine())
        }

        return insert(editor, offset, text)
    }

    private fun trySupFunctions(
        element: PsiElement,
        editor: Editor,
        offset: Int
    ): TextRange? {
        // Ast node check, for a sup implementation, inside a sup block (not
        // a sup-ext block). from here, we can get the prototype information,
        // but we must be inside the function implementation to activate the
        // docstring.
        val supImpl = PsiTreeUtil.getParentOfType(element, SppSupImplementation::class.java) ?: return null
        if (supImpl.parent !is SppSupPrototypeFunctions) return null

        // Don't fire when the cursor is inside a nested function body within this sup.
        val nestedFunc = PsiTreeUtil.getParentOfType(element, SppFunctionImplementation::class.java)
        if (nestedFunc != null && PsiTreeUtil.isAncestor(supImpl, nestedFunc, true)) return null
        if (!hasBareHashStarter(supImpl, editor, offset)) return null

        // If this is beneath existing members, don't add the docs string.
        // Only at the top of the superimposition.
        if (supImpl.supMemberList.isNotEmpty()) {
            val firstMember = supImpl.supMemberList.first()
            if (offset > firstMember.textRange.startOffset) return null
        }

        val text = buildString {
            for (member in supImpl.supMemberList) {
                member.supTypeStatement?.typeStatement?.let { append(supTypeStatementLine(it.upperIdentifier.text)) }
                member.supCmpStatement?.cmpStatement?.let { append(supCmpStatementLine(it.identifier.text)) }
            }
        }

        return insert(editor, offset, text)
    }

    private fun tryClass(
        element: PsiElement,
        editor: Editor,
        offset: Int
    ): TextRange? {
        // Ast node check, for a class implementation. From here, we can get the
        // prototype information, but we must be inside the class implementation to
        // activate the docstring.
        val classImpl = PsiTreeUtil.getParentOfType(element, SppClassImplementation::class.java) ?: return null
        if (!hasBareHashStarter(classImpl, editor, offset)) return null
        val proto = classImpl.parent as? SppClassPrototype ?: return null

        // If this is beneath existing members, don't add the docs string.
        // Only at the top of the class.
        if (classImpl.classMemberList.isNotEmpty()) {
            val firstMember = classImpl.classMemberList.first()
            if (offset > firstMember.textRange.startOffset) return null
        }

        // Extract the generic parameter list from the class prototype, and the
        // field list from the class implementation.
        val genericParams = proto.genericParameterGroup?.genericParameterList ?: emptyList()
        val attributes = classImpl.classMemberList.map { it.classAttribute }

        // Build the docstring from the nodes gathered above. The description line was already typed by the
        // user (the bare "#" that triggered this), so only the tag lines need to be filled in.
        val text = buildString {
            for (gp in genericParams) append(genericParamLine(gp))
            for (attr in attributes) append(clsFieldLine(attr.identifier.text))
        }

        return insert(editor, offset, text)
    }

    // If the previous line was a # comment, continue it on the new line.
    // Fires when: (a) Enter was pressed in the middle of a comment, or
    // (b) at the end of a comment whose next sibling line is also a comment.
    private fun continueComment(editor: Editor, offset: Int) {
        val doc = editor.document
        val curLineNum = doc.getLineNumber(offset)
        if (curLineNum == 0) return

        val prevLineText = doc.getText(TextRange(
            doc.getLineStartOffset(curLineNum - 1),
            doc.getLineEndOffset(curLineNum - 1)
        ))

        val contentIdx = prevLineText.indexOfFirst { !it.isWhitespace() }
        if (contentIdx < 0 || prevLineText[contentIdx] != '#') return
        val commentIndent = prevLineText.substring(0, contentIdx)

        // Detect middle-of-comment: IntelliJ moved text after cursor to this line.
        val curLineEnd = doc.getLineEndOffset(curLineNum)
        val inMiddle = doc.getText(TextRange(offset, curLineEnd)).isNotBlank()

        if (!inMiddle) {
            // At end of comment: only continue if the line below is also a comment.
            val nextLineNum = curLineNum + 1
            if (nextLineNum >= doc.lineCount) return
            val nextLineText = doc.getText(TextRange(
                doc.getLineStartOffset(nextLineNum),
                doc.getLineEndOffset(nextLineNum)
            ))
            val nextIdx = nextLineText.indexOfFirst { !it.isWhitespace() }
            if (nextIdx < 0 || nextLineText[nextIdx] != '#') return
        }

        // Replace whatever auto-indent was added with the correct comment prefix.
        val curLineStart = doc.getLineStartOffset(curLineNum)
        doc.replaceString(curLineStart, offset, "$commentIndent# ")
        editor.caretModel.moveToOffset(curLineStart + commentIndent.length + "# ".length)
    }

    // True when the block's docstring is exactly one bare "#" comment - i.e. the user just typed "#" as the
    // very first line of the body and pressed Enter, asking for the rest of the docstring framework.
    private fun hasBareHashStarter(impl: PsiElement, editor: Editor, offset: Int): Boolean {
        val curLineNum = editor.document.getLineNumber(offset)
        if (curLineNum == 0) return false
        val prevLineText = editor.document.getText(TextRange(
            editor.document.getLineStartOffset(curLineNum - 1),
            editor.document.getLineEndOffset(curLineNum - 1)
        ))
        if (prevLineText.trim() != "#") return false

        // That "#" must be the very first thing in the body - nothing but whitespace between "{" and it.
        var child = impl.firstChild?.nextSibling // step past "{"
        while (child is PsiWhiteSpace) child = child.nextSibling
        return child is PsiComment && child.text.trim() == "#"
    }

    private fun insert(editor: Editor, offset: Int, text: String): TextRange {
        // Keep exactly one trailing newline after the tag lines (even if there are none), so a single blank
        // line separates the docstring block from whatever follows it (the closing brace, or the first member).
        val body = text.trimEnd('\n')
        val toInsert = if (body.isEmpty()) "\n" else "$body\n"
        editor.document.insertString(offset, toInsert)
        return TextRange(offset, offset + toInsert.length)
    }

    private fun genericParamLine(gp: SppGenericParameter): String {
        val name = genericParamName(gp) ?: return ""
        val tag = if (gp.genericParameterType != null) "type" else "cmp"
        return "# @$tag $name: \n"
    }

    private fun funcParamLine(fp: SppFunctionParameter): String {
        val name = funcParamName(fp) ?: return ""
        val prefix = when {
            fp.functionParameterRequired != null -> ""
            fp.functionParameterOptional != null -> "?"
            fp.functionParameterVariadic != null -> ".."
            else -> return ""
        }
        return "# @let $prefix$name: \n"
    }

    private fun funcReturnLine(): String {
        return "# @ret: \n"
    }

    private fun supTypeStatementLine(typeIdentifier: String): String {
        return "# @type $typeIdentifier: \n"
    }

    private fun supCmpStatementLine(cmpIdentifier: String): String {
        return "# @cmp $cmpIdentifier: \n"
    }

    private fun clsFieldLine(fieldIdentifier: String): String {
        return "# @let $fieldIdentifier: \n"
    }

    private fun genericParamName(gp: SppGenericParameter): String? = when {
        gp.genericParameterType?.genericParameterTypeRequired != null ->
            gp.genericParameterType!!.genericParameterTypeRequired!!.typeIdentifier.text

        gp.genericParameterType?.genericParameterTypeOptional != null ->
            gp.genericParameterType!!.genericParameterTypeOptional!!.typeIdentifier.text

        gp.genericParameterType?.genericParameterTypeVariadic != null ->
            gp.genericParameterType!!.genericParameterTypeVariadic!!.typeIdentifier.text

        gp.genericParameterComp?.genericParameterCompRequired != null ->
            gp.genericParameterComp!!.genericParameterCompRequired!!.identifier.text

        gp.genericParameterComp?.genericParameterCompOptional != null ->
            gp.genericParameterComp!!.genericParameterCompOptional!!.identifier.text

        gp.genericParameterComp?.genericParameterCompVariadic != null ->
            gp.genericParameterComp!!.genericParameterCompVariadic!!.identifier.text

        else -> null
    }

    private fun funcParamName(fp: SppFunctionParameter): String? = when {
        fp.functionParameterRequired != null ->
            fp.functionParameterRequired!!.localVariable.localVariableSingleIdentifier?.identifier?.text

        fp.functionParameterOptional != null ->
            fp.functionParameterOptional!!.localVariable.localVariableSingleIdentifier?.identifier?.text

        fp.functionParameterVariadic != null ->
            fp.functionParameterVariadic!!.localVariable.localVariableSingleIdentifier?.identifier?.text

        fp.functionParameterSelf != null -> "self"
        else -> null
    }
}