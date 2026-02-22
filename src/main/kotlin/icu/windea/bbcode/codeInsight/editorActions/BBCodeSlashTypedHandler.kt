package icu.windea.bbcode.codeInsight.editorActions

import com.intellij.codeInsight.editorActions.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.project.*
import com.intellij.psi.*
import com.intellij.psi.codeStyle.*
import com.intellij.psi.impl.source.tree.*
import com.intellij.psi.util.*
import icu.windea.bbcode.*
import icu.windea.bbcode.psi.*
import icu.windea.bbcode.psi.BBCodeTypes.*

// Auto-complete closing tags.
//com.intellij.codeInsight.editorActions.XmlSlashTypedHandler

class BBCodeSlashTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, editedFile: PsiFile): Result {
        if (c == ']' || c == '/') {
            PsiDocumentManager.getInstance(project).commitAllDocuments()

            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            if (file !is BBCodeFile) return Result.CONTINUE
            val provider = file.viewProvider
            val offset = editor.caretModel.offset
            val element = provider.findElementAt(offset - 1, BBCodeLanguage::class.java)
            if (element == null) return Result.CONTINUE
            if (element.language !is BBCodeLanguage) return Result.CONTINUE

            val prevLeaf = element.node
            if (prevLeaf == null) return Result.CONTINUE
            val prevLeafText = prevLeaf.text
            if(prevLeafText == "]" && prevLeaf.elementType == TAG_PREFIX_END) {
                if(TreeUtil.findSibling(prevLeaf, TAG_NAME) != null) return Result.CONTINUE

                val tag = element.parentOfType<BBCodeTag>(false)
                val tagName = tag?.name ?: return Result.CONTINUE
                EditorModificationUtil.insertStringAtCaret(editor, "[/$tagName]", false, 0)
                autoIndent(editor)
                return Result.STOP
            } else if (prevLeafText == "[/" && prevLeaf.elementType == TAG_SUFFIX_START) {
                if(TreeUtil.findSibling(prevLeaf, TAG_NAME) != null) return Result.CONTINUE

                val tag = element.parentOfType<BBCodeTag>(false)
                val tagName = tag?.name ?: return Result.CONTINUE
                EditorModificationUtil.insertStringAtCaret(editor, "$tagName]", false)
                autoIndent(editor)
                return Result.STOP
            }
        }
        return Result.CONTINUE
    }

    private fun autoIndent(editor: Editor) {
        val project = editor.project
        if (project != null) {
            val documentManager = PsiDocumentManager.getInstance(project)
            val document = editor.document
            documentManager.commitDocument(document)
            val lineOffset = document.getLineStartOffset(document.getLineNumber(editor.caretModel.offset))
            CodeStyleManager.getInstance(project).adjustLineIndent(document, lineOffset)
        }
    }
}
