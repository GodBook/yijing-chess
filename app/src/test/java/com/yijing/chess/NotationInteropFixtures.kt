package com.yijing.chess

import com.google.gson.Gson
import com.yijing.chess.parser.NotationCodec
import java.io.File

/** Outputs real codec results for validation by independent PGN and SGF readers. */
object NotationInteropFixtures {
    fun write(directory: File) {
        directory.mkdirs()
        val fixtures = linkedMapOf(
            "variations.pgn" to "1.e4 {控制中心} e5 (1...c5 {西西里}) 2.Nf3 Nc6 3.Bb5 a6 *",
            "castling.pgn" to "[FEN \"r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1\"]\n1.O-O O-O-O *",
            "promotion.pgn" to "[FEN \"7k/P7/8/8/8/8/8/7K w - - 0 1\"]\n1.a8=N *",
            "mate.pgn" to "[FEN \"7k/8/5KQ1/8/8/8/8/8 w - - 0 1\"]\n1.Qg7# 1-0",
            "black-first.pgn" to "[FEN \"7k/8/8/8/8/8/8/KR6 b - - 17 42\"]\n42...Kg7 *",
            "setup-variations.sgf" to "(;GM[1]FF[4]SZ[9]GN[独立校验]KM[6.5]RU[Japanese]AB[aa][bb]AW[cc]PL[W]C[方括号\\]反斜线\\\\];W[dd](;B[];W[])(;B[ee]))",
            "standard.sgf" to "(;GM[1]FF[4]SZ[19]KM[7.5];B[dd];W[pp];B[pd];W[dp])"
        )
        val expected = fixtures.map { (name, source) ->
            val session = NotationCodec.importText(source)
            File(directory, name).writeText(NotationCodec.export(session, BuildConfig.VERSION_NAME, true), Charsets.UTF_8)
            mapOf("file" to name, "state" to session.current.position.state, "moves" to session.moveCount,
                "nodes" to session.nodes.size, "comment" to session.root.comment)
        }
        File(directory, "expected.json").writeText(Gson().toJson(expected), Charsets.UTF_8)
    }
}
