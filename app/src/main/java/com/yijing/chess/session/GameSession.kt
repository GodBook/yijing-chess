package com.yijing.chess.session

import com.yijing.chess.chess.ChessBoard
import com.yijing.chess.go.GoBoard
import com.yijing.chess.model.*
import com.yijing.chess.xiangqi.XiangqiBoard
import java.util.UUID

fun GameType.firstSide(): PlayerSide = when (this) {
    GameType.XIANGQI -> PlayerSide.RED
    GameType.CHESS -> PlayerSide.WHITE
    GameType.GO -> PlayerSide.BLACK
}

fun GameType.opposite(side: PlayerSide): PlayerSide = side.opposite(this == GameType.XIANGQI)

data class PositionSnapshot(
    val gameType: GameType,
    val state: String,
    val turn: PlayerSide,
    val boardSize: Int = 19,
    val ko: BoardCoordinate? = null,
    val passes: Int = 0,
    val blackPrisoners: Int = 0,
    val whitePrisoners: Int = 0
) {
    fun chess(): ChessBoard = ChessBoard.fromFen(state)
    fun xiangqi(): XiangqiBoard = XiangqiBoard.fromFen(state)
    fun go(): GoBoard = GoBoard.fromStateString(boardSize, state, turn, blackPrisoners, whitePrisoners, ko, passes)

    companion object {
        fun of(board: ChessBoard) = PositionSnapshot(GameType.CHESS, board.toFen(), board.turn)
        fun of(board: XiangqiBoard) = PositionSnapshot(GameType.XIANGQI, board.toFen(), board.turn)
        fun of(board: GoBoard) = PositionSnapshot(GameType.GO, board.toStateString(), board.turn, board.size,
            board.lastCapturedCoord, board.consecutivePasses, board.blackPrisoners, board.whitePrisoners)
    }
}

enum class EventKind { MOVE, PASS, RESUME }
data class GameEvent(val kind: EventKind, val side: PlayerSide, val move: MoveRecord? = null) {
    val label: String get() = when (kind) {
        EventKind.MOVE -> move?.notation.orEmpty()
        EventKind.PASS -> "${side.displayName}停着"
        EventKind.RESUME -> "继续对弈"
    }
}

data class GameNode(
    val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null,
    val event: GameEvent? = null,
    val position: PositionSnapshot,
    val comment: String = ""
)

enum class GamePhase { PLAYING, SCORING, FINISHED }
enum class GoScoringRule(val label: String) { AREA("中国规则 · 数子"), TERRITORY("日本规则 · 数目") }
data class GameOutcome(val phase: GamePhase = GamePhase.PLAYING, val winner: PlayerSide? = null, val reason: String = "", val margin: Double? = null) {
    fun result(type: GameType): String = if (phase != GamePhase.FINISHED) "*" else when (winner) {
        null -> "1/2-1/2"
        type.firstSide() -> "1-0"
        else -> "0-1"
    }
}

