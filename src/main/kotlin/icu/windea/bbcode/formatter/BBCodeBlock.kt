package icu.windea.bbcode.formatter

import com.intellij.formatting.*
import com.intellij.formatting.Indent
import com.intellij.lang.*
import com.intellij.psi.*
import com.intellij.psi.codeStyle.*
import com.intellij.psi.formatter.common.*
import com.intellij.psi.tree.*
import icu.windea.bbcode.*
import icu.windea.bbcode.codeStyle.*
import icu.windea.bbcode.psi.BBCodeTypes.*

//com.intellij.psi.formatter.xml.XmlBlock

class BBCodeBlock(
    node: ASTNode,
    private val settings: CodeStyleSettings
) : AbstractBlock(node, createWrap(), createAlignment()) {
    companion object {
        private val NOT_EMPTY_TAG_NAME_END = TokenSet.create(TAG_PREFIX_END, TAG_SUFFIX_END)

        private fun createWrap(): Wrap? {
            return null
        }

        private fun createAlignment(): Alignment? {
            return null
        }

        private fun createSpacingBuilder(settings: CodeStyleSettings): SpacingBuilder {
            val customSettings = settings.getCustomSettings(BBCodeCodeStyleSettings::class.java)
            return SpacingBuilder(settings, BBCodeLanguage)
                .around(EQUAL_SIGN).spaceIf(customSettings.SPACE_AROUND_EQUALITY_IN_ATTRIBUTE)
                .between(TAG_NAME, NOT_EMPTY_TAG_NAME_END).spaceIf(customSettings.SPACE_AFTER_TAG_NAME)
                .between(TAG_NAME, EMPTY_TAG_PREFIX_END).spaceIf(customSettings.SPACE_AFTER_TAG_NAME || customSettings.SPACE_INSIDE_EMPTY_TAG)
        }
    }

    private val spacingBuilder = createSpacingBuilder(settings)

    override fun buildChildren(): List<Block> {
        val children = mutableListOf<Block>()
        val childNodes = myNode.getChildren(null)
        childNodes.forEach { node ->
            if (node.elementType != TokenType.WHITE_SPACE) {
                children += BBCodeBlock(node, settings)
            }
        }
        return children
    }

    override fun getIndent(): Indent? {
        val elementType = myNode.elementType
        val parentElementType = myNode.treeParent?.elementType
        return when {
            elementType == TAG_PREFIX_START -> Indent.getNoneIndent()
            elementType == TAG_SUFFIX_START -> Indent.getNoneIndent()
            parentElementType == TAG -> Indent.getNormalIndent()
            else -> Indent.getNoneIndent()
        }
    }

    override fun getChildIndent(): Indent? {
        val elementType = myNode.elementType
        return when {
            elementType is IFileElementType -> Indent.getNoneIndent()
            elementType == TAG -> Indent.getNormalIndent()
            // Top-level elements (direct children of file root) must explicitly return
            // no indent; returning null lets IntelliJ fall back to a default strategy
            // that can add unwanted indent on Enter in plain-text content.
            // Inside tags, null is correct — it inherits the tag's child indent.
            else -> if (myNode.treeParent?.elementType is IFileElementType) Indent.getNoneIndent() else null
        }
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        return spacingBuilder.getSpacing(this, child1, child2)
    }

    override fun isLeaf(): Boolean {
        return myNode.firstChildNode == null
    }
}
