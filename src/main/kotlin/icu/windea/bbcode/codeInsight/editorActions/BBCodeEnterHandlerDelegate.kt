package icu.windea.bbcode.codeInsight.editorActions

import com.intellij.codeInsight.editorActions.enter.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.*
import com.intellij.psi.*
import com.intellij.psi.util.*
import icu.windea.bbcode.*
import icu.windea.bbcode.lang.*
import icu.windea.bbcode.psi.*
import icu.windea.bbcode.psi.BBCodeTypes.*

// Expand [list]|[/list] on Enter to:
// [list]
//  |
// [/list]
class BBCodeEnterHandlerDelegate : EnterHandlerDelegateAdapter() {
    companion object {
        private val blockContainerTagNames = setOf("list", "ul", "ol", "olist")
    }

    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        if(file !is BBCodeFile) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val chars = document.charsSequence
        if(caretOffset < 0 || caretOffset >= chars.length) return EnterHandlerDelegate.Result.Continue
        if(chars.getOrNull(caretOffset) != '[' || chars.getOrNull(caretOffset + 1) != '/') return EnterHandlerDelegate.Result.Continue

        val endToken = file.findElementAt(caretOffset) ?: return EnterHandlerDelegate.Result.Continue
        if(endToken.elementType != TAG_SUFFIX_START) return EnterHandlerDelegate.Result.Continue
        val tag = endToken.parentOfType<BBCodeTag>(withSelf = false) ?: return EnterHandlerDelegate.Result.Continue
        val tagName = tag.name.orNull() ?: return EnterHandlerDelegate.Result.Continue
        if(tagName !in blockContainerTagNames) return EnterHandlerDelegate.Result.Continue

        val startTagEnd = tag.children()
            .firstOrNull { it.elementType == TAG_PREFIX_END || it.elementType == EMPTY_TAG_PREFIX_END }
            ?: return EnterHandlerDelegate.Result.Continue
        val bodyStartOffset = startTagEnd.endOffset
        if(bodyStartOffset > caretOffset) return EnterHandlerDelegate.Result.Continue
        val bodyText = chars.subSequence(bodyStartOffset, caretOffset)
        if(bodyText.any { !it.isWhitespace() }) return EnterHandlerDelegate.Result.Continue

        val currentLineStartOffset = document.getLineStartOffset(document.getLineNumber(caretOffset))
        val baseIndent = chars.subSequence(currentLineStartOffset, caretOffset).toString()
        if(baseIndent.any { !it.isWhitespace() }) return EnterHandlerDelegate.Result.Continue

        document.insertString(caretOffset, " \n$baseIndent")
        editor.caretModel.moveToOffset(caretOffset + 1)
        return EnterHandlerDelegate.Result.Continue
    }
}
