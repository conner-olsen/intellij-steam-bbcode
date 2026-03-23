package icu.windea.bbcode.codeInsight.editorActions

import com.intellij.codeInsight.*
import com.intellij.codeInsight.editorActions.enter.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.*
import com.intellij.psi.*
import com.intellij.psi.util.*
import icu.windea.bbcode.*
import icu.windea.bbcode.lang.schema.*
import icu.windea.bbcode.psi.*
import icu.windea.bbcode.psi.BBCodeTypes.*

private val blockContainerTagNames = setOf("list", "ul", "ol", "olist")
private val tagPattern = Regex("""\[(/)?([A-Za-z0-9*_-]+)[^]]*]""")

// Expand [list]|[/list] on Enter to:
// [list]
//  [|
// [/list]
//
// Also insert [ on Enter inside list container bodies:
// [list]
//  [*] text|     →  Enter  →     [*] text
// [/list]                         [|
//                                [/list]
class BBCodeEnterHandlerDelegate : EnterHandlerDelegateAdapter() {
    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        if(file !is BBCodeFile) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val chars = document.charsSequence
        if(caretOffset < 0 || caretOffset >= chars.length) return EnterHandlerDelegate.Result.Continue

        if(tryExpandEmptyContainerBody(file, editor, document, chars, caretOffset)) {
            return EnterHandlerDelegate.Result.Continue
        }

        if(tryInsertTagOpenInContainer(editor, document, chars, caretOffset)) {
            return EnterHandlerDelegate.Result.Continue
        }

        return EnterHandlerDelegate.Result.Continue
    }

    private fun tryExpandEmptyContainerBody(
        file: PsiFile, editor: Editor, document: Document, chars: CharSequence, caretOffset: Int
    ): Boolean {
        val closingOffsetDelta = chars.nextCharOffsetSkippingWhiteSpace(caretOffset)
        val closingStartOffset = caretOffset + closingOffsetDelta
        if(chars.getOrNull(closingStartOffset) != '[' || chars.getOrNull(closingStartOffset + 1) != '/') return false

        val endToken = file.findElementAt(closingStartOffset) ?: return false
        if(endToken.elementType != TAG_SUFFIX_START) return false
        val tag = endToken.parentOfType<BBCodeTag>(withSelf = false) ?: return false
        val tagName = tag.name.orNull() ?: return false
        if(tagName !in blockContainerTagNames) return false

        val startTagEnd = tag.children()
            .firstOrNull { it.elementType == TAG_PREFIX_END || it.elementType == EMPTY_TAG_PREFIX_END }
            ?: return false
        val bodyStartOffset = startTagEnd.endOffset
        if(bodyStartOffset > closingStartOffset) return false
        val bodyText = chars.subSequence(bodyStartOffset, closingStartOffset)
        if(bodyText.any { !it.isWhitespace() }) return false

        val currentLineStartOffset = document.getLineStartOffset(document.getLineNumber(closingStartOffset))
        val baseIndent = chars.subSequence(currentLineStartOffset, closingStartOffset).toString()
        if(baseIndent.any { !it.isWhitespace() }) return false

        document.insertString(closingStartOffset, " [\n$baseIndent")
        editor.caretModel.moveToOffset(closingStartOffset + 2)
        AutoPopupController.getInstance(editor.project!!).scheduleAutoPopup(editor)
        return true
    }

    private fun tryInsertTagOpenInContainer(
        editor: Editor, document: Document, chars: CharSequence, caretOffset: Int
    ): Boolean {
        // Only act if the rest of the current line (after caret) is whitespace-only
        val caretLine = document.getLineNumber(caretOffset)
        val lineEnd = document.getLineEndOffset(caretLine)
        val textAfterCaret = chars.subSequence(caretOffset, lineEnd)
        if(textAfterCaret.any { !it.isWhitespace() }) return false

        // Don't insert [ if the previous line is just a bare [ (avoid cascading)
        if(caretLine > 0) {
            val prevLineStart = document.getLineStartOffset(caretLine - 1)
            val prevLineEnd = document.getLineEndOffset(caretLine - 1)
            val prevLineContent = chars.subSequence(prevLineStart, prevLineEnd).toString().trim()
            if(prevLineContent == "[") return false
        }

        // Find the innermost enclosing block container via text scanning
        val containerIndent = findEnclosingContainerIndent(chars, caretOffset) ?: return false
        val childIndent = "$containerIndent "

        // Replace any auto-indent on the current line with the correct indent + [
        val lineStart = document.getLineStartOffset(caretLine)
        document.replaceString(lineStart, caretOffset, "$childIndent[")
        val newCaretOffset = lineStart + childIndent.length + 1
        editor.caretModel.moveToOffset(newCaretOffset)
        AutoPopupController.getInstance(editor.project!!).scheduleAutoPopup(editor)
        return true
    }

    /**
     * Scans text before [offset] for unclosed tags, tracking a stack.
     * Returns the indentation of the innermost unclosed block container tag,
     * or null if the caret is not directly inside a container (e.g. inside [b]...[/b]).
     */
    private fun findEnclosingContainerIndent(chars: CharSequence, offset: Int): String? {
        val textBefore = chars.subSequence(0, offset).toString()
        data class TagEntry(val name: String, val indent: String, val isContainer: Boolean)
        val stack = mutableListOf<TagEntry>()
        for(match in tagPattern.findAll(textBefore)) {
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2]
            if(isClosing) {
                val index = stack.indexOfLast { it.name == tagName }
                if(index >= 0) {
                    while(stack.size > index) stack.removeAt(stack.lastIndex)
                }
            } else {
                val tagType = BBCodeSchemaManager.getTagType(tagName)
                if(tagType != BBCodeTagType.Empty && tagType != BBCodeTagType.Line) {
                    val isContainer = tagName in blockContainerTagNames
                    val lineStart = textBefore.lastIndexOf('\n', match.range.first).let { if(it < 0) 0 else it + 1 }
                    val indent = textBefore.substring(lineStart, match.range.first)
                    stack.add(TagEntry(tagName, if(indent.all { it.isWhitespace() }) indent else "", isContainer))
                }
            }
        }
        // Only insert [ if the innermost unclosed tag IS a container
        // (don't insert if we're inside e.g. [b]...[/b] within the list)
        val innermost = stack.lastOrNull() ?: return null
        return if(innermost.isContainer) innermost.indent else null
    }
}
