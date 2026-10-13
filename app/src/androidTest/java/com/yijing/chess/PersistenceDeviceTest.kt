package com.yijing.chess

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.yijing.chess.model.*
import com.yijing.chess.parser.NotationCodec
import com.yijing.chess.session.*
import com.yijing.chess.storage.*
import com.yijing.chess.ui.NativeFiles
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "qa-${UUID.randomUUID()}"
    private lateinit var repository: SessionRepository
    private lateinit var prefs: android.content.SharedPreferences

    @Before fun prepare() {
        repository = SessionRepository(context, name, name)
        prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    @After fun cleanup() {
        val directory = File(context.filesDir, name).canonicalFile
        check(directory.parentFile == context.filesDir.canonicalFile && directory.name.startsWith("qa-"))
        directory.deleteRecursively()
        prefs.edit().clear().commit()
    }

    @Test fun atomicSaveRestoreUndoAndDeletionAreConsistent() = runBlocking {
        val session = NotationCodec.importPgn("1.e4 e5 2.Nf3 *").annotate("QA persisted comment")
        repository.save(session)
        val restored = SessionRepository(context, name, name).load().sessions.single()
        assertEquals(session, restored)
        assertEquals(2, restored.undo().moveCount)
        repository.delete(session)
        repository.save(session.copy(revision = session.revision - 1))
        assertTrue(repository.load().sessions.isEmpty())
        assertEquals(session.id, repository.undoDelete()!!.id)
        assertEquals(1, repository.load().sessions.size)
    }

    @Test fun migrationIsIdempotentAndKeepsOriginalBackup() = runBlocking {
        val session = NotationCodec.importPgn("1.e4 *")
        val old = SavedGameState(GameType.CHESS, GameMode.PVP, boardState = session.current.position.state, turn = PlayerSide.BLACK,
            moveHistoryJson = Gson().toJson(session.path().mapNotNull { it.event?.move }), timestamp = 1234)
        prefs.edit().putString("save_CHESS", Gson().toJson(old)).commit()
        val migrated = repository.load().sessions.single()
        assertTrue(prefs.contains("save_CHESS"))
        assertEquals(1, migrated.moveCount)
        val newer = migrated.annotate("QA newer data")
        repository.save(newer)
        prefs.edit().remove("migrated_CHESS").commit()
        val afterRestart = SessionRepository(context, name, name).load().sessions.single()
        assertEquals(newer.current.comment, afterRestart.current.comment)
        assertEquals(newer.id, afterRestart.id)
    }

    @Test fun corruptFileIsReportedAndPreserved() = runBlocking {
        val source = File(File(context.filesDir, name), "broken.json")
        source.writeText("{broken", Charsets.UTF_8)
        val result = repository.load()
        assertTrue(result.sessions.isEmpty())
        assertEquals(1, result.warnings.size)
        assertEquals("{broken", source.readText())
    }

    @Test fun fileProviderRoundTripAndSizeLimit() {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, "$name.pgn").apply { writeText("placeholder") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        try {
            NativeFiles.write(context, uri, "[Event \"文件往返\"]\n1.e4 e5 *")
            assertEquals(2, NotationCodec.importText(NativeFiles.read(context, uri)).moveCount)
            file.writeText("x".repeat(2_000_001))
            assertTrue(runCatching { NativeFiles.read(context, uri) }.isFailure)
        } finally { file.delete() }
    }

    @Test fun sgfDeclaredChineseEncodingIsReadCorrectly() {
        val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, "$name.sgf")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        try {
            file.writeBytes("(;CA[GB2312]SZ[9]GN[围棋练习];B[aa])".toByteArray(java.nio.charset.Charset.forName("GB18030")))
            assertEquals("围棋练习", NotationCodec.importText(NativeFiles.read(context, uri)).title)
        } finally { file.delete() }
    }
}
