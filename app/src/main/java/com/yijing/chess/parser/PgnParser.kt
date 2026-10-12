package com.yijing.chess.parser

import com.yijing.chess.model.MoveTreeNode

/**
 * 国际象棋 PGN (Portable Game Notation) 导出与解析器
 */
object PgnParser {

    fun exportToPgn(
        rootNode: MoveTreeNode,
        whitePlayer: String = "White",
        blackPlayer: String = "Black",
        event: String = "Casual Game",
        result: String = "*"
    ): String {
        val sb = StringBuilder()
        sb.append("[Event \"$event\"]\n")
        sb.append("[Site \"YiJing App\"]\n")
        sb.append("[Date \"????.??.??\"]\n")
        sb.append("[Round \"1\"]\n")
        sb.append("[White \"$whitePlayer\"]\n")
        sb.append("[Black \"$blackPlayer\"]\n")
        sb.append("[Result \"$result\"]\n\n")

        var curr: MoveTreeNode? = rootNode.currentChild
        var moveIndex = 1
        var lineLen = 0

        while (curr != null) {
            val move = curr.move
            if (move != null) {
                val token = if (curr.moveNumber % 2 == 1) {
                    "$moveIndex. ${move.notation} "
                } else {
                    moveIndex++
                    "${move.notation} "
                }
                sb.append(token)
                lineLen += token.length
                if (lineLen > 70) {
                    sb.append("\n")
                    lineLen = 0
                }
            }
            curr = curr.currentChild
        }

        sb.append(result)
        return sb.toString()
    }

    fun exportChessPgn(rootNode: MoveTreeNode, event: String = "弈境国际象棋对局"): String {
        return exportToPgn(rootNode, event = event)
    }

    fun exportXiangqiPgn(rootNode: MoveTreeNode, event: String = "弈境中国象棋对局"): String {
        return exportToPgn(rootNode, whitePlayer = "红方", blackPlayer = "黑方", event = event)
    }
}
