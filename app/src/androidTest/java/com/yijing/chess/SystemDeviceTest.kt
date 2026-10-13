package com.yijing.chess

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.yijing.chess.chess.ChessAI
import com.yijing.chess.chess.ChessBoard
import com.yijing.chess.model.PlayerSide
import com.yijing.chess.update.*
import com.yijing.chess.xiangqi.XiangqiAI
import com.yijing.chess.xiangqi.XiangqiBoard
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SystemDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation
    private fun shell(command: String) = automation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
    }
    private suspend fun waitFor(label: String, predicate: () -> Boolean) {
        try { withTimeout(15_000) { while (!predicate()) delay(100) } }
        catch (error: TimeoutCancellationException) {
            throw AssertionError("$label; active package=${automation.rootInActiveWindow?.packageName}", error)
        }
    }
    private fun capture(name: String) {
        val bitmap = automation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun permissionToggle(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isCheckable && node.isEnabled) return node
        for (index in 0 until node.childCount) permissionToggle(node.getChild(index))?.let { return it }
        return null
    }
    private fun hasAppLabel(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.text?.contains("弈境") == true) return true
        return (0 until node.childCount).any { hasAppLabel(node.getChild(it)) }
    }

    @Test fun deniedInstallPermissionCanBeRetriedThroughNativeInstaller(): Unit = runBlocking {
        val name = "qa-native-installer-${UUID.randomUUID()}"
        val update = UpdateManager(context, name)
        try {
            assertFalse("Use scripts/RunDeviceTests.ps1 to prepare and restore the native install permission", context.packageManager.canRequestPackageInstalls())
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val meta = JsonParser.parseString(URL("http://127.0.0.1:37123/meta").readText()).asJsonObject
            update.downloadApk(UpdateInfo("v1.3.0", "1.3.0", "QA", "QA", "http://127.0.0.1:37123/fixture.apk", meta["size"].asLong, meta["sha256"].asString))
            waitFor("download ready") { update.status.value is UpdateStatus.ReadyToInstall || update.status.value is UpdateStatus.Error }
            val ready = update.status.value as UpdateStatus.ReadyToInstall
            update.installApk(ready.apkPath)
            waitFor("permission settings opened") { automation.rootInActiveWindow?.packageName == "com.android.settings" }
            capture("qa-install-permission")
            shell("input keyevent KEYCODE_BACK")
            waitFor("returned after denying permission") { automation.rootInActiveWindow?.packageName == context.packageName }
            update.onResume()
            assertTrue(update.status.value is UpdateStatus.InstallPermission)
            assertTrue(File(ready.apkPath).exists())
            update.installApk(ready.apkPath)
            waitFor("permission retry settings") { permissionToggle(automation.rootInActiveWindow) != null }
            var toggle = requireNotNull(permissionToggle(automation.rootInActiveWindow))
            while (!toggle.isClickable) toggle = requireNotNull(toggle.parent)
            assertTrue(toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitFor("permission granted") { context.packageManager.canRequestPackageInstalls() }
            shell("input keyevent KEYCODE_BACK")
            waitFor("returned after granting permission") { automation.rootInActiveWindow?.packageName == context.packageName }
            update.onResume()
            println("Installer state: ${update.status.value}")
            waitFor("native installer opened: ${update.status.value}") {
                val root = automation.rootInActiveWindow
                root?.packageName?.toString()?.contains("packageinstaller") == true && hasAppLabel(root)
            }
            automation.waitForIdle(100, 3000)
            capture("qa-native-installer")
            assertTrue(update.status.value is UpdateStatus.ReadyToInstall)
            shell("input keyevent KEYCODE_BACK")
        } finally {
            update.cancelDownload(); delay(250); update.close()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun searchBudgetsAndCancellationHaveDeviceMeasurements() = runBlocking {
        val measurements = linkedMapOf<String, List<Long>>()
        for (type in listOf("chess", "xiangqi")) {
            suspend fun search(budget: Long) {
                if (type == "chess") ChessAI.findBestMove(ChessBoard.initialBoard(), PlayerSide.WHITE, 3, budget)
                else XiangqiAI.findBestMove(XiangqiBoard.initialBoard(), PlayerSide.RED, 3, budget)
            }
            val elapsed = mutableListOf<Long>()
            repeat(10) {
                val started = System.nanoTime()
                search(250)
                elapsed.add((System.nanoTime() - started) / 1_000_000)
            }
            val cancellations = mutableListOf<Long>()
            repeat(20) {
                val entered = CompletableDeferred<Unit>()
                val task = launch(Dispatchers.Default) { entered.complete(Unit); search(30_000) }
                entered.await(); delay(50)
                assertTrue("Search must be active when cancellation is measured", task.isActive)
                val started = System.nanoTime()
                task.cancelAndJoin()
                cancellations.add((System.nanoTime() - started) / 1_000_000)
            }
            measurements["$type-budget250ms"] = elapsed
            measurements["$type-cancel"] = cancellations
            assertTrue("Search budget grossly exceeded", elapsed.max() < 1500)
            assertTrue("Cancellation did not complete promptly", cancellations.max() < 1000)
        }
        File(context.getExternalFilesDir(null), "qa-performance.json").writeText(Gson().toJson(measurements))
    }
}
