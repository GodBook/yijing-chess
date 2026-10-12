package com.yijing.chess.parser

import com.yijing.chess.model.BoardCoordinate
import com.yijing.chess.model.MoveRecord
import com.yijing.chess.model.MoveTreeNode

/**
 * 通用 SGF (Smart Game Format) 解析与序列化器
 */
object SgfParser {

    /**
     * 将走步树序列化为 SGF 格式文本
     */
    fun exportToSgf(
        rootNode: MoveTreeNode,
        gameName: String,
        boardSize: Int = 19
    ): String {
        val sb = StringBuilder()
        sb.append("(;GM[1]FF[4]CA[UTF-8]AP[YiJing:1.0.0]SZ[").append(boardSize).append("]GN[").append(gameName).append("]\n")

        fun writeNode(node: MoveTreeNode) {
            val move = node.move
            if (move != null) {
                // 围棋记谱: B[dp] 或 W[dd]
                val color = if (node.moveNumber % 2 == 1) "B" else "W"
                val colChar = ('a'.code + move.to.x).toChar()
                val rowChar = ('a'.code + move.to.y).toChar()
                sb.append(";").append(color).append("[").append(colChar).append(rowChar).append("]")
                if (node.comment.isNotBlank()) {
                    sb.append("C[").append(escapeSgf(node.comment)).append("]")
                }
            }

            if (node.children.size == 1) {
                writeNode(node.children.first())
            } else if (node.children.size > 1) {
                for (child in node.children) {
                    sb.append("\n(")
                    writeNode(child)
                    sb.append(")")
                }
            }
        }

        if (rootNode.children.isNotEmpty()) {
            writeNode(rootNode.children.first())
        }
        sb.append(")")
        return sb.toString()
    }

    private fun escapeSgf(text: String): String = text.replace("]", "\\]")

    /**
     * 基础 SGF 走步序列解析
     */
    fun parseSimpleSgf(sgfContent: String): List<BoardCoordinate> {
        val moves = mutableListOf<BoardCoordinate>()
        val regex = Regex(";[BW]\\[([a-z]{2})\\]")
        val matches = regex.findAll(sgfContent)
        for (m in matches) {
            val coordStr = m.groupValues[1]
            val x = coordStr[0] - 'a'
            val y = coordStr[1] - 'a'
            moves.add(BoardCoordinate(x, y))
        }
        return moves
    }
}
