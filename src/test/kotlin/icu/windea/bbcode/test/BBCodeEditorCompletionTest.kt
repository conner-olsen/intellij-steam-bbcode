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

    // ── Real-world file: exact content from user's change-notes.bbcode ──

    private val realWorldFile = """
        [list]
         [*][b]New Multiplayer Specific Mode:[/b]
         [list]
          [*] Only enable unrestricted land transfer for the host
          [*] New ability to trade land between players
          [*] Block land transfers between players that are not in the same war
         [/list]
         [*] Now requires the [url=TODO]Community Mod Framework[/url] (to detect the host player)
        [/list]
    """.trimIndent()

    // ── [*] suggestion tests ──

    fun testSuggestStarInSimpleList() {
        configureText("[list]\n [*<caret>\n[/list]")
        assertCompletionContains("*")
    }

    fun testSuggestStarFromOpenBracketInList() {
        configureText("[list]\n [<caret>\n[/list]")
        assertCompletionContains("*", "li", "list")
    }

    fun testSuggestStarInOlist() {
        configureText("[olist]\n [*<caret>\n[/olist]")
        assertCompletionContains("*")
    }

    fun testSuggestStarInUl() {
        configureText("[ul]\n [*<caret>\n[/ul]")
        assertCompletionContains("*")
    }

    fun testSuggestStarInOl() {
        configureText("[ol]\n [*<caret>\n[/ol]")
        assertCompletionContains("*")
    }

    fun testSuggestStarAfterNestedListCloses() {
        configureText(
            """
                [list]
                 [*][b]Feature:[/b]
                 [list]
                  [*] Sub-feature 1
                  [*] Sub-feature 2
                 [/list]
                 [*<caret>
                [/list]
            """.trimIndent()
        )
        assertCompletionContains("*")
    }

    fun testSuggestStarInRealWorldFileAfterSublist() {
        // Cursor after [/list] of the inner list, typing [* for a new outer bullet
        configureText(
            """
                [list]
                 [*][b]New Multiplayer Specific Mode:[/b]
                 [list]
                  [*] Only enable unrestricted land transfer for the host
                  [*] New ability to trade land between players
                  [*] Block land transfers between players that are not in the same war
                 [/list]
                 [*<caret>
                [/list]
            """.trimIndent()
        )
        assertCompletionContains("*")
    }

    fun testSuggestStarFromBracketInRealWorldFile() {
        configureText(
            """
                [list]
                 [*][b]New Multiplayer Specific Mode:[/b]
                 [list]
                  [*] Only enable unrestricted land transfer for the host
                  [*] New ability to trade land between players
                  [*] Block land transfers between players that are not in the same war
                 [/list]
                 [<caret>
                [/list]
            """.trimIndent()
        )
        assertCompletionContains("*", "li", "list")
    }

    fun testSuggestStarBetweenExistingBullets() {
        configureText(
            """
                [list]
                 [*] First item
                 [*<caret>
                 [*] Third item
                [/list]
            """.trimIndent()
        )
        assertCompletionContains("*")
    }

    fun testSuggestStarInEmptyList() {
        configureText("[list]\n [*<caret>\n[/list]")
        assertCompletionContains("*")
    }

    fun testSuggestListChildrenInNestedContext() {
        // All list child types should be suggested
        configureText("[list]\n [<caret>\n[/list]")
        assertCompletionContains("*", "li", "list", "ul", "ol", "olist")
    }

    // ── [*] insert handler tests ──

    fun testStarCompletionInsertsClosingBracketAndSpace() {
        configureText("[list]\n [<caret>\n[/list]")
        completeAndAccept("*")
        assertDocEquals("[list]\n [*] \n[/list]")
        assertCaretAt("[list]\n [*] ".length)
    }

    fun testStarCompletionAfterNestedListInsertsCorrectly() {
        configureText(
            """
                [list]
                 [*][b]Feature:[/b]
                 [list]
                  [*] Sub-feature
                 [/list]
                 [<caret>
                [/list]
            """.trimIndent()
        )
        completeAndAccept("*")
        assertDocEquals("[list]\n [*][b]Feature:[/b]\n [list]\n  [*] Sub-feature\n [/list]\n [*] \n[/list]")
    }

    fun testStarCompletionInRealWorldFileInsertsCorrectly() {
        configureText(
            """
                [list]
                 [*][b]New Multiplayer Specific Mode:[/b]
                 [list]
                  [*] Only enable unrestricted land transfer for the host
                  [*] New ability to trade land between players
                  [*] Block land transfers between players that are not in the same war
                 [/list]
                 [<caret>
                [/list]
            """.trimIndent()
        )
        completeAndAccept("*")
        assertTrue(
            "Document should contain ' [*] ' after inner [/list]",
            myFixture.editor.document.text.contains("[/list]\n [*] \n[/list]")
        )
    }

    fun testStarCompletionWhenExistingClosingBracketPresent() {
        // If ] already exists after *, just move past it and add space
        configureText("[list]\n [*<caret>]\n[/list]")
        completeAndAccept("*")
        assertDocEquals("[list]\n [*] \n[/list]")
    }

    // ── [list] completion expansion tests ──

    fun testListCompletionExpandsWithContextIndent() {
        configureText("[list]\n [li<caret>\n[/list]")
        completeAndAccept("list")
        assertDocEquals("[list]\n [list]\n  [\n [/list]\n[/list]")
        assertCaretAt("[list]\n [list]\n  [".length)
    }

    fun testListCompletionAfterNestedListUsesOuterIndent() {
        configureText(
            """
                [list]
                 [*][b]Feature:[/b]
                 [list]
                  [*] Sub-feature
                 [/list]
                 [li<caret>
                [/list]
            """.trimIndent()
        )
        completeAndAccept("list")
        // New [list] at 1-space indent should expand with 2-space child indent, NOT 3-space
        assertDocEquals("[list]\n [*][b]Feature:[/b]\n [list]\n  [*] Sub-feature\n [/list]\n [list]\n  [\n [/list]\n[/list]")
        assertCaretAt("[list]\n [*][b]Feature:[/b]\n [list]\n  [*] Sub-feature\n [/list]\n [list]\n  [".length)
    }

    fun testSuggestListInLineItemWithoutFollowingLineTag() {
        configureText(
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
        assertCompletionContains("list")
    }

    // ── Enter key expansion tests ──

    fun testEnterBetweenEmptyListTagsExpandsBody() {
        configureText("[list]<caret>[/list]")
        myFixture.type('\n')
        assertDocEquals("[list]\n [\n[/list]")
        assertCaretAt("[list]\n [".length)
    }

    fun testEnterBetweenEmptyIndentedListTagsExpandsBody() {
        configureText(" [list]<caret>[/list]")
        myFixture.type('\n')
        assertDocEquals(" [list]\n  [\n [/list]")
        assertCaretAt(" [list]\n  [".length)
    }

    fun testEnterBetweenEmptyOlistTagsExpandsBody() {
        configureText("[olist]<caret>[/olist]")
        myFixture.type('\n')
        assertDocEquals("[olist]\n [\n[/olist]")
        assertCaretAt("[olist]\n [".length)
    }

    fun testEnterBetweenEmptyUlTagsExpandsBody() {
        configureText("[ul]<caret>[/ul]")
        myFixture.type('\n')
        assertDocEquals("[ul]\n [\n[/ul]")
        assertCaretAt("[ul]\n [".length)
    }

    // ── Enter after content inside list container ──

    fun testEnterAfterBulletContentInsertsTagOpen() {
        configureText("[list]\n [*] test<caret>\n[/list]")
        myFixture.type('\n')
        assertDocEquals("[list]\n [*] test\n [\n[/list]")
        assertCaretAt("[list]\n [*] test\n [".length)
    }

    fun testEnterAfterBulletContentInIndentedList() {
        configureText(" [list]\n  [*] test<caret>\n [/list]")
        myFixture.type('\n')
        assertDocEquals(" [list]\n  [*] test\n  [\n [/list]")
        assertCaretAt(" [list]\n  [*] test\n  [".length)
    }

    fun testEnterAfterBulletInRealWorldFile() {
        configureText(
            """
                [list]
                 [*][b]New Multiplayer Specific Mode:[/b]<caret>
                 [list]
                  [*] Only enable unrestricted land transfer for the host
                  [*] New ability to trade land between players
                  [*] Block land transfers between players that are not in the same war
                 [/list]
                 [*] Now requires the [url=TODO]Community Mod Framework[/url] (to detect the host player)
                [/list]
            """.trimIndent()
        )
        myFixture.type('\n')
        val text = myFixture.editor.document.text
        val caretOffset = myFixture.editor.caretModel.offset
        // New line should have [ at the outer list's child indent
        val caretLine = myFixture.editor.document.getLineNumber(caretOffset)
        val lineStart = myFixture.editor.document.getLineStartOffset(caretLine)
        val lineContent = text.substring(lineStart, myFixture.editor.document.getLineEndOffset(caretLine))
        assertEquals("New line should be ' ['", " [", lineContent)
    }

    fun testEnterAfterLastBulletInRealWorldFile() {
        configureText(
            """
                [list]
                 [*][b]New Multiplayer Specific Mode:[/b]
                 [list]
                  [*] Only enable unrestricted land transfer for the host
                  [*] New ability to trade land between players
                  [*] Block land transfers between players that are not in the same war
                 [/list]
                 [*] Now requires the [url=TODO]Community Mod Framework[/url] (to detect the host player)<caret>
                [/list]
            """.trimIndent()
        )
        myFixture.type('\n')
        val text = myFixture.editor.document.text
        val caretOffset = myFixture.editor.caretModel.offset
        val caretLine = myFixture.editor.document.getLineNumber(caretOffset)
        val lineStart = myFixture.editor.document.getLineStartOffset(caretLine)
        val lineContent = text.substring(lineStart, myFixture.editor.document.getLineEndOffset(caretLine))
        assertEquals("New line should be ' ['", " [", lineContent)
    }

    fun testEnterAfterBulletInInnerList() {
        configureText(
            """
                [list]
                 [*][b]Feature:[/b]
                 [list]
                  [*] Sub-feature<caret>
                 [/list]
                [/list]
            """.trimIndent()
        )
        myFixture.type('\n')
        val text = myFixture.editor.document.text
        val caretOffset = myFixture.editor.caretModel.offset
        val caretLine = myFixture.editor.document.getLineNumber(caretOffset)
        val lineStart = myFixture.editor.document.getLineStartOffset(caretLine)
        val lineContent = text.substring(lineStart, myFixture.editor.document.getLineEndOffset(caretLine))
        // Inner list child indent is 2 spaces
        assertEquals("New line should be '  ['", "  [", lineContent)
    }

    fun testEnterInsideBoldTagDoesNotInsertTagOpen() {
        // Inside [b]...[/b] within a list — should NOT insert [
        configureText("[list]\n [*][b]bold<caret>[/b]\n[/list]")
        myFixture.type('\n')
        val text = myFixture.editor.document.text
        assertFalse(
            "Should not insert bare [ when inside [b] tag",
            text.contains("\n [\n") || text.contains("\n[\n")
        )
    }

    fun testEnterOnBareBracketDoesNotCascade() {
        // If previous line is just [, don't insert another [
        configureText("[list]\n [<caret>\n[/list]")
        myFixture.type('\n')
        val text = myFixture.editor.document.text
        val lines = text.split("\n")
        val bracketLines = lines.count { it.trim() == "[" }
        assertTrue("Should not have cascading [ lines (got $bracketLines)", bracketLines <= 1)
    }

    fun testStarTagAppearsFirstInCompletionList() {
        configureText("[list]\n [<caret>\n[/list]")
        myFixture.complete(CompletionType.BASIC)
        val lookup = myFixture.lookup
        assertNotNull("Lookup should be open", lookup)
        val items = lookup!!.items.map { it.lookupString }
        assertTrue("'*' should be in completions", "*" in items)
        assertEquals("'*' should be the first completion", "*", items[0])
    }

    // ── Helpers ──

    private fun configureText(text: String) {
        myFixture.configureByText("test.bbcode", text)
    }

    private fun assertDocEquals(expected: String) {
        assertEquals(expected, myFixture.editor.document.text)
    }

    private fun assertCaretAt(expectedOffset: Int) {
        assertEquals(expectedOffset, myFixture.editor.caretModel.offset)
    }

    private fun assertCompletionContains(vararg tagNames: String) {
        val completionResults = myFixture.complete(CompletionType.BASIC).orEmpty()
        val variants = (myFixture.lookupElementStrings.orEmpty() + completionResults.map { it.lookupString }).toSet()
        for (name in tagNames) {
            assertTrue("Expected '$name' completion; got: $variants", name in variants)
        }
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
