package com.yijing.chess

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.yijing.chess.update.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL
import java.util.UUID

/** Requires scripts/qa-apk-server.mjs and adb reverse tcp:37123 tcp:37123. */
@RunWith(AndroidJUnit4::class)
class UpdateDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    private val managers = mutableListOf<UpdateManager>()
    private val server = "http://127.0.0.1:37123"
    private fun manager(path: String = "/api/latest", name: String = "qa-update-${UUID.randomUUID()}"): UpdateManager {
        names.add(name)
        return UpdateManager(context, name, server + path).also { managers.add(it) }
    }
    private fun fixture(path: String = "/fixture.apk"): UpdateInfo {
        val meta = JsonParser.parseString(URL("$server/meta").readText()).asJsonObject
        return UpdateInfo("v1.3.0", "1.3.0", "QA update", "QA fixture", server + path, meta["size"].asLong, meta["sha256"].asString)
    }
    @After fun cleanup() {
        managers.forEach { it.cancelDownload(); it.resetStatus() }
        Thread.sleep(250)
        managers.forEach(UpdateManager::close)
        names.distinct().forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun unavailableReleasesAreNotReportedAsLatest() = runBlocking {
        val missing = manager("/api/missing"); missing.checkForUpdates(true)
        assertTrue(missing.status.value is UpdateStatus.Information)
        val noApk = manager("/api/no-apk"); noApk.checkForUpdates(true)
        assertTrue(noApk.status.value is UpdateStatus.Information)
        val current = manager("/api/current"); current.checkForUpdates(true)
        assertTrue(current.status.value is UpdateStatus.UpToDate)
        val newer = manager(); newer.checkForUpdates(true)
        assertTrue(newer.status.value is UpdateStatus.UpdateAvailable)
    }

    @Test fun backgroundDownloadStaysHiddenAndValidatesPackage() = runBlocking {
        val update = manager()
        update.downloadApk(fixture("/slow.apk"))
        update.resetStatus()
        delay(1000)
        assertEquals(UpdateStatus.Idle, update.status.value)
        assertTrue(update.hasPending.value)
        update.showStatus()
        withTimeout(60_000) { while (update.status.value !is UpdateStatus.ReadyToInstall && update.status.value !is UpdateStatus.Error) delay(200) }
        assertTrue(update.status.value.toString(), update.status.value is UpdateStatus.ReadyToInstall)
        val ready = update.status.value as UpdateStatus.ReadyToInstall
        update.validateApk(java.io.File(ready.apkPath), ready.info)
    }

    @Test fun managerRecreationResumesSystemTask() = runBlocking {
        val name = "qa-update-${UUID.randomUUID()}"
        val first = manager(name = name)
        first.downloadApk(fixture("/slow.apk"))
        delay(500)
        first.close(); managers.remove(first)
        val restored = manager(name = name)
        restored.showStatus()
        withTimeout(60_000) { while (restored.status.value !is UpdateStatus.ReadyToInstall && restored.status.value !is UpdateStatus.Error) delay(200) }
        assertTrue(restored.status.value.toString(), restored.status.value is UpdateStatus.ReadyToInstall)
    }

    @Test fun corruptAndDigestMismatchedPackagesAreRejected() = runBlocking {
        for (info in listOf(fixture("/broken.apk").copy(apkSize = 7, sha256 = null), fixture().copy(sha256 = "0".repeat(64)))) {
            val update = manager()
            update.downloadApk(info)
            withTimeout(60_000) { while (update.status.value !is UpdateStatus.Error && update.status.value !is UpdateStatus.ReadyToInstall) delay(200) }
            assertTrue(update.status.value.toString(), update.status.value is UpdateStatus.Error)
            assertEquals(UpdateStage.DOWNLOAD, (update.status.value as UpdateStatus.Error).stage)
        }
    }
}