/** Immutable, acyclic persistence model. Nodes store exact after-positions; parents give before-positions. */
data class GameSession(
    val schemaVersion: Int = 2,
    val id: String = UUID.randomUUID().toString(),
    val gameType: GameType,
    val mode: GameMode = GameMode.PVE,
    val title: String = "${gameType.displayName}对局",
    val humanSide: PlayerSide = gameType.firstSide(),
    val difficulty: Int = 2,
    val thinkMillis: Long = 2500,
    val komi: Double = 7.5,
    val scoringRule: GoScoringRule = GoScoringRule.AREA,
    val nodes: List<GameNode>,
    val cursorId: String,
    val liveNodeId: String = cursorId,
    val preferredChildren: Map<String, String> = emptyMap(),
    val declaredOutcome: GameOutcome? = null,
    val markedDead: List<BoardCoordinate> = emptyList(),
    val legacyNotes: List<String> = emptyList(),
    val migrationNotice: String? = null,
    val lessonId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val revision: Long = 0
) {
    val current: GameNode get() = nodes.first { it.id == cursorId }
    val live: GameNode get() = nodes.first { it.id == liveNodeId }
    val root: GameNode get() = nodes.first()
    val isReview: Boolean get() = cursorId != liveNodeId
    val isDraft: Boolean get() = mode == GameMode.EDITOR
    val hasMoves: Boolean get() = liveNodeId != root.id
    val moveCount: Int get() = path(liveNodeId).count { it.event?.kind in listOf(EventKind.MOVE, EventKind.PASS) }

    fun path(nodeId: String = cursorId): List<GameNode> {
        val byId = nodes.associateBy { it.id }
        val result = mutableListOf<GameNode>()
        var next: GameNode? = requireNotNull(byId[nodeId]) { "历史位置不存在" }
        val seen = mutableSetOf<String>()
        while (next != null) {
            require(seen.add(next.id)) { "棋谱历史存在循环" }
            result.add(next)
            next = next.parentId?.let { requireNotNull(byId[it]) { "历史父节点缺失" } }
        }
        return result.asReversed()
    }

    fun outcome(): GameOutcome = when {
        isDraft || isReview -> GameOutcome()
        declaredOutcome != null -> declaredOutcome
        else -> SessionRules.outcome(this)
    }

    fun play(from: BoardCoordinate, to: BoardCoordinate, promotion: String? = null): GameSession {
        require(!isDraft && !isReview && outcome().phase == GamePhase.PLAYING) { "请先返回实战或开始推演" }
        val move = SessionRules.legalMoves(current.position).firstOrNull {
            it.from == from && it.to == to && it.promotion == promotion
        } ?: throw IllegalArgumentException("该走法不合法")
        return append(GameEvent(EventKind.MOVE, current.position.turn, move))
    }

    fun pass(): GameSession {
        require(gameType == GameType.GO && !isDraft && !isReview && outcome().phase == GamePhase.PLAYING) { "当前不能停着" }
        return append(GameEvent(EventKind.PASS, current.position.turn))
    }

    fun append(event: GameEvent): GameSession {
        require(event.side == current.position.turn) { "行棋方与当前轮次不符" }
        val position = SessionRules.after(current.position, event)
        val existing = nodes.firstOrNull { it.parentId == cursorId && it.event == event }
        val node = existing ?: GameNode(parentId = cursorId, event = event, position = position)
        return copy(nodes = if (existing == null) nodes + node else nodes, cursorId = node.id, liveNodeId = node.id,
            preferredChildren = preferredChildren + (cursorId to node.id), declaredOutcome = null, markedDead = emptyList()).touch()
    }

    fun undo(): GameSession {
        if (!hasMoves || isDraft) return this
        var node = live
        var remaining = if (mode == GameMode.PVE && live.position.turn == humanSide) 2 else 1
        while (remaining > 0 && node.parentId != null) {
            if (node.event?.kind != EventKind.RESUME) remaining--
            node = nodes.first { it.id == node.parentId }
        }
        return copy(cursorId = node.id, liveNodeId = node.id, declaredOutcome = null, markedDead = emptyList()).touch()
    }

    fun review(nodeId: String): GameSession {
        require(nodes.any { it.id == nodeId })
        return copy(cursorId = nodeId).touch()
    }

    fun returnToLive() = copy(cursorId = liveNodeId).touch()
    fun branchHere() = copy(liveNodeId = cursorId, declaredOutcome = null, markedDead = emptyList()).touch()
    fun previous() = current.parentId?.let(::review) ?: this
    fun next(): GameSession {
        val children = nodes.filter { it.parentId == cursorId }
        val preferred = preferredChildren[cursorId]
        return children.firstOrNull { it.id == preferred }?.let { review(it.id) } ?: children.firstOrNull()?.let { review(it.id) } ?: this
    }

    fun replaceDraft(position: PositionSnapshot): GameSession {
        require(isDraft && position.gameType == gameType)
        SessionRules.validateSnapshot(position)
        val node = root.copy(position = position)
        return copy(nodes = listOf(node), cursorId = node.id, liveNodeId = node.id, declaredOutcome = null).touch()
    }

    fun annotate(comment: String) = copy(nodes = nodes.map { if (it.id == cursorId) it.copy(comment = comment.take(10_000)) else it }).touch()
    fun rename(name: String) = copy(title = name.trim().take(80).ifBlank { "${gameType.displayName}对局" }).touch()
    fun finish(winner: PlayerSide?, reason: String, margin: Double? = null) = copy(declaredOutcome = GameOutcome(GamePhase.FINISHED, winner, reason, margin)).touch()
    fun resumeGo(): GameSession {
        require(gameType == GameType.GO && !isReview && !isDraft)
        return append(GameEvent(EventKind.RESUME, current.position.turn))
    }

    fun toggleDead(coord: BoardCoordinate): GameSession {
        require(gameType == GameType.GO && outcome().phase == GamePhase.SCORING)
        val group = current.position.go().getGroupAndLiberties(coord).first
        val dead = markedDead.toSet()
        return copy(markedDead = (if (group.any { it in dead }) dead - group else dead + group).toList()).touch()
    }

    fun touch() = copy(updatedAt = System.currentTimeMillis(), revision = revision + 1)

    fun validate(): GameSession {
        require(schemaVersion == 2) { "存档版本暂不支持" }
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")) && nodes.isNotEmpty() && nodes.size <= 10_000) { "存档标识或历史长度无效" }
        require(difficulty in 1..3 && thinkMillis in 25..30_000 && komi.isFinite() && komi in -100.0..100.0) { "存档对局设置无效" }
        require(humanSide in listOf(gameType.firstSide(), gameType.opposite(gameType.firstSide()))) { "执子方无效" }
        require(root.parentId == null && root.event == null && nodes.drop(1).all { it.parentId != null && it.event != null }) { "棋谱根节点无效" }
        val seen = mutableSetOf<String>()
        val byId = nodes.associateBy { it.id }
        for (node in nodes) {
            require(node.parentId == null || node.parentId in seen) { "棋谱历史缺失或存在循环" }
            require(seen.add(node.id) && node.position.gameType == gameType) { "棋谱节点重复或棋类不符" }
            SessionRules.validateSnapshot(node.position)
            node.event?.let { event ->
                val parent = requireNotNull(byId[node.parentId]) { "棋谱父节点缺失" }
                require(SessionRules.after(parent.position, event) == node.position) { "棋谱走法与局面不一致" }
            }
        }
        require(cursorId in seen && liveNodeId in seen) { "存档当前历史位置缺失" }
        require(preferredChildren.all { (parent, child) -> nodes.any { it.id == child && it.parentId == parent } }) { "棋谱分支索引无效" }
        return this
    }

    companion object {
        fun create(type: GameType, mode: GameMode = GameMode.PVE, size: Int = 19, position: PositionSnapshot? = null): GameSession {
            val start = position ?: SessionRules.initial(type, size)
            val root = GameNode(position = start)
            return GameSession(gameType = type, mode = mode, nodes = listOf(root), cursorId = root.id)
        }
    }
}

fun coordinateName(type: GameType, coord: BoardCoordinate, size: Int = 19): String = when (type) {
    GameType.CHESS -> "${('a'.code + coord.x).toChar()}${8 - coord.y}"
    GameType.GO -> "${"ABCDEFGHJKLMNOPQRST".getOrElse(coord.x) { '?' }}${size - coord.y}"
    GameType.XIANGQI -> "${coord.x + 1}列${10 - coord.y}路"
}
