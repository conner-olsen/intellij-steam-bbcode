package icu.windea.bbcode.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.*
import com.intellij.openapi.editor.*
import com.intellij.psi.util.*
import com.intellij.util.*
import icons.*
import icu.windea.bbcode.*
import icu.windea.bbcode.lang.schema.*
import icu.windea.bbcode.psi.*

class BBCodeTagNameCompletionProvider : CompletionProvider<CompletionParameters>() {
    companion object {
        private val blockContainerTagNames = setOf("list", "ul", "ol", "olist")
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        if(parameters.position.prevSibling?.elementType != BBCodeTypes.TAG_PREFIX_START) return
        val tag = parameters.position.parent?.castOrNull<BBCodeTag>() ?: return
        val parentTag = tag.parent?.castOrNull<BBCodeTag>()
        val addedTagNames = mutableSetOf<String>()
        if(parentTag == null) {
            val schema = BBCodeSchemaManager.getSchema(project) ?: return
            //typing a root tag
            schema.tags.forEach f@{ tagSchema ->
                if(!tagSchema.parentNames.isNullOrEmpty()) return@f
                addLookupElement(tagSchema, addedTagNames, result)
            }
        } else {
            val parentTagSchema = BBCodeSchemaManager.resolveForTag(parentTag) ?: return
            // If completion happens inside a line list item (e.g. [*]...),
            // use the surrounding list container for child-tag suggestions.
            val completionContextTagSchema = parentTagSchema.takeUnless { it.type == BBCodeTagType.Line }
                ?: parentTag.parent?.castOrNull<BBCodeTag>()?.let { BBCodeSchemaManager.resolveForTag(it) }
                ?: parentTagSchema
            val schema = BBCodeSchemaManager.getSchema(project) ?: return
            completionContextTagSchema.childNames?.forEach f@{ childName ->
                val tagSchema = schema.tagMap[childName] ?: return@f
                if(tagSchema.parentNames != null && completionContextTagSchema.name !in tagSchema.parentNames) return@f
                addLookupElement(tagSchema, addedTagNames, result)
            }
            if(completionContextTagSchema.childNames == null) {
                //typing a inline or empty tag
                schema.tags.forEach f@{ tagSchema ->
                    if(!tagSchema.parentNames.isNullOrEmpty()) return@f
                    if(tagSchema.type != BBCodeTagType.Inline && tagSchema.type != BBCodeTagType.Empty) return@f
                    addLookupElement(tagSchema, addedTagNames, result)
                }
            }
        }
    }

    private fun addLookupElement(tagSchema: BBCodeSchema.Tag, addedTagNames: MutableSet<String>, result: CompletionResultSet) {
        if(!addedTagNames.add(tagSchema.name)) return
        val lookupElement = LookupElementBuilder.create(tagSchema.name)
            .withIcon(BBCodeIcons.Tag)
            .withInsertHandler h@{ c, _ ->
                val editor = c.editor
                val caretOffset = editor.caretModel.offset
                val content = c.document.charsSequence
                val nextCharOffset = content.nextCharOffsetSkippingWhiteSpace(caretOffset)
                val nextChar = content.getOrNull(caretOffset + nextCharOffset)
                if(tagSchema.attribute != null) {
                    if(tagSchema.attribute.optional) {
                        //do nothing
                        return@h
                    }
                    if (nextChar == '=') {
                        //move caret to the right bound of "="
                        EditorModificationUtil.moveCaretRelatively(editor, 1 + nextCharOffset)
                    } else {
                        //insert "=" and move caret to the right bound
                        EditorModificationUtil.insertStringAtCaret(editor, "=")
                    }
                } else if(tagSchema.attributes.isNotEmpty()) {
                    //do nothing
                } else {
                    if(nextChar == ']') {
                        //move caret to the right bound of "]"
                        EditorModificationUtil.moveCaretRelatively(editor, 1 + nextCharOffset)
                    } else if(tagSchema.type == BBCodeTagType.Empty || tagSchema.type == BBCodeTagType.Line) {
                        //insert "]" and move caret to the right bound
                        EditorModificationUtil.insertStringAtCaret(editor, "]", false, 1)
                    } else {
                        //insert "]" and move caret to the right bound, and then insert "[/<tag name>]"
                        EditorModificationUtil.insertStringAtCaret(editor, "][/${tagSchema.name}]", false, 1)
                    }
                    if(tagSchema.name in blockContainerTagNames) {
                        expandContainerBody(c, tagSchema.name)
                    }
                }
            }
        result.addElement(lookupElement)
    }

    private fun expandContainerBody(context: InsertionContext, tagName: String) {
        val editor = context.editor
        val document = context.document
        val caretOffset = editor.caretModel.offset
        if(!startsWithClosingTag(document.charsSequence, caretOffset, tagName)) return
        // Expand [tag]|[/tag] to:
        // [tag]
        //  |
        // [/tag]
        document.insertString(caretOffset, "\n \n")
        editor.caretModel.moveToOffset(caretOffset + 2)
    }

    private fun startsWithClosingTag(text: CharSequence, offset: Int, tagName: String): Boolean {
        val closingTag = "[/$tagName]"
        if(offset < 0 || offset + closingTag.length > text.length) return false
        for(i in closingTag.indices) {
            if(text[offset + i] != closingTag[i]) return false
        }
        return true
    }
}
