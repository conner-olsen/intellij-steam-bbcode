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
        private val tagPattern = Regex("""\[(/)?([A-Za-z0-9*_-]+)(?:[^\]]*)]""")
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        if(!isAfterTagPrefix(parameters)) return
        val schema = BBCodeSchemaManager.getSchema(project) ?: return
        val tag = parameters.position.parentOfType<BBCodeTag>(withSelf = false)
        val addedTagNames = mutableSetOf<String>()
        if(tag == null) {
            addFallbackContextCompletions(parameters, schema, addedTagNames, result)
            return
        }
        val parentTag = tag.parentOfType<BBCodeTag>(withSelf = false)
        if(parentTag == null) {
            //typing a root tag
            schema.tags.forEach f@{ tagSchema ->
                if(!tagSchema.parentNames.isNullOrEmpty()) return@f
                addLookupElement(tagSchema, addedTagNames, result)
            }
        } else {
            val completionContextTagSchema = findCompletionContextTagSchema(parentTag)
            val effectiveContextTagSchema = when {
                completionContextTagSchema?.childNames != null -> completionContextTagSchema
                else -> findFallbackContextTagSchema(parameters, schema)
                    ?.takeIf { it.childNames != null }
                    ?: completionContextTagSchema
            }
            if(effectiveContextTagSchema == null) {
                addFallbackContextCompletions(parameters, schema, addedTagNames, result)
                return
            }
            effectiveContextTagSchema.childNames?.forEach f@{ childName ->
                val tagSchema = schema.tagMap[childName] ?: return@f
                if(tagSchema.parentNames != null && effectiveContextTagSchema.name !in tagSchema.parentNames) return@f
                addLookupElement(tagSchema, addedTagNames, result)
            }
            if(effectiveContextTagSchema.childNames == null) {
                //typing a inline or empty tag
                schema.tags.forEach f@{ tagSchema ->
                    if(!tagSchema.parentNames.isNullOrEmpty()) return@f
                    if(tagSchema.type != BBCodeTagType.Inline && tagSchema.type != BBCodeTagType.Empty) return@f
                    addLookupElement(tagSchema, addedTagNames, result)
                }
            }
        }
    }

    private fun addFallbackContextCompletions(
        parameters: CompletionParameters,
        schema: BBCodeSchema,
        addedTagNames: MutableSet<String>,
        result: CompletionResultSet
    ) {
        val contextTagSchema = findFallbackContextTagSchema(parameters, schema)
        if(contextTagSchema?.childNames != null) {
            contextTagSchema.childNames.forEach { childName ->
                val childTagSchema = schema.tagMap[childName] ?: return@forEach
                addLookupElement(childTagSchema, addedTagNames, result)
            }
        } else {
            schema.tags.forEach f@{ tagSchema ->
                if(!tagSchema.parentNames.isNullOrEmpty()) return@f
                addLookupElement(tagSchema, addedTagNames, result)
            }
        }
    }

    private fun findFallbackContextTagSchema(parameters: CompletionParameters, schema: BBCodeSchema): BBCodeSchema.Tag? {
        val caretOffset = parameters.offset.coerceIn(0, parameters.originalFile.text.length)
        val textBeforeCaret = parameters.originalFile.text.substring(0, caretOffset)
        val openTagStack = mutableListOf<String>()
        for(match in tagPattern.findAll(textBeforeCaret)) {
            val isClosingTag = match.groupValues[1] == "/"
            val tagName = match.groupValues[2]
            val tagSchema = schema.tagMap[tagName] ?: continue
            if(isClosingTag) {
                val index = openTagStack.lastIndexOf(tagName)
                if(index >= 0) {
                    while(openTagStack.size > index) {
                        openTagStack.removeAt(openTagStack.lastIndex)
                    }
                }
            } else {
                if(tagSchema.type != BBCodeTagType.Empty && tagSchema.type != BBCodeTagType.Line) {
                    openTagStack += tagName
                }
            }
        }
        return openTagStack.lastOrNull()?.let { schema.tagMap[it] }
    }

    private fun isAfterTagPrefix(parameters: CompletionParameters): Boolean {
        val text = parameters.originalFile.text
        var offset = parameters.offset - 1
        if(offset < 0 || offset >= text.length) return false
        while(offset >= 0 && isTagNameCharacter(text[offset])) {
            offset--
        }
        if(offset < 0 || text[offset] != '[') return false
        if(offset + 1 < text.length && text[offset + 1] == '/') return false
        return true
    }

    private fun isTagNameCharacter(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '_' || c == '-' || c == '*'
    }

    private fun findCompletionContextTagSchema(parentTag: BBCodeTag): BBCodeSchema.Tag? {
        var currentTag: BBCodeTag? = parentTag
        while(currentTag != null) {
            val currentSchema = BBCodeSchemaManager.resolveForTag(currentTag)
            if(currentSchema != null) {
                val candidateSchema = if(currentSchema.type == BBCodeTagType.Line) {
                    currentTag.parent?.castOrNull<BBCodeTag>()?.let { BBCodeSchemaManager.resolveForTag(it) } ?: currentSchema
                } else currentSchema
                if(candidateSchema.childNames != null) return candidateSchema
            }
            currentTag = currentTag.parent?.castOrNull()
        }
        return BBCodeSchemaManager.resolveForTag(parentTag)
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
        val chars = document.charsSequence
        val caretOffset = editor.caretModel.offset
        if(!startsWithClosingTag(chars, caretOffset, tagName)) return

        val lineNumber = document.getLineNumber(caretOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val openingTagOffset = findOpeningTagOffset(chars, lineStartOffset, caretOffset)
        if(openingTagOffset < lineStartOffset) return
        val baseIndent = chars.subSequence(lineStartOffset, openingTagOffset).toString()
        if(baseIndent.any { !it.isWhitespace() }) return

        val childIndent = "$baseIndent "
        // Expand [tag]|[/tag] to:
        // [tag]
        // <baseIndent><one-indent>|
        // [/tag]
        document.insertString(caretOffset, "\n$childIndent\n$baseIndent")
        editor.caretModel.moveToOffset(caretOffset + 1 + childIndent.length)
    }

    private fun findOpeningTagOffset(text: CharSequence, lineStartOffset: Int, caretOffset: Int): Int {
        var i = caretOffset - 1
        while(i >= lineStartOffset) {
            if(text[i] == '[') return i
            i--
        }
        return -1
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
