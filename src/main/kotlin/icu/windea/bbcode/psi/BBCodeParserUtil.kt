package icu.windea.bbcode.psi

import com.intellij.lang.*
import com.intellij.lang.parser.*
import com.intellij.psi.*
import icu.windea.bbcode.*
import icu.windea.bbcode.lang.schema.*
import icu.windea.bbcode.psi.BBCodeTypes.*
import icu.windea.bbcode.util.*

@Suppress("UNUSED_PARAMETER")
object BBCodeParserUtil : GeneratedParserUtilBase() {
    object Keys: KeyRegistry() {
        val tagName by createKey<String>(Keys)
        val lineTagBodyDepthStack by createKey<MutableList<Int>>(Keys)
    }

    @JvmStatic
    fun putTagName(b: PsiBuilder, l: Int): Boolean {
        if(b.tokenType == TAG_NAME) {
            b.putUserData(Keys.tagName, b.tokenText)
        }
        return true
    }

    @JvmStatic
    fun isIncompleteTag(b: PsiBuilder, l: Int): Boolean {
        var prevTokenType = b.rawLookup(-1)
        if (prevTokenType == TokenType.WHITE_SPACE) prevTokenType = b.rawLookup(-2)
        return prevTokenType != TAG_PREFIX_END && prevTokenType != EMPTY_TAG_PREFIX_END
    }

    @JvmStatic
    fun isInlineTag(b: PsiBuilder, l: Int): Boolean {
        val tagName = b.getUserData(Keys.tagName) ?: return false
        val tagType = BBCodeSchemaManager.getSchema(b.project)?.tagMap?.get(tagName)?.type ?: return false
        if(tagType != BBCodeTagType.Empty) return false
        return true
    }

    @JvmStatic
    fun isLineTag(b: PsiBuilder, l: Int): Boolean {
        val tagName = b.getUserData(Keys.tagName) ?: return false
        val tagType = BBCodeSchemaManager.getSchema(b.project)?.tagMap?.get(tagName)?.type ?: return false
        if (tagType != BBCodeTagType.Line) return false

        // Generated parser currently invokes checkTagBody at depth (l + 3) for this line tag body.
        // Track that depth on a stack to avoid leaking line-tag behavior into nested normal tags.
        val stack = b.getUserData(Keys.lineTagBodyDepthStack) ?: mutableListOf<Int>().also {
            b.putUserData(Keys.lineTagBodyDepthStack, it)
        }
        stack += l + 3
        return true
    }

    @JvmStatic
    fun exitLineTag(b: PsiBuilder, l: Int): Boolean {
        val stack = b.getUserData(Keys.lineTagBodyDepthStack)
        if (!stack.isNullOrEmpty()) {
            stack.removeAt(stack.lastIndex)
            if (stack.isEmpty()) b.putUserData(Keys.lineTagBodyDepthStack, null)
        }
        return true
    }

    @JvmStatic
    fun checkTagBody(b: PsiBuilder, l: Int): Boolean {
        val stack = b.getUserData(Keys.lineTagBodyDepthStack)
        if (stack.isNullOrEmpty()) return true
        val activeBodyDepth = stack.last()
        if (l != activeBodyDepth) return true

        var i = 0
        if(b.rawLookup(i) != TAG_PREFIX_START) return true
        i++
        if(b.rawLookup(i) == TokenType.WHITE_SPACE) i++
        if(b.rawLookup(i) != TAG_NAME) return true
        val start = b.rawTokenTypeStart(i)
        val end = b.rawTokenTypeStart(i + 1).let { if(it != -1) it else b.originalText.length }
        val tagName = b.originalText.substring(start, end).orNull() ?: return true
        val tagType = BBCodeSchemaManager.getSchema(b.project)?.tagMap?.get(tagName)?.type ?: return true
        if (tagType != BBCodeTagType.Line) return true
        return false
    }

    @JvmStatic
    fun checkLineTagBody(b: PsiBuilder, l: Int): Boolean {
        return checkTagBody(b, l)
    }
}
