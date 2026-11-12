package app.olauncher.focus

import app.olauncher.helper.appLabelMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerSearchTest {

    @Test
    fun `tiers from exact to subsequence`() {
        assertEquals(MatchTier.EXACT, fuzzyMatchTier("Maps", "maps"))
        assertEquals(MatchTier.EXACT, fuzzyMatchTier("Google Maps", "googlemaps"))
        assertEquals(MatchTier.PREFIX, fuzzyMatchTier("Google Maps", "goo"))
        assertEquals(MatchTier.WORD_PREFIX, fuzzyMatchTier("Google Maps", "map"))
        assertEquals(MatchTier.SUBSTRING, fuzzyMatchTier("Google Maps", "ogle"))
        assertEquals(MatchTier.INITIALS, fuzzyMatchTier("Google Maps", "gm"))
        assertEquals(MatchTier.INITIALS, fuzzyMatchTier("Google Maps", "gmaps"))
        assertEquals(MatchTier.SUBSEQUENCE, fuzzyMatchTier("Telegram", "tlgm"))
    }

    @Test
    fun `no match`() {
        assertNull(fuzzyMatchTier("Camera", "xyz"))
        assertNull(fuzzyMatchTier("Camera", "   "))
        assertNull(fuzzyMatchTier("", "a"))
    }

    @Test
    fun `short queries do not use loose subsequence matching`() {
        // "cr" is a subsequence of "Camera" but too short to be meaningful
        assertNull(fuzzyMatchTier("Camera", "cr"))
        assertEquals(MatchTier.SUBSEQUENCE, fuzzyMatchTier("Camera", "cmr"))
    }

    @Test
    fun `initials need at least two words`() {
        assertNull(fuzzyMatchTier("Gmail", "gl"))
    }

    @Test
    fun `diacritics and case are ignored`() {
        assertEquals(MatchTier.EXACT, fuzzyMatchTier("Café", "CAFE"))
    }

    @Test
    fun `everything upstream matched still matches at substring tier or better`() {
        val cases = listOf(
            "Camera" to "cam", "Camera" to "  cam  ", "Café" to "cafe", "Cafe" to "café",
            "Google Maps" to "googlemaps", "Google-Maps" to "google maps", "Sam's App" to "sams app",
            "WhatsApp" to "sapp",
        )
        for ((label, query) in cases) {
            assertTrue("$label / $query", appLabelMatches(label, query))
            val tier = fuzzyMatchTier(label, query)
            assertTrue("$label / $query -> $tier", tier != null && tier >= MatchTier.SUBSTRING)
        }
    }

    private data class Item(val label: String, val launches: Int)

    private fun rank(items: List<Item>, q: String?) = searchAndRank(items, q, { it.label }, { it.launches }).map { it.label }

    @Test
    fun `blank query sorts by usage keeping alphabetical order for ties`() {
        val items = listOf(Item("Alpha", 0), Item("Bravo", 5), Item("Charlie", 0), Item("Delta", 9))
        assertEquals(listOf("Delta", "Bravo", "Alpha", "Charlie"), rank(items, ""))
        assertEquals(listOf("Delta", "Bravo", "Alpha", "Charlie"), rank(items, null))
    }

    @Test
    fun `better tier beats more launches, launches break ties within a tier`() {
        val items = listOf(Item("Mail", 1), Item("Gmail", 50), Item("Maps", 10), Item("Messages", 3))
        // "ma": Mail and Maps are prefixes (Maps launched more), Gmail is a substring
        assertEquals(listOf("Maps", "Mail", "Gmail"), rank(items, "ma"))
    }

    @Test
    fun `scrubber letters`() {
        assertEquals('C', scrubberLetter("camera"))
        assertEquals('E', scrubberLetter("Écran"))
        assertEquals('#', scrubberLetter("2FA"))
        assertEquals('#', scrubberLetter(""))
        assertEquals(listOf('A', 'C', 'Z', '#'), scrubberLetters(listOf("zoom", "1Password", "Alpha", "", "chrome", "Calc")))
    }
}
