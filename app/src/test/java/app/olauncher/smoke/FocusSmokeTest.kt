package app.olauncher.smoke

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.olauncher.MainActivity
import app.olauncher.MainViewModel
import app.olauncher.data.AppModel
import app.olauncher.ui.AppDrawerAdapter
import app.olauncher.R
import app.olauncher.data.Prefs
import android.widget.TextView
import app.olauncher.focus.DEFAULT_MODE_ID
import app.olauncher.focus.LaunchRepository
import app.olauncher.focus.ModeManager
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

    private fun app(label: String) = AppModel.App(
        appLabel = label, key = null, appPackage = "pkg.${label.lowercase()}",
        activityClassName = null, user = android.os.Process.myUserHandle(),
    )

    private fun MainActivity.drawerLabels(): List<String> {
        val adapter = findViewById<RecyclerView>(R.id.recyclerView).adapter as AppDrawerAdapter
        return adapter.currentList.map { it.appLabel }.filter { it.isNotEmpty() }
    }

    private fun tap(view: View, y: Float) {
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 5f, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 5f, y, 0))
        idle()
    }

    @Test
    fun `drawer is usage sorted and the scrubber filters by letter`() = withActivity { activity ->
        activity.navigate(R.id.action_mainFragment_to_appListFragment)
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        // Let the real (empty) app list load first so it can't overwrite ours
        repeat(50) { if (viewModel.appList.value == null) { Thread.sleep(20); idle() } }
        val user = android.os.Process.myUserHandle().toString()
        viewModel.launchCounts.value = mapOf(appKey("pkg.beta", user) to 5, appKey("pkg.charlie", user) to 1)
        viewModel.appList.value = listOf(app("Alpha"), app("Beta"), app("Bravo"), app("Charlie"))
        idle()
        assertEquals(listOf("Beta", "Charlie", "Alpha", "Bravo"), activity.drawerLabels())

        val scrubber = activity.findViewById<LinearLayout>(R.id.letterScrubber)
        assertEquals(3, scrubber.childCount) // A, B, C
        scrubber.measure(
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        )
        scrubber.layout(0, 0, 100, 300)
        val middle = 150f // "B"
        tap(scrubber, middle)
        assertEquals(listOf("Beta", "Bravo"), activity.drawerLabels())
        tap(scrubber, middle) // tapping the active letter clears it
        assertEquals(4, activity.drawerLabels().size)
    }

    @Test
    fun `modes keep separate favorites and the home label switches them`() {
        val prefs = Prefs(context)
        prefs.homeAppsNum = 2
        prefs.appName1 = "Mail"
        prefs.appPackage1 = "pkg.mail"
        val modes = ModeManager(context)
        val evening = modes.addMode("Evening")

        modes.switchManually(evening.id)
        assertEquals("Mail", prefs.appName1) // new modes start as a copy
        prefs.appName1 = "Books"
        prefs.appPackage1 = "pkg.books"
        prefs.homeAppsNum = 3

        modes.switchManually(DEFAULT_MODE_ID)
        assertEquals("Mail", prefs.appName1)
        assertEquals(2, prefs.homeAppsNum)

        modes.switchManually(evening.id)
        assertEquals("Books", prefs.appName1)
        assertEquals(3, prefs.homeAppsNum)

        withActivity { activity ->
            val label = activity.findViewById<TextView>(R.id.modeLabel)
            assertEquals(View.VISIBLE, label.visibility)
            assertEquals("Evening", label.text.toString())
            label.performClick() // cycles to the next mode
            idle()
            assertEquals("Default", label.text.toString())
            // (Home clears favorites whose package isn't installed, so check the mode, not the slot)
            assertEquals(DEFAULT_MODE_ID, ModeManager(context).activeMode.id)
        }

        modes.deleteMode(evening.id)
        assertEquals(1, modes.modes.size)
        assertEquals(DEFAULT_MODE_ID, modes.activeMode.id)
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
