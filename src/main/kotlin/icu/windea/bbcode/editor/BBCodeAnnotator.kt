package icu.windea.bbcode.editor

import com.intellij.lang.annotation.*
import com.intellij.psi.*
import com.intellij.psi.util.*
import icu.windea.bbcode.*
import icu.windea.bbcode.intentions.*
import icu.windea.bbcode.lang.*
import icu.windea.bbcode.psi.*

//com.intellij.codeInspection.htmlInspections.XmlWrongClosingTagNameInspection

class BBCodeAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        annotateMismatchedTag(element, holder)
    }

    private fun annotateMismatchedTag(element: PsiElement, holder: AnnotationHolder) {
        // Highlight mismatched tags.
        if (element.elementType != BBCodeTypes.TAG_NAME) return
        val parent = element.parent ?: return
        val tag = (if (parent is PsiErrorElement) parent.parent else parent) as? BBCodeTag ?: return
        val startElement = BBCodeManager.getStartTagNameElement(tag)
        val endElement = BBCodeManager.getEndTagNameElement(tag)
        if (element == startElement) {
            if (endElement != null && tag.name != endElement.text) {
                registerProblemStart(holder, tag, startElement, endElement)
            }
        } else if (element == endElement) {
            if (tag.name != endElement.text) {
                registerProblemEnd(holder, tag, endElement)
            }
        }
    }

    private fun registerProblemStart(holder: AnnotationHolder, tag: BBCodeTag, startElement: PsiElement, endElement: PsiElement) {
        val tagName = tag.name
        val endText = endElement.text
        val renameEndAction = RenameTagBeginOrEndIntentionAction(tagName, endText, false)
        val renameStartAction = RenameTagBeginOrEndIntentionAction(endText, tagName, true)
        holder.newAnnotation(HighlightSeverity.ERROR, BBCodeBundle.message("bbcode.inspection.tag.has.wrong.closing.tag.name"))
            .range(startElement).withFix(renameEndAction).withFix(renameStartAction)
            .create()
    }

    private fun registerProblemEnd(holder: AnnotationHolder, tag: BBCodeTag, endElement: PsiElement) {
        val tagName = tag.name
        val endText = endElement.text
        val removeSuffixAction = RemoveExtraClosingTagIntentionAction()
        val renameEndAction = RenameTagBeginOrEndIntentionAction(tagName, endText, false)
        val renameStartAction = RenameTagBeginOrEndIntentionAction(endText, tagName, true)
        holder.newAnnotation(HighlightSeverity.ERROR, BBCodeBundle.message("bbcode.inspection.wrong.closing.tag.name"))
            .range(endElement).withFix(removeSuffixAction).withFix(renameEndAction).withFix(renameStartAction)
            .create()
    }
}

