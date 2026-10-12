package com.yijing.chess.model

import java.util.UUID

/**
 * 棋谱变着树节点 (支持多分支推演、注释及引擎评分)
 */
data class MoveTreeNode(
    val id: String = UUID.randomUUID().toString(),
    val moveNumber: Int,
    val move: MoveRecord?,                // 根节点为 null (表示开局初始或排局局面)
    val fenBefore: String,                // 走子前快照 (FEN 格式)
    var comment: String = "",             // 用户注释或解说
    var evalScore: Float? = null,         // 引擎分析估值 (+1.5, -0.8 等)
    var parent: MoveTreeNode? = null,
    val children: MutableList<MoveTreeNode> = mutableListOf(),
    var selectedChildIndex: Int = 0       // 当前主线分支索引
) {
    /**
     * 当前选中的主线子节点
     */
    val currentChild: MoveTreeNode?
        get() = if (children.isNotEmpty() && selectedChildIndex in children.indices) {
            children[selectedChildIndex]
        } else children.firstOrNull()

    /**
     * 添加或复用分支走法
     */
    fun addVariation(
        move: MoveRecord,
        fenBefore: String,
        comment: String = ""
    ): MoveTreeNode {
        val existing = children.find { it.move?.from == move.from && it.move?.to == move.to && it.move?.promotion == move.promotion }
        if (existing != null) {
            selectedChildIndex = children.indexOf(existing)
            return existing
        }
        val nextNode = MoveTreeNode(
            moveNumber = this.moveNumber + 1,
            move = move,
            fenBefore = fenBefore,
            comment = comment,
            parent = this
        )
        children.add(nextNode)
        selectedChildIndex = children.lastIndex
        return nextNode
    }

    /**
     * 是否为主线
     */
    val isMainLine: Boolean
        get() = parent?.selectedChildIndex == parent?.children?.indexOf(this)
}

/**
 * 通用着法记录结构
 */
data class MoveRecord(
    val from: BoardCoordinate,
    val to: BoardCoordinate,
    val pieceText: String,               // 如 "车"、"马"、"N"、"B"、"黑"
    val notation: String,                // 中文记谱 "炮二平五" 或 代数记谱 "Nf3" 或 "Qd1"
    val capturedText: String? = null,    // 被吃子
    val promotion: String? = null        // 升变 (如 "Q", "R", "B", "N")
)
