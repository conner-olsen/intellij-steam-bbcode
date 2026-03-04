package icu.windea.bbcode.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns.*
import icu.windea.bbcode.*
import icu.windea.bbcode.psi.*
import icu.windea.bbcode.psi.BBCodeTypes.*

class BBCodeCompletionContributor : CompletionContributor() {
    private val tagSuffixPattern = psiElement().withElementType(TAG_NAME).afterLeaf("[/")
        .withParent(BBCodeTag::class.java)
    private val tagNamePattern = psiElement()
    private val attributeNamePattern = psiElement().withElementType(ATTRIBUTE_NAME)
        .withParent(BBCodeAttribute::class.java)

    init {
        extend(CompletionType.BASIC, tagSuffixPattern, BBCodeTagSuffixCompletionProvider())
        extend(CompletionType.BASIC, tagNamePattern, BBCodeTagNameCompletionProvider())
        extend(CompletionType.BASIC, attributeNamePattern, BBCodeAttributeNameCompletionProvider())
    }

    override fun beforeCompletion(context: CompletionInitializationContext) {
        context.dummyIdentifier = BBCodeConstants.dummyIdentifier
    }

    @Suppress("RedundantOverride")
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        super.fillCompletionVariants(parameters, result)
    }
}
