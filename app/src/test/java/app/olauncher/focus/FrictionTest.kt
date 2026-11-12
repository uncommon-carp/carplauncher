package app.olauncher.focus

import app.olauncher.focus.FrictionDecision.Allow
import app.olauncher.focus.FrictionDecision.Challenge
import app.olauncher.focus.FrictionDecision.Refuse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class FrictionTest {
    private val defaults = FrictionSettings()

    @Test
    fun `apps that are not flagged are never gated`() {
        assertEquals(Allow, frictionDecision(false, FrictionSettings(dailyCap = 1), launchesToday = 99))
    }

    @Test
    fun `default is a 10 second countdown with no cap`() {
        assertEquals(Challenge(10, askWhy = false, launchesLeftToday = null), frictionDecision(true, defaults, 0))
    }

    @Test
    fun `cap counts down and refuses once reached`() {
        val settings = FrictionSettings(delaySeconds = 5, askWhy = true, dailyCap = 3)
        assertEquals(Challenge(5, true, 2), frictionDecision(true, settings, 0))
        assertEquals(Challenge(5, true, 0), frictionDecision(true, settings, 2))
        assertEquals(Refuse(3), frictionDecision(true, settings, 3))
        assertEquals(Refuse(3), frictionDecision(true, settings, 7))
    }

    @Test
    fun `no delay and no question means only the cap applies`() {
        val settings = FrictionSettings(delaySeconds = 0, askWhy = false, dailyCap = 2)
        assertEquals(Allow, frictionDecision(true, settings, 1))
        assertEquals(Refuse(2), frictionDecision(true, settings, 2))
    }

    @Test
    fun `question alone still challenges`() {
        assertEquals(Challenge(0, true, null), frictionDecision(true, FrictionSettings(0, true, 0), 0))
    }

    @Test
    fun `negative values are treated as off`() {
        assertEquals(Allow, frictionDecision(true, FrictionSettings(-5, false, -1), 100))
    }

    @Test
    fun `start of local day`() {
        val utc = TimeZone.getTimeZone("UTC")
        val noon = 1_700_000_000_000L - (1_700_000_000_000L % 86_400_000L) + 12 * 3_600_000L
        assertEquals(noon - 12 * 3_600_000L, startOfLocalDay(noon, utc))
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo") // UTC+9: 12:00 UTC is 21:00 local
        assertEquals(noon - 21 * 3_600_000L, startOfLocalDay(noon, tokyo))
    }

    @Test
    fun `start of local day across a DST change`() {
        val ny = TimeZone.getTimeZone("America/New_York")
        // 2026-03-08 15:00 UTC = 11:00 EDT, after the 02:00 spring-forward; midnight was EST (05:00 UTC)
        val afterSpringForward = 1_772_982_000_000L
        assertEquals(1_772_946_000_000L, startOfLocalDay(afterSpringForward, ny))
    }

    @Test
    fun `shortcuts inherit the flag of their package and profile`() {
        val flagged = setOf(appKey("com.social", "UserHandle{0}"))
        assertTrue(isDistracting(flagged, "com.social", "UserHandle{0}"))
        assertFalse(isDistracting(flagged, "com.social", "UserHandle{10}"))
    }
}
