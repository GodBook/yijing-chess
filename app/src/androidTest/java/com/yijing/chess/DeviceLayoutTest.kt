package com.yijing.chess

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yijing.chess.model.*
import com.yijing.chess.session.GameSession
import com.yijing.chess.storage.AppPreferences
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class DeviceLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = compose.activity.model
    private val owned = mutableSetOf<String>()
    private var originalFont = "1.0"
    private var originalDensity: String? = null
    private lateinit var originalPreferences: AppPreferences

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText().trim() }
    }

    @Before fun prepare() {
        compose.waitUntil(15_000) { model.state.value.loaded }
        originalFont = shell("settings get system font_scale")
        originalDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        originalPreferences = model.state.value.preferences
        compose.runOnUiThread { model.updatePreferences(model.state.value.preferences.copy(onboardingComplete = true, sound = false, haptics = false)) }
    }

    @After fun cleanup() {
        shell("settings put system font_scale $originalFont")
        shell(if (originalDensity == null) "wm density reset" else "wm density $originalDensity")
        compose.runOnUiThread {
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            model.state.value.sessions.filter { it.id in owned }.forEach(model::delete)
            model.updatePreferences(originalPreferences)
        }
        compose.waitUntil(15_000) { model.state.value.sessions.none { it.id in owned } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull("Device screenshot unavailable", bitmap)
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "qa-layout").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun compactScreensAndLargeFontsKeepBoardAndActionsReachable() {
        for ((density, font, width) in listOf(Triple(480, "1.3", 360), Triple(540, "2.0", 320))) {
            shell("wm density $density"); shell("settings put system font_scale $font")
            compose.waitUntil(15_000) { kotlin.math.abs(compose.activity.resources.configuration.fontScale - font.toFloat()) < 0.01f }
            for (type in GameType.entries) {
                val session = GameSession.create(type, GameMode.PVP, 19).rename("QA-layout-${type.name}")
                owned.add(session.id)
                compose.runOnUiThread { model.open(session) }
                for ((orientation, name) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "portrait", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "landscape")) {
                    compose.runOnUiThread { compose.activity.requestedOrientation = orientation }
                    val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                    compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == expected }
                    compose.onNodeWithTag("board").assertIsDisplayed()
                    compose.onNodeWithTag("undo").assertIsDisplayed()
                    compose.onNodeWithTag("hint").assertIsDisplayed()
                    val bounds = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot
                    assertTrue("Board must keep usable area", bounds.width > 150 && bounds.height > 150)
                    capture("${type.name}-${width}-font${font.replace(".", "")}-${name}")
                }
            }
            val scoring = GameSession.create(GameType.GO, GameMode.PVP, 9).pass().pass().rename("QA-layout-scoring")
            owned.add(scoring.id)
            compose.runOnUiThread { model.open(scoring); compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            compose.onNodeWithTag("confirm_score").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("resume_go").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("board").assertIsDisplayed()
            capture("GO-scoring-${width}-font${font.replace(".", "")}")
        }
    }
}
