package icu.windea.bbcode.codeInsight.editorActions

import com.intellij.codeInsight.*
import com.intellij.codeInsight.editorActions.enter.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.*
import com.intellij.psi.*
import com.intellij.psi.util.*
import icu.windea.bbcode.*
import icu.windea.bbcode.psi.*
import icu.windea.bbcode.psi.BBCodeTypes.*

private val blockContainerTagNames = setOf("list", "ul", "ol", "olist")

// Expand [list]|[/list] on Enter to:
// [list]
//  [|
// [/list]
class BBCodeEnterHandlerDelegate : EnterHandlerDelegateAdapter() {
    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        if(file !is BBCodeFile) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val chars = document.charsSequence
        if(caretOffset < 0 || caretOffset >= chars.length) return EnterHandlerDelegate.Result.Continue
        val closingOffsetDelta = chars.nextCharOffsetSkippingWhiteSpace(caretOffset)
        val closingStartOffset = caretOffset + closingOffsetDelta
        if(chars.getOrNull(closingStartOffset) != '[' || chars.getOrNull(closingStartOffset + 1) != '/') return EnterHandlerDelegate.Result.Continue

        val endToken = file.findElementAt(closingStartOffset) ?: return EnterHandlerDelegate.Result.Continue
        if(endToken.elementType != TAG_SUFFIX_START) return EnterHandlerDelegate.Result.Continue
        val tag = endToken.parentOfType<BBCodeTag>(withSelf = false) ?: return EnterHandlerDelegate.Result.Continue
        val tagName = tag.name.orNull() ?: return EnterHandlerDelegate.Result.Continue
        if(tagName !in blockContainerTagNames) return EnterHandlerDelegate.Result.Continue

        val startTagEnd = tag.children()
            .firstOrNull { it.elementType == TAG_PREFIX_END || it.elementType == EMPTY_TAG_PREFIX_END }
            ?: return EnterHandlerDelegate.Result.Continue
        val bodyStartOffset = startTagEnd.endOffset
        if(bodyStartOffset > closingStartOffset) return EnterHandlerDelegate.Result.Continue
        val bodyText = chars.subSequence(bodyStartOffset, closingStartOffset)
        if(bodyText.any { !it.isWhitespace() }) return EnterHandlerDelegate.Result.Continue

        val currentLineStartOffset = document.getLineStartOffset(document.getLineNumber(closingStartOffset))
        val baseIndent = chars.subSequence(currentLineStartOffset, closingStartOffset).toString()
        if(baseIndent.any { !it.isWhitespace() }) return EnterHandlerDelegate.Result.Continue

        document.insertString(closingStartOffset, " [\n$baseIndent")
        editor.caretModel.moveToOffset(closingStartOffset + 2)
        AutoPopupController.getInstance(editor.project!!).scheduleAutoPopup(editor)
        return EnterHandlerDelegate.Result.Continue
    }
}
