package com.yijing.chess

import com.yijing.chess.model.*
import com.yijing.chess.parser.NotationCodec
import com.yijing.chess.session.*
import org.junit.Assert.*
import org.junit.Test

class NotationRegressionTest {
    @Test fun pgnRoundTripHasSameBoardCommentsAndVariations() {
        val session = NotationCodec.importPgn("[Event \"试验\"]\n[Result \"*\"]\n\n1.e4 {控制中心} e5 (1...c5 {西西里}) 2.Nf3 *")
        assertEquals(3, session.moveCount)
        assertEquals(2, session.nodes.count { it.parentId == session.path()[1].id })
        val restored = NotationCodec.importPgn(NotationCodec.exportPgn(session, "test", true))
        assertEquals(session.current.position, restored.current.position)
        assertEquals(session.nodes.size, restored.nodes.size)
        assertEquals("控制中心", restored.path()[1].comment)
    }

    @Test fun undoExportAndRestartCannotIncludeOldMoves() {
        val session = NotationCodec.importPgn("1.e4 e5 2.Nf3 Nc6 *").undo().undo()
        val restored = NotationCodec.importPgn(NotationCodec.exportPgn(session, "test"))
        assertEquals(2, restored.moveCount)
        assertEquals(session.current.position, restored.current.position)
        assertEquals(0, NotationCodec.importPgn(NotationCodec.exportPgn(GameSession.create(GameType.CHESS), "test")).moveCount)
    }

    @Test fun customBlackToMoveFenAndMateResult() {
        val text = "[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/KR6 b - - 17 42\"]\n[Result \"*\"]\n42...Kg7 *"
        val session = NotationCodec.importPgn(text)
        val exported = NotationCodec.exportPgn(session, "test")
        assertTrue(exported.contains("[SetUp \"1\"]"))
        assertTrue(exported.contains("42... Kg7"))
        assertEquals(session.current.position, NotationCodec.importPgn(exported).current.position)
        val mate = NotationCodec.importPgn("[FEN \"7k/8/5KQ1/8/8/8/8/8 w - - 0 1\"]\n1.Qg7# 1-0")
        assertEquals(PlayerSide.WHITE, mate.outcome().winner)
    }

    @Test fun sgfRoundTripPreservesSetupWhiteFirstPassesAndEscapes() {
        val text = "(;GM[1]FF[4]SZ[9]GN[练习]KM[6.5]RU[Japanese]AB[aa][bb]AW[cc]PL[W]C[方括号\\]与反斜线\\\\];W[dd]C[注释];B[];W[])"
        val session = NotationCodec.importSgf(text)
        assertEquals(GamePhase.SCORING, session.outcome().phase)
        assertEquals(3, session.moveCount)
        assertEquals(PlayerSide.WHITE, session.path()[1].event?.side)
        val restored = NotationCodec.importSgf(NotationCodec.exportSgf(session, "test"))
        assertEquals(session.current.position, restored.current.position)
        assertEquals(session.root.comment, restored.root.comment)
        assertEquals(GoScoringRule.TERRITORY, restored.scoringRule)
    }

    @Test fun sgfVariationsAndSelectedMainLine() {
        val session = NotationCodec.importSgf("(;SZ[9];B[aa](;W[bb];B[cc])(;W[dd]))")
        assertEquals(BoardCoordinate(2, 2), session.current.event?.move?.to)
        val exported = NotationCodec.exportSgf(session, "test", true)
        val restored = NotationCodec.importSgf(exported)
        assertEquals(session.current.position, restored.current.position)
        assertEquals(session.nodes.size, restored.nodes.size)
    }

    @Test fun compressedSetupAndSgfResult() {
        val session = NotationCodec.importSgf("(;SZ[9]AB[aa:bb]PL[W]RE[W+2.5])")
        assertEquals(4, session.root.position.state.count { it == 'B' })
        assertEquals(PlayerSide.WHITE, session.outcome().winner)
        assertEquals(2.5, session.outcome().margin!!, 0.001)
    }

    @Test fun xiangqiPgnUsesDocumentedIccs() {
        var session = GameSession.create(GameType.XIANGQI, GameMode.PVP)
        session = session.play(BoardCoordinate(1, 7), BoardCoordinate(4, 7))
        val exported = NotationCodec.exportPgn(session, "test")
        assertTrue(exported.contains("[Format \"ICCS\"]"))
        assertTrue(exported.contains("b2e2"))
        assertEquals(session.current.position, NotationCodec.importPgn(exported).current.position)
    }

    @Test fun malformedAndIllegalImportsAreRejected() {
        for (text in listOf("(;SZ[9];B[zz])", "(;SZ[9];B[aa];W[aa])", "(;SZ[9];B[]", "1.e4 (1...e5 *", "1.e5 *"))
            assertTrue(text, runCatching { NotationCodec.importText(text) }.isFailure)
    }

    @Test fun fenImportsAsProtectedDraft() {
        val session = NotationCodec.importText("7k/8/8/8/8/8/8/KR6 b - - 17 42")
        assertTrue(session.isDraft)
        assertEquals("17", session.current.position.state.split(" ")[4])
    }

    @Test fun xiangqiRankZeroAndGoResumeSurviveRoundTrip() {
        val chess = GameSession.create(GameType.XIANGQI, GameMode.PVP).play(BoardCoordinate(1, 9), BoardCoordinate(2, 7))
        assertEquals(chess.current.position, NotationCodec.importPgn(NotationCodec.exportPgn(chess, "test")).current.position)
        val resumed = GameSession.create(GameType.GO, GameMode.PVP, 9).pass().pass().resumeGo().annotate("双方确认后继续")
        val restored = NotationCodec.importSgf(NotationCodec.exportSgf(resumed, "test"))
        assertEquals(0, restored.current.position.passes)
        assertEquals(GamePhase.PLAYING, restored.outcome().phase)
        assertEquals("双方确认后继续", restored.current.comment)
    }

    @Test fun sgfSetupDefaultsToBlackUnlessHandicapSaysOtherwise() {
        assertEquals(PlayerSide.BLACK, NotationCodec.importSgf("(;SZ[9]AB[aa])").current.position.turn)
        assertEquals(PlayerSide.WHITE, NotationCodec.importSgf("(;SZ[9]HA[2]AB[aa][bb])").current.position.turn)
    }

    @Test fun longSgfSequenceImportsAndExportsWithoutUsingTheCallStackForEachMove() {
        val text = "(;GM[1]FF[4]SZ[9]" + ";B[];W[];YJRS[1]".repeat(1000) + ")"
        val session = NotationCodec.importSgf(text)
        assertEquals(3001, session.nodes.size)
        assertEquals(2000, session.moveCount)
        val restored = NotationCodec.importSgf(NotationCodec.exportSgf(session, "test", true))
        assertEquals(session.nodes.size, restored.nodes.size)
        assertEquals(session.current.position, restored.current.position)
    }
}
