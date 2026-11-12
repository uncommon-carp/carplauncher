package app.olauncher.smoke

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.focus.LaunchRepository
import app.olauncher.focus.appKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Starts the real activity and walks the screens the focus features touch, so a layout,
 * binding or wiring mistake fails here rather than only on a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FocusSmokeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun skipFirstRunFlows() {
        Prefs(context).apply {
            firstOpen = false
            firstSettingsOpen = false
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun withActivity(block: (MainActivity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity(block)
        }
    }

    private fun MainActivity.navigate(action: Int) {
        findNavController(R.id.nav_host_fragment).navigate(action)
        idle()
    }

    @Test
    fun `home renders the frequent apps row`() = withActivity { activity ->
        val row = activity.findViewById<View>(R.id.frequentAppsRow)
        assertNotNull(row)
        // No launches recorded yet, so nothing to show
        assertEquals(View.GONE, row.visibility)
    }

    @Test
    fun `settings shows the focus card`() = withActivity { activity ->
        activity.navigate(R.id.action_mainFragment_to_settingsFragment)
        assertNotNull(activity.findViewById<View>(R.id.frequentAppsNum))
        assertNotNull(activity.findViewById<View>(R.id.frequentAppsWindow))
    }

    @Test
    fun `app drawer opens`() = withActivity { activity ->
        activity.navigate(R.id.action_mainFragment_to_appListFragment)
        assertNotNull(activity.findViewById<View>(R.id.recyclerView))
    }

    @Test
    fun `launch log round-trips through Room`() = runBlocking {
        val repo = LaunchRepository(context)
        val now = System.currentTimeMillis()
        repo.record("com.example.a", "UserHandle{0}", now)
        repo.record("com.example.a", "UserHandle{0}", now)
        repo.record("com.example.b", "UserHandle{0}", now)
        val stats = repo.statsSince(now - 1000)
        assertEquals(2, stats.getValue(appKey("com.example.a", "UserHandle{0}")).count)
        assertEquals(1, repo.countSince("com.example.b", "UserHandle{0}", now - 1000))
    }
}
