package icu.windea.bbcode.codeInsight.completion

import com.intellij.codeInsight.*
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
        private val tagPattern = Regex("""\[(/)?([A-Za-z0-9*_-]+)[^]]*]""")
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        val tagNamePrefix = getTagNamePrefix(parameters) ?: return
        val prefixedResult = result.withPrefixMatcher(BBCodeTagPrefixMatcher(tagNamePrefix))
        val schema = BBCodeSchemaManager.getSchema(project) ?: return
        val tag = parameters.position.parentOfType<BBCodeTag>(withSelf = false)
        val addedTagNames = mutableSetOf<String>()
        if(tag == null) {
            addFallbackContextCompletions(parameters, schema, addedTagNames, prefixedResult)
            return
        }
        val parentTag = tag.parentOfType<BBCodeTag>(withSelf = false)
        if(parentTag == null) {
            addFallbackContextCompletions(parameters, schema, addedTagNames, prefixedResult)
        } else {
            val completionContextTagSchema = findCompletionContextTagSchema(parentTag, parameters.originalFile.text, parameters.offset)
            // When inside a Line tag's body (e.g. [*] text [|), use its schema directly.
            // It has no childNames, so the caller will offer inline/empty completions.
            // Don't let the fallback scanner override this by finding the enclosing list.
            val isInsideLineTagBody = completionContextTagSchema?.type == BBCodeTagType.Line
            val effectiveContextTagSchema = when {
                completionContextTagSchema?.childNames != null -> completionContextTagSchema
                isInsideLineTagBody -> completionContextTagSchema
                else -> findFallbackContextTagSchema(parameters, schema)
                    ?.takeIf { it.childNames != null }
                    ?: completionContextTagSchema
            }
            if(effectiveContextTagSchema == null) {
                addFallbackContextCompletions(parameters, schema, addedTagNames, prefixedResult)
                return
            }
            effectiveContextTagSchema.childNames?.forEach f@{ childName ->
                val tagSchema = schema.tagMap[childName] ?: return@f
                if(tagSchema.parentNames != null && effectiveContextTagSchema.name !in tagSchema.parentNames) return@f
                addLookupElement(tagSchema, addedTagNames, prefixedResult)
            }
            if(effectiveContextTagSchema.childNames == null) {
                //typing a inline or empty tag
                schema.tags.forEach f@{ tagSchema ->
                    if(!tagSchema.parentNames.isNullOrEmpty()) return@f
                    if(tagSchema.type != BBCodeTagType.Inline && tagSchema.type != BBCodeTagType.Empty) return@f
                    addLookupElement(tagSchema, addedTagNames, prefixedResult)
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

    private fun getTagNamePrefix(parameters: CompletionParameters): String? {
        val text = parameters.originalFile.text
        val offset = parameters.offset.coerceIn(0, text.length)
        var prefixStart = offset - 1
        if(prefixStart < 0 || prefixStart >= text.length) return null
        while(prefixStart >= 0 && isTagNameCharacter(text[prefixStart])) {
            prefixStart--
        }
        if(prefixStart < 0 || text[prefixStart] != '[') return null
        if(prefixStart + 1 < text.length && text[prefixStart + 1] == '/') return null

        var prefixEnd = offset
        while(prefixEnd < text.length && isTagNameCharacter(text[prefixEnd])) {
            prefixEnd++
        }
        var prefix = text.substring(prefixStart + 1, prefixEnd)
        if(prefix.endsWith(BBCodeConstants.dummyIdentifier)) {
            prefix = prefix.removeSuffix(BBCodeConstants.dummyIdentifier)
        }
        return prefix
    }

    private fun isTagNameCharacter(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '_' || c == '-' || c == '*'
    }

    private fun findCompletionContextTagSchema(parentTag: BBCodeTag, fileText: String, caretOffset: Int): BBCodeSchema.Tag? {
        var currentTag: BBCodeTag? = parentTag
        while(currentTag != null) {
            val currentSchema = BBCodeSchemaManager.resolveForTag(currentTag)
            if(currentSchema != null) {
                if(currentSchema.type == BBCodeTagType.Line) {
                    // Line tag body (e.g. [*]) — the PSI extends across lines until the
                    // next [*] or [/list], but only SAME-LINE content is truly "inside
                    // the bullet".  A [<caret> on a DIFFERENT line is a new list item.
                    val tagOffset = currentTag.textOffset
                    val lo = minOf(tagOffset, caretOffset).coerceIn(0, fileText.length)
                    val hi = maxOf(tagOffset, caretOffset).coerceIn(0, fileText.length)
                    val sameLine = '\n' !in fileText.substring(lo, hi)
                    if(sameLine) {
                        // Same line as [*] — typing inline content inside the bullet
                        return currentSchema
                    }
                    // Different line — return null so the caller uses the fallback
                    // text scanner, which correctly finds the enclosing [list] even
                    // when the PSI tree is broken by the dummy identifier injection.
                    return null
                }
                if(currentSchema.childNames != null) return currentSchema
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
                        if(tagSchema.type == BBCodeTagType.Line) {
                            insertLineTagTrailingSpace(editor)
                        }
                    } else if(tagSchema.type == BBCodeTagType.Line) {
                        //insert "] " and move caret to the right bound
                        EditorModificationUtil.insertStringAtCaret(editor, "] ", false, 2)
                    } else if(tagSchema.type == BBCodeTagType.Empty) {
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
        val prioritized = if(tagSchema.type == BBCodeTagType.Line) {
            PrioritizedLookupElement.withPriority(lookupElement, 1.0)
        } else lookupElement
        result.addElement(prioritized)
    }

    private fun insertLineTagTrailingSpace(editor: Editor) {
        val caretOffset = editor.caretModel.offset
        val nextChar = editor.document.charsSequence.getOrNull(caretOffset)
        if(nextChar != ' ') {
            EditorModificationUtil.insertStringAtCaret(editor, " ")
        }
    }

    private class BBCodeTagPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
        override fun cloneWithPrefix(prefix: String): PrefixMatcher = BBCodeTagPrefixMatcher(prefix)

        override fun prefixMatches(name: String): Boolean {
            return name.startsWith(prefix, ignoreCase = true)
        }

        override fun isStartMatch(name: String): Boolean {
            return prefixMatches(name)
        }
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
        // <baseIndent><one-indent>[|
        // [/tag]
        document.insertString(caretOffset, "\n$childIndent[\n$baseIndent")
        editor.caretModel.moveToOffset(caretOffset + 1 + childIndent.length + 1)
        AutoPopupController.getInstance(context.project).scheduleAutoPopup(editor)
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
