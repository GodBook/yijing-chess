package com.yijing.chess

import com.google.gson.Gson
import com.yijing.chess.chess.ChessBoard
import com.yijing.chess.go.GoBoard
import com.yijing.chess.model.*
import com.yijing.chess.session.*
import com.yijing.chess.storage.*
import org.junit.Assert.*
import org.junit.Test

class SessionRegressionTest {
    private fun chess() = GameSession.create(GameType.CHESS, GameMode.PVP)
    private fun play(session: GameSession, from: String, to: String): GameSession {
        fun coord(text: String) = BoardCoordinate(text[0] - 'a', 8 - text[1].digitToInt())
        return session.play(coord(from), coord(to))
    }

    @Test fun undoAndBranchShareOneHistory() {
        val first = play(chess(), "e2", "e4")
        val second = play(first, "e7", "e5")
        val undone = second.undo()
        assertEquals(first.current.position, undone.current.position)
        assertEquals(1, undone.moveCount)
        val branch = play(undone, "c7", "c5")
        assertEquals(2, branch.moveCount)
        assertEquals(2, branch.nodes.count { it.parentId == first.cursorId })
        assertEquals("c5", branch.path().last().event?.move?.notation)
        branch.validate()
    }

    @Test fun serializationPreservesUndoAndAnnotations() {
        val session = play(play(chess(), "e2", "e4"), "e7", "e5").annotate("开放中心")
        val restored = Gson().fromJson(Gson().toJson(session), GameSession::class.java).validate()
        assertEquals(session, restored)
        assertEquals("开放中心", restored.current.comment)
        assertEquals(1, restored.undo().moveCount)
    }

    @Test fun replayCannotAccidentallyMove() {
        val session = play(chess(), "e2", "e4")
        val reviewing = session.review(session.root.id)
        assertTrue(reviewing.isReview)
        assertTrue(runCatching { play(reviewing, "d2", "d4") }.isFailure)
        val branch = play(reviewing.branchHere(), "d2", "d4")
        assertEquals("d4", branch.current.event?.label)
        assertEquals(session.liveNodeId, reviewing.returnToLive().cursorId)
    }

    @Test fun goPassesAreRealEventsAndUndoRestoresTurn() {
        val start = GameSession.create(GameType.GO, GameMode.PVP, 9)
        val first = start.pass()
        val ended = first.pass()
        assertEquals(2, ended.moveCount)
        assertEquals(GamePhase.SCORING, ended.outcome().phase)
        assertTrue(runCatching { ended.play(BoardCoordinate(0, 0), BoardCoordinate(0, 0)) }.isFailure)
        val undone = ended.undo()
        assertEquals(1, undone.current.position.passes)
        assertEquals(PlayerSide.WHITE, undone.current.position.turn)
        val continued = ended.resumeGo()
        assertEquals(GamePhase.PLAYING, continued.outcome().phase)
        continued.validate()
    }

    @Test fun pveUndoReturnsToHumanDecision() {
        var session = GameSession.create(GameType.CHESS)
        session = play(play(session, "e2", "e4"), "e7", "e5")
        assertEquals(0, session.undo().moveCount)
        val thinking = play(GameSession.create(GameType.CHESS), "e2", "e4")
        assertEquals(0, thinking.undo().moveCount)
    }

    @Test fun mateAndStalemateAreDistinct() {
        val mate = GameSession.create(GameType.CHESS, GameMode.PVP, position = PositionSnapshot.of(ChessBoard.fromFen("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1")))
        assertEquals(PlayerSide.WHITE, mate.outcome().winner)
        val stale = GameSession.create(GameType.CHESS, GameMode.PVP, position = PositionSnapshot.of(ChessBoard.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")))
        assertEquals(GamePhase.FINISHED, stale.outcome().phase)
        assertNull(stale.outcome().winner)
        assertEquals("困毙和棋", stale.outcome().reason)
    }

    @Test fun threefoldCanBeClaimedAndFivefoldIsAutomatic() {
        var session = chess()
        repeat(2) { session = play(play(play(play(session, "g1", "f3"), "g8", "f6"), "f3", "g1"), "f6", "g8") }
        assertNotNull(SessionRules.drawClaim(session))
        assertEquals(GamePhase.PLAYING, session.outcome().phase)
        repeat(2) { session = play(play(play(play(session, "g1", "f3"), "g8", "f6"), "f3", "g1"), "f6", "g8") }
        assertEquals(GamePhase.FINISHED, session.outcome().phase)
    }

    @Test fun finishedOutcomeDoesNotDependOnDialog() {
        val session = chess().finish(PlayerSide.BLACK, "白方认输")
        assertEquals("0-1", session.outcome().result(GameType.CHESS))
        assertEquals(GamePhase.FINISHED, Gson().fromJson(Gson().toJson(session), GameSession::class.java).outcome().phase)
    }

    @Test fun editorValidationExplainsMissingKings() {
        val draft = GameSession.create(GameType.CHESS, GameMode.EDITOR, position = SessionRules.empty(GameType.CHESS))
        assertEquals(2, SessionRules.playableErrors(draft.current.position).count { it.contains("一个王") })
        assertEquals(GamePhase.PLAYING, draft.outcome().phase)
        draft.validate()
    }

    @Test fun legacyChessRebuildsVerifiedHistory() {
        val original = play(play(chess(), "e2", "e4"), "e7", "e5")
        val legacy = SavedGameState(GameType.CHESS, GameMode.PVP, boardState = original.current.position.state,
            turn = original.current.position.turn, moveHistoryJson = Gson().toJson(original.path().mapNotNull { it.event?.move }))
        val migrated = LegacyMigrator.migrate(legacy)
        assertEquals(2, migrated.moveCount)
        assertEquals(1, migrated.undo().moveCount)
    }

    @Test fun legacyGoPreservesBoardAndDisclosesMissingRules() {
        val board = GoBoard(9)
        board.playMove(BoardCoordinate(4, 4), PlayerSide.BLACK)
        val legacy = SavedGameState(GameType.GO, GameMode.PVP, boardState = board.toStateString(), boardSize = 9,
            turn = board.turn, moveHistoryJson = "[]", blackPrisoners = 2)
        val migrated = LegacyMigrator.migrate(legacy)
        assertEquals(board.toStateString(), migrated.current.position.state)
        assertEquals(2, migrated.current.position.blackPrisoners)
        assertTrue(migrated.migrationNotice!!.contains("停着和劫点"))
    }

    @Test fun invalidHistoryIsRejectedWithoutInventingMoves() {
        val session = play(chess(), "e2", "e4")
        val invalid = session.copy(nodes = session.nodes.map { if (it.event != null) it.copy(position = session.root.position) else it })
        assertTrue(runCatching { invalid.validate() }.isFailure)
    }

    @Test fun goScoringUsesKomiAndDeclaredRule() {
        val board = GoBoard(9)
        board[0, 0] = PlayerSide.BLACK
        board[8, 8] = PlayerSide.WHITE
        val session = GameSession.create(GameType.GO, GameMode.PVP, 9, PositionSnapshot.of(board))
        val score = scoreGo(session)
        assertEquals(1.0, score.black, 0.001)
        assertEquals(8.5, score.white, 0.001)
        assertEquals(79, score.neutral)
    }
}
