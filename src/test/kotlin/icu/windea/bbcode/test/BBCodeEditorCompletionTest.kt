package icu.windea.bbcode.test

import com.intellij.codeInsight.*
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.*
import com.intellij.testFramework.fixtures.*
import icu.windea.bbcode.*

class BBCodeEditorCompletionTest : BasePlatformTestCase() {
    private var oldAutocompleteOnCodeCompletion = false

    override fun setUp() {
        super.setUp()
        defaultProject = project
        val settings = CodeInsightSettings.getInstance()
        oldAutocompleteOnCodeCompletion = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
    }

    override fun tearDown() {
        try {
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = oldAutocompleteOnCodeCompletion
        } finally {
            super.tearDown()
        }
    }

    fun testSuggestListInLineItemWithoutFollowingLineTag() {
        myFixture.configureByText(
            "steam-list-context.bbcode",
            """
                [olist]
                 [*][b]Feature 1:[/b] Feature 1 description
                  [list]
                   [*][b]Sub-Feature 1:[/b] Feature 1 sub-feature description
                   [*][b]Sub-Feature 2:[/b] Feature 1 sub-feature description
                  [/list]
                 [*][b]Feature 2:[/b] Feature 2 description
                 [*][b]...[/b]
                 [li<caret>
                [/olist]
            """.trimIndent()
        )

        val completionResults = myFixture.complete(CompletionType.BASIC).orEmpty()
        val variants = (myFixture.lookupElementStrings.orEmpty() + completionResults.map { it.lookupString }).toSet()
        assertTrue("Expected 'list' completion; got: $variants", "list" in variants)
    }

    fun testListCompletionExpandsWithContextIndent() {
        myFixture.configureByText(
            "nested-list-insert.bbcode",
            """
                [list]
                 [li<caret>
                [/list]
            """.trimIndent()
        )

        completeAndAccept("list")

        assertEquals(
            "[list]\n [list]\n  \n [/list]\n[/list]",
            myFixture.editor.document.text
        )
        assertEquals("[list]\n [list]\n  ".length, myFixture.editor.caretModel.offset)
    }

    fun testSuggestStarTagInsideList() {
        myFixture.configureByText(
            "star-tag-completion.bbcode",
            "[list]\n [*<caret>\n[/list]"
        )

        val completionResults = myFixture.complete(CompletionType.BASIC).orEmpty()
        val variants = (myFixture.lookupElementStrings.orEmpty() + completionResults.map { it.lookupString }).toSet()
        assertTrue("Expected '*' completion; got: $variants", "*" in variants)
    }

    fun testStarTagCompletionInsertsClosingBracketAndSpace() {
        myFixture.configureByText(
            "star-tag-insert.bbcode",
            "[list]\n [<caret>\n[/list]"
        )

        completeAndAccept("*")

        assertEquals("[list]\n [*] \n[/list]", myFixture.editor.document.text)
        assertEquals("[list]\n [*] ".length, myFixture.editor.caretModel.offset)
    }

    fun testEnterBetweenListTagsExpandsToMiddleLine() {
        myFixture.configureByText("enter-between-list-tags.bbcode", "[list]<caret>[/list]")

        myFixture.type('\n')

        assertEquals("[list]\n \n[/list]", myFixture.editor.document.text)
        assertEquals("[list]\n ".length, myFixture.editor.caretModel.offset)
    }

    private fun completeAndAccept(lookupString: String? = null) {
        val completionResults = myFixture.complete(CompletionType.BASIC).orEmpty()
        val lookup = myFixture.lookup
        if (lookup != null) {
            if (lookupString != null) {
                val item = lookup.items.firstOrNull { it.lookupString == lookupString }
                assertNotNull("Lookup item '$lookupString' not found. Actual: ${lookup.items.map { it.lookupString }}", item)
                lookup.currentItem = item
            }
            myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        } else if (lookupString != null) {
            assertTrue(
                "Expected completion '$lookupString'; got ${completionResults.map { it.lookupString }}",
                completionResults.any { it.lookupString == lookupString }
            )
        }
    }
}
