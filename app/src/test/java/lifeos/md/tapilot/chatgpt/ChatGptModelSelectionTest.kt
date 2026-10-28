package lifeos.md.tapilot.chatgpt

import org.junit.Assert.*
import org.junit.Test

class ChatGptModelSelectionTest {
    @Test fun defaultsToTerraAndPreservesExplicitSelection() {
        val models = listOf("gpt-6-luna", "gpt-6.1-terra", "gpt-6.1-sol")
        assertEquals("gpt-6.1-terra", ChatGptModelSelection.select(models))
        assertEquals("gpt-6.1-terra", ChatGptModelSelection.select(models, "retired-model"))
        assertEquals("gpt-6-luna", ChatGptModelSelection.select(models, "gpt-6-luna"))
    }
    @Test fun choosesLatestAvailableLunaRegardlessOfCatalogOrder() {
        assertEquals("gpt-6-luna", ChatGptModelSelection.select(listOf("gpt-6.1-sol", "gpt-6-luna", "gpt-6-astra")))
        assertEquals("gpt-6.10-luna", ChatGptModelSelection.select(listOf("gpt-6.2-luna", "gpt-6.10-luna", "gpt-6-luna")))
        assertEquals("gpt-7-luna", ChatGptModelSelection.select(listOf("gpt-6.10-luna", "gpt-7-luna")))
    }
    @Test fun prefersRollingAliasAndPreservesExplicitSelection() {
        val models = listOf("gpt-6-luna-2026-10-01", "gpt-6-luna", "gpt-6.1-sol")
        assertEquals("gpt-6-luna", ChatGptModelSelection.select(models))
        assertEquals("gpt-6.1-sol", ChatGptModelSelection.select(models, "gpt-6.1-sol"))
        assertEquals("gpt-6-luna", ChatGptModelSelection.select(models, "retired-model"))
    }
    @Test fun neverInventsAnUnavailableModel() {
        assertEquals("gpt-6.1-sol", ChatGptModelSelection.select(listOf("gpt-6.1-sol", "gpt-6-astra")))
        assertThrows(IllegalArgumentException::class.java) { ChatGptModelSelection.select(emptyList()) }
    }
}
