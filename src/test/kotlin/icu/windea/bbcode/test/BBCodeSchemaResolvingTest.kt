package icu.windea.bbcode.test

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.lang.annotation.*
import com.intellij.psi.*
import com.intellij.psi.util.*
import com.intellij.testFramework.fixtures.*
import icu.windea.bbcode.*
import icu.windea.bbcode.inspections.*
import icu.windea.bbcode.lang.schema.*

class BBCodeSchemaResolvingTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        defaultProject = project
    }

    fun testStandard() {
        val schema = BBCodeSchemaManager.getStandardSchema(project)
        assertNotNull(schema)
        schema!!
        assertEquals("https://www.bbcode.org/reference.php", schema.url)
        assertTrue(schema.tags.isNotEmpty())
    }

    fun testSteamListExtensionsInStandardSchema() {
        val schema = BBCodeSchemaManager.getStandardSchema(project)
        assertNotNull(schema)
        schema!!

        val olistTag = schema.tagMap["olist"]
        assertNotNull(olistTag)

        val listChildNames = schema.tagMap["list"]?.childNames
        assertNotNull(listChildNames)
        assertTrue("list" in listChildNames!!)
        assertTrue("ul" in listChildNames)
        assertTrue("ol" in listChildNames)
        assertTrue("olist" in listChildNames)

        val liParentNames = schema.tagMap["li"]?.parentNames
        assertNotNull(liParentNames)
        assertTrue("olist" in liParentNames!!)

        val shorthandParentNames = schema.tagMap["*"]?.parentNames
        assertNotNull(shorthandParentNames)
        assertTrue("olist" in shorthandParentNames!!)
    }

    fun testSteamWorkshopStyleListsHaveNoParserOrSchemaErrors() {
        val content = """
            [h1]Features:[/h1]
            [olist]
             [*][b]Feature 1:[/b] Feature 1 description
            	[list]
            	 [*][b]Sub-Feature 1:[/b] Feature 1 sub-feature description
            	 [*][b]Sub-Feature 2:[/b] Feature 1 sub-feature description
            	[/list]
             [*][b]Feature 2:[/b] Feature 2 description
             [*][b]...[/b]
            [/olist]

            [h1]Compatibility:[/h1]
            [list]
             [*]Not Ironman/Achievement Compatible.
             [*]Fully save game compatible.
            [/list]
        """.trimIndent()

        myFixture.configureByText("steam-workshop-snippet.bbcode", content)
        val parserErrors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        assertTrue(
            "Parser errors:\n${parserErrors.joinToString("\n") {
                val range = it.textRange
                val snippetStart = (range.startOffset - 20).coerceAtLeast(0)
                val snippetEnd = (range.endOffset + 20).coerceAtMost(content.length)
                val snippet = content.substring(snippetStart, snippetEnd).replace("\n", "\\n")
                "${it.errorDescription} @ ${range.startOffset}-${range.endOffset}; text='${it.text}'; around='$snippet'"
            }}",
            parserErrors.isEmpty(),
        )

        myFixture.enableInspections(BBCodeSchemaValidationInspection())
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        assertTrue(
            "Highlight errors:\n${errors.joinToString("\n") { "${it.description} @ ${it.text}" }}",
            errors.isEmpty(),
        )
    }
}
