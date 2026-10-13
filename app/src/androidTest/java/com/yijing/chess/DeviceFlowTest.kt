package com.yijing.chess

import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yijing.chess.chess.*
import com.yijing.chess.model.*
import com.yijing.chess.parser.NotationCodec
import com.yijing.chess.session.*
import com.yijing.chess.storage.AppPreferences
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val owned = mutableSetOf<String>()
    private lateinit var originalPreferences: AppPreferences
    private val model get() = compose.activity.model

    @Before fun prepare() {
        compose.waitUntil(15_000) { model.state.value.loaded }
        originalPreferences = model.state.value.preferences
        compose.runOnUiThread { model.updatePreferences(originalPreferences.copy(onboardingComplete = true, sound = false, haptics = false)) }
        compose.waitForIdle()
    }

    @After fun cleanup() {
        compose.runOnUiThread {
            model.stopThinking()
            model.state.value.sessions.filter { it.id in owned }.forEach(model::delete)
            model.updatePreferences(originalPreferences)
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        compose.waitUntil(15_000) { model.state.value.sessions.none { it.id in owned } }
    }

    private fun open(session: GameSession) {
        val value = session.rename("QA-${session.gameType.name}-${session.id.take(6)}")
        owned.add(value.id)
        compose.runOnUiThread { model.open(value) }
        compose.waitForIdle()
    }

    private fun point(x: Int, y: Int) = compose.onNodeWithTag("point_${x}_$y", useUnmergedTree = true)

    @Test fun allThreeGamesContinueWithOneTapAndUndo() {
        for (type in GameType.entries) {
            open(GameSession.create(type, GameMode.PVP, 9))
            when (type) {
                GameType.CHESS -> { point(4, 6).performClick(); point(4, 4).performClick() }
                GameType.XIANGQI -> { point(0, 6).performClick(); point(0, 5).performClick() }
                GameType.GO -> point(4, 4).performClick()
            }
            compose.waitUntil(10_000) { model.state.value.current?.moveCount == 1 && model.state.value.saveStatus == "已自动保存" }
            val before = model.state.value.current!!.current.position
            compose.onNodeWithTag("game_back").performClick()
            compose.onNodeWithTag("continue_${type.name}").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(before, model.state.value.current!!.current.position)
            compose.onNodeWithTag("undo").assertIsEnabled().performClick()
            compose.waitUntil { model.state.value.current?.moveCount == 0 }
        }
    }

    @Test fun goPassesEnterScoringAndClosingResultKeepsGameFinished() {
        open(GameSession.create(GameType.GO, GameMode.PVP, 9))
        compose.onNodeWithTag("pass").performClick()
        compose.onNodeWithTag("pass").performClick()
        compose.onNodeWithTag("confirm_score").assertIsDisplayed().performClick()
        compose.onNodeWithText("查看棋盘").performClick()
        assertEquals(GamePhase.FINISHED, model.state.value.outcome.phase)
        point(4, 4).performClick()
        assertEquals(2, model.state.value.current!!.moveCount)
        compose.onNodeWithTag("undo").performClick()
        compose.waitUntil { model.state.value.outcome.phase == GamePhase.PLAYING }
    }

    @Test fun goNineteenPreviewRequiresConfirmation() {
        open(GameSession.create(GameType.GO, GameMode.PVP, 19))
        point(9, 9).performClick()
        assertEquals(0, model.state.value.current!!.moveCount)
        compose.onNodeWithTag("confirm_go").assertIsDisplayed().performClick()
        compose.waitUntil { model.state.value.current!!.moveCount == 1 }
        assertEquals(PlayerSide.BLACK, model.state.value.current!!.current.position.go()[9, 9])
    }

    @Test fun promotionPickerProducesLegalKnightAndCorrectNotation() {
        open(GameSession.create(GameType.CHESS, GameMode.PVP, position = PositionSnapshot.of(ChessBoard.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1"))))
        point(0, 1).performClick(); point(0, 0).performClick()
        compose.onNodeWithTag("promote_KNIGHT").performClick()
        compose.waitUntil { model.state.value.current!!.moveCount == 1 }
        assertEquals(ChessPieceType.KNIGHT, model.state.value.current!!.current.position.chess()[0, 0]!!.type)
        assertEquals("a8=N", model.state.value.current!!.current.event!!.move!!.notation)
    }

    @Test fun mateAndStalemateStayFinishedAfterDialogDismissal() {
        for ((fen, winner) in listOf("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1" to PlayerSide.WHITE,
            "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1" to null)) {
            open(GameSession.create(GameType.CHESS, GameMode.PVP, position = PositionSnapshot.of(ChessBoard.fromFen(fen))))
            compose.onNodeWithText("查看棋盘").performClick()
            assertEquals(GamePhase.FINISHED, model.state.value.outcome.phase)
            assertEquals(winner, model.state.value.outcome.winner)
            point(7, 0).performClick()
            assertEquals(0, model.state.value.current!!.moveCount)
        }
    }

    @Test fun replayBranchAndAnnotationArePersisted() {
        var session = NotationCodec.importPgn("1.e4 e5 2.Nf3 *")
        open(session)
        session = model.state.value.current!!
        val branchPoint = session.path()[1]
        compose.runOnUiThread { model.review(branchPoint.id) }
        compose.onNodeWithTag("branch_here").performClick()
        point(2, 1).performClick(); point(2, 3).performClick()
        assertEquals(2, model.state.value.current!!.moveCount)
        assertEquals("c5", model.state.value.current!!.current.event!!.move!!.notation)
        compose.onNodeWithTag("game_more").performClick()
        compose.onNodeWithText("添加局面注释").performClick()
        compose.onNodeWithTag("text_entry").performTextReplacement("QA variation note")
        compose.onNodeWithTag("text_entry_confirm").performClick()
        compose.waitUntil { model.state.value.current!!.current.comment == "QA variation note" }
        val exported = NotationCodec.exportPgn(model.state.value.current!!, "test", true)
        val restored = NotationCodec.importPgn(exported)
        assertEquals(2, restored.nodes.count { it.parentId == restored.path()[1].id })
    }

    @Test fun hintDoesNotAlterHistoryOrLastMove() {
        open(GameSession.create(GameType.GO, GameMode.PVP, 9))
        val node = model.state.value.current!!.cursorId
        compose.onNodeWithTag("hint").performClick()
        compose.waitUntil(15_000) { !model.state.value.hintThinking && model.state.value.hint != null }
        assertEquals(node, model.state.value.current!!.cursorId)
        assertEquals(0, model.state.value.current!!.moveCount)
        assertNotNull(model.state.value.hint!!.to)
    }

    @Test fun pendingAiCannotLandAfterRestartOrEditorSwitch() {
        val base = GameSession.create(GameType.CHESS).copy(difficulty = 3, thinkMillis = 5000)
        open(base)
        compose.runOnUiThread { model.performMove(BoardCoordinate(4, 6), BoardCoordinate(4, 4)); model.restart() }
        owned.add(model.state.value.current!!.id)
        Thread.sleep(500)
        compose.waitForIdle()
        assertEquals(0, model.state.value.current!!.moveCount)
        compose.runOnUiThread { model.performMove(BoardCoordinate(4, 6), BoardCoordinate(4, 4)); model.startEditor() }
        owned.add(model.state.value.current!!.id)
        val position = model.state.value.current!!.current.position
        Thread.sleep(500)
        assertTrue(model.state.value.current!!.isDraft)
        assertEquals(position, model.state.value.current!!.current.position)
    }

    @Test fun importPreviewCancellationPreservesCurrentGame() {
        open(GameSession.create(GameType.CHESS, GameMode.PVP))
        val original = model.state.value.current!!.id
        compose.runOnUiThread { model.importText("1.e4 e5 *") }
        compose.waitUntil(15_000) { model.state.value.pendingImport != null }
        compose.onNodeWithText("导入预览").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(original, model.state.value.current!!.id)
        compose.runOnUiThread { model.importText("1.d4 d5 *") }
        compose.waitUntil(15_000) { model.state.value.pendingImport != null }
        val importedId = model.state.value.pendingImport!!.id
        owned.add(importedId)
        compose.onNodeWithTag("confirm_import").performClick()
        compose.waitUntil { model.state.value.current!!.id == importedId }
        assertEquals(2, model.state.value.current!!.moveCount)
    }

    @Test fun exportClipboardReflectsUndo() {
        open(NotationCodec.importPgn("1.e4 e5 2.Nf3 Nc6 *"))
        compose.onNodeWithTag("undo").performClick()
        compose.onNodeWithTag("game_more").performClick()
        compose.onNodeWithText("导出与分享棋谱").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("export_copy").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("export_copy").assertIsEnabled().performClick()
        val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip!!.getItemAt(0).coerceToText(compose.activity).toString()
        assertEquals(3, NotationCodec.importPgn(text).moveCount)
    }

    @Test fun landscapeAndActivityRecreationPreserveBoard() {
        open(GameSession.create(GameType.XIANGQI, GameMode.PVP))
        point(0, 6).performClick(); point(0, 5).performClick()
        val position = model.state.value.current!!.current.position
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithTag("board").assertIsDisplayed()
        compose.onNodeWithTag("undo").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { model.state.value.loaded }
        assertEquals(position, model.state.value.current!!.current.position)
    }

    @Test fun allCuratedLessonsArePlayableAndSolvable() {
        for (lesson in Lessons.all) {
            open(lesson.session())
            if (lesson.type != GameType.GO) point(lesson.from.x, lesson.from.y).performClick()
            point(lesson.to.x, lesson.to.y).performClick()
            compose.waitUntil { model.state.value.lessonSolved }
            assertEquals(1, model.state.value.current!!.moveCount)
        }
    }

    @Test fun boardExposesSelectableAccessibleSquares() {
        open(GameSession.create(GameType.CHESS, GameMode.PVP))
        point(4, 6).assertContentDescriptionEquals("e2，白方兵").performClick()
        point(4, 6).assertIsSelected()
        point(4, 4).assertContentDescriptionEquals("e4，空格").performClick()
        assertEquals(1, model.state.value.current!!.moveCount)
    }
}
