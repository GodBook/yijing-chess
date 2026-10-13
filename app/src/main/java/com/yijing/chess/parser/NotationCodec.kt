package com.yijing.chess.parser

import com.yijing.chess.chess.ChessBoard
import com.yijing.chess.go.GoBoard
import com.yijing.chess.model.*
import com.yijing.chess.session.*
import com.yijing.chess.xiangqi.XiangqiBoard
import java.text.SimpleDateFormat
import java.util.Locale

/** PGN/SAN, Xiangqi PGN/ICCS, SGF FF[4], and strict FEN. All imports build the same event tree. */
object NotationCodec {
    private const val MAX_TEXT = 2_000_000
    private val results = setOf("1-0", "0-1", "1/2-1/2", "*")

    fun importText(input: String): GameSession {
        require(input.length in 1..MAX_TEXT) { "棋谱为空或超过 2 MB，请选择单局棋谱" }
        val text = input.trim().trimStart('\uFEFF')
        return when {
            text.startsWith("(") -> importSgf(text)
            text.startsWith("[") || Regex("^\\d+\\.").containsMatchIn(text) -> importPgn(text)
            text.substringBefore(' ').count { it == '/' } == 7 -> GameSession.create(GameType.CHESS, GameMode.EDITOR,
                position = PositionSnapshot.of(ChessBoard.fromFen(text))).rename("导入的国际象棋局面")
            text.substringBefore(' ').count { it == '/' } == 9 -> GameSession.create(GameType.XIANGQI, GameMode.EDITOR,
                position = PositionSnapshot.of(XiangqiBoard.fromFen(text))).rename("导入的象棋局面")
            else -> throw IllegalArgumentException("未识别棋谱，请选择 PGN、SGF 或粘贴完整 FEN")
        }.validate()
    }

    fun export(session: GameSession, appVersion: String, variations: Boolean = false): String =
        if (session.gameType == GameType.GO) exportSgf(session, appVersion, variations) else exportPgn(session, appVersion, variations)

    private fun escapedTag(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
    private fun sgfValue(text: String) = text.replace("\\", "\\\\").replace("]", "\\]").replace("\r\n", "\n")

    fun exportPgn(session: GameSession, appVersion: String, variations: Boolean = false): String {
        require(session.gameType != GameType.GO)
        val result = session.outcome().result(session.gameType)
        val tags = linkedMapOf(
            "Event" to session.title, "Site" to (session.metadata["Site"] ?: "YiJing"),
            "Date" to (session.metadata["Date"] ?: SimpleDateFormat("yyyy.MM.dd", Locale.ROOT).format(session.createdAt)),
            "Round" to (session.metadata["Round"] ?: "1"),
            "White" to (session.metadata["White"] ?: if (session.gameType == GameType.XIANGQI) "红方" else "白方"),
            "Black" to (session.metadata["Black"] ?: "黑方"), "Result" to result, "Annotator" to "YiJing $appVersion")
        if (session.gameType == GameType.XIANGQI) { tags["Variant"] = "Xiangqi"; tags["Format"] = "ICCS" }
        if (session.root.position.state != SessionRules.initial(session.gameType).state) {
            tags["SetUp"] = "1"; tags["FEN"] = session.root.position.state
        }
        val output = StringBuilder()
        for ((key, value) in tags) output.append("[$key \"${escapedTag(value)}\"]\n")
        output.append('\n')
        fun comment(value: String) { if (value.isNotBlank()) output.append("{${value.replace('{', '(').replace('}', ')')}} ") }
        comment(session.root.comment)
        val mainPath = session.path(session.liveNodeId)
        val mainNext = mainPath.zipWithNext().associate { it.first.id to it.second.id }
        val byId = session.nodes.associateBy { it.id }
        val childrenByParent = session.nodes.groupBy { it.parentId }
        val plyById = mutableMapOf(session.root.id to 0)
        session.nodes.drop(1).forEach { plyById[it.id] = plyById.getValue(it.parentId!!) + 1 }
        fun token(node: GameNode): String {
            val before = byId.getValue(node.parentId!!).position
            val event = node.event!!
            val move = event.move!!
            val number = if (session.gameType == GameType.CHESS) before.chess().fullMoveNumber else {
                val offset = if (session.root.position.turn == session.gameType.firstSide()) 0 else 1
                (plyById.getValue(node.id) - 1 + offset) / 2 + 1
            }
            val notation = if (session.gameType == GameType.CHESS) move.notation else iccs(move)
            return "$number${if (event.side == session.gameType.firstSide()) "." else "..."} $notation "
        }
        fun line(start: GameNode, principal: Boolean, depth: Int = 0) {
            require(depth < 128) { "棋谱分支嵌套过深" }
            var parent = start
            while (true) {
                if (principal && parent.id == session.liveNodeId) return
                val children = childrenByParent[parent.id].orEmpty()
                val selected = if (principal) children.firstOrNull { it.id == mainNext[parent.id] } else
                    children.firstOrNull { it.id == session.preferredChildren[parent.id] } ?: children.firstOrNull()
                if (selected == null) return
                output.append(token(selected)); comment(selected.comment)
                if (variations) for (alternative in children.filter { it.id != selected.id }) {
                    output.append("("); output.append(token(alternative)); comment(alternative.comment); line(alternative, false, depth + 1); output.append(") ")
                }
                parent = selected
            }
        }
        line(session.root, true)
        output.append(result).append('\n')
        return output.toString()
    }

    private fun iccs(move: MoveRecord) = "${('a'.code + move.from.x).toChar()}${9 - move.from.y}${('a'.code + move.to.x).toChar()}${9 - move.to.y}"

    private data class PgnToken(val kind: Char, val text: String)

    fun importPgn(text: String): GameSession {
        require(text.length <= MAX_TEXT)
        val tags = linkedMapOf<String, String>()
        val tokens = mutableListOf<PgnToken>()
        var index = 0
        while (index < text.length) {
            when (val char = text[index]) {
                ' ', '\t', '\n', '\r', '\uFEFF' -> index++
                '[' -> {
                    index++
                    val start = index
                    while (index < text.length && !text[index].isWhitespace()) index++
                    val key = text.substring(start, index)
                    while (index < text.length && text[index].isWhitespace()) index++
                    require(index < text.length && text[index++] == '"') { "PGN 标签缺少引号" }
                    val value = StringBuilder()
                    while (index < text.length && text[index] != '"') {
                        if (text[index] == '\\') { index++; require(index < text.length) { "PGN 标签转义不完整" } }
                        value.append(text[index++])
                    }
                    require(index < text.length) { "PGN 标签未结束" }; index++
                    while (index < text.length && text[index].isWhitespace()) index++
                    require(index < text.length && text[index++] == ']') { "PGN 标签未闭合" }
                    require(key.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "PGN 标签名无效" }
                    tags[key] = value.toString()
                }
                '{' -> {
                    val start = ++index
                    while (index < text.length && text[index] != '}') index++
                    require(index < text.length) { "PGN 注释未闭合" }
                    tokens.add(PgnToken('C', text.substring(start, index++)))
                }
                ';' -> {
                    val start = ++index
                    while (index < text.length && text[index] != '\n') index++
                    tokens.add(PgnToken('C', text.substring(start, index)))
                }
                '(', ')' -> { tokens.add(PgnToken(char, char.toString())); index++ }
                else -> {
                    val start = index
                    while (index < text.length && !text[index].isWhitespace() && text[index] !in "(){};[") index++
                    require(index > start) { "PGN 第 ${index + 1} 字符无法解析" }
                    tokens.add(PgnToken('M', text.substring(start, index)))
                }
            }
        }
        val fen = tags["FEN"]
        require(tags["SetUp"] != "1" || fen != null) { "自定义开局 PGN 缺少 FEN" }
        val type = if (tags["Variant"].equals("Xiangqi", true) || fen?.substringBefore(' ')?.count { it == '/' } == 9) GameType.XIANGQI else GameType.CHESS
        val start = fen?.let { if (type == GameType.CHESS) PositionSnapshot.of(ChessBoard.fromFen(it)) else PositionSnapshot.of(XiangqiBoard.fromFen(it)) }
        var session = GameSession.create(type, GameMode.PVP, position = start).copy(metadata = tags).rename(tags["Event"] ?: "导入棋谱")
        require(SessionRules.playableErrors(session.root.position).isEmpty()) { SessionRules.playableErrors(session.root.position).joinToString("；") }
        val stack = java.util.ArrayDeque<String>()
        var mainEnd = session.root.id
        var result = tags["Result"] ?: "*"
        for (token in tokens) {
            when (token.kind) {
                'C' -> session = session.annotate(listOf(session.current.comment, token.text).filter { it.isNotBlank() }.joinToString("\n"))
                '(' -> {
                    require(stack.size < 128) { "棋谱分支嵌套过深" }
                    stack.push(session.cursorId)
                    val parent = session.current.parentId ?: session.root.id
                    session = session.copy(cursorId = parent, liveNodeId = parent, declaredOutcome = null)
                }
                ')' -> {
                    require(stack.isNotEmpty()) { "PGN 分支括号不匹配" }
                    val back = stack.pop(); session = session.copy(cursorId = back, liveNodeId = back, declaredOutcome = null)
                }
                else -> {
                    var moveText = token.text.replace(Regex("^\\d+\\.(?:\\.\\.)?"), "")
                    if (moveText.isBlank() || moveText.startsWith('$') || moveText == "e.p.") continue
                    if (moveText in results) { if (stack.isEmpty()) result = moveText; continue }
                    moveText = moveText.trimEnd('!', '?')
                    if (type == GameType.CHESS) moveText = moveText.replace('0', 'O')
                    val legal = SessionRules.legalMoves(session.current.position)
                    fun normalized(value: String) = value.trimEnd('+', '#')
                    val matched = legal.filter { normalized(if (type == GameType.XIANGQI) iccs(it) else it.notation) == normalized(moveText) }
                    require(matched.size == 1) { "无法解析 ${session.moveCount + 1} 手：$moveText，走法非法或存在歧义" }
                    session = session.append(GameEvent(EventKind.MOVE, session.current.position.turn, matched.single()))
                    require(session.nodes.size <= 10_000) { "棋谱超过 10000 个节点" }
                    if (stack.isEmpty()) mainEnd = session.cursorId
                }
            }
        }
        require(stack.isEmpty()) { "PGN 分支未闭合" }
        require(result in results) { "PGN 结果无效" }
        session = selectMain(session, mainEnd)
        if (result != "*") {
            val natural = session.outcome()
            require(natural.phase != GamePhase.FINISHED || natural.result(type) == result) { "棋谱结果与终局局面冲突" }
            val winner = when (result) { "1-0" -> type.firstSide(); "0-1" -> type.opposite(type.firstSide()); else -> null }
            session = session.finish(winner, tags["Termination"] ?: "棋谱记录结果")
        }
        return session.validate()
    }

    fun exportSgf(session: GameSession, appVersion: String, variations: Boolean = false): String {
        require(session.gameType == GameType.GO)
        val root = session.root.position.go()
        val output = StringBuilder("(;GM[1]FF[4]CA[UTF-8]AP[YiJing:${sgfValue(appVersion)}]SZ[${root.size}]GN[${sgfValue(session.title)}]KM[${session.komi}]RU[${if (session.scoringRule == GoScoringRule.AREA) "Chinese" else "Japanese"}]PL[${if (root.turn == PlayerSide.BLACK) "B" else "W"}]")
        for ((property, color) in listOf("AB" to PlayerSide.BLACK, "AW" to PlayerSide.WHITE)) {
            val stones = buildList { for (y in 0 until root.size) for (x in 0 until root.size) if (root[x, y] == color) add(root.toSgfCoord(BoardCoordinate(x, y))) }
            if (stones.isNotEmpty()) { output.append(property); for (stone in stones) output.append("[$stone]") }
        }
        if (root.lastCapturedCoord != null) output.append("YJKO[${root.toSgfCoord(root.lastCapturedCoord!!)}]")
        if (root.blackPrisoners > 0) output.append("YJBP[${root.blackPrisoners}]")
        if (root.whitePrisoners > 0) output.append("YJWP[${root.whitePrisoners}]")
        if (root.consecutivePasses > 0) output.append("YJPA[${root.consecutivePasses}]")
        val outcome = session.outcome()
        if (outcome.phase == GamePhase.FINISHED) output.append("RE[${if (outcome.winner == null) "0" else (if (outcome.winner == PlayerSide.BLACK) "B+" else "W+") + (outcome.margin?.toString() ?: "R")}]")
        for (tag in listOf("PB", "PW", "DT", "EV")) session.metadata[tag]?.let { output.append("$tag[${sgfValue(it)}]") }
        fun comment(value: String) { if (value.isNotBlank()) output.append("C[${sgfValue(value)}]") }
        comment(session.root.comment)
        val mainNext = session.path(session.liveNodeId).zipWithNext().associate { it.first.id to it.second.id }
        val childrenByParent = session.nodes.groupBy { it.parentId }
        fun node(start: GameNode, principal: Boolean, depth: Int = 0) {
            require(depth < 128) { "SGF 分支嵌套过深" }
            var node = start
            while (true) {
                node.event?.let { event ->
                    when (event.kind) {
                        EventKind.MOVE -> output.append(";${if (event.side == PlayerSide.BLACK) "B" else "W"}[${root.toSgfCoord(event.move!!.to)}]")
                        EventKind.PASS -> output.append(";${if (event.side == PlayerSide.BLACK) "B" else "W"}[]")
                        EventKind.RESUME -> output.append(";YJRS[1]")
                    }
                    comment(node.comment)
                }
                if (principal && node.id == session.liveNodeId) return
                val children = childrenByParent[node.id].orEmpty()
                val chosen = if (principal) children.firstOrNull { it.id == mainNext[node.id] } else
                    children.firstOrNull { it.id == session.preferredChildren[node.id] } ?: children.firstOrNull()
                if (chosen == null) return
                val others = if (variations) children.filter { it.id != chosen.id } else emptyList()
                if (others.isEmpty()) node = chosen else {
                    for (child in listOf(chosen) + others) {
                        output.append("\n("); node(child, principal && child.id == chosen.id, depth + 1); output.append(')')
                    }
                    return
                }
            }
        }
        node(session.root, true)
        return output.append(")\n").toString()
    }

    private data class SgfNode(val properties: Map<String, List<String>>, val children: MutableList<SgfNode> = mutableListOf())

    private class SgfReader(val text: String) {
        var index = 0; var count = 0
        fun whitespace() { while (index < text.length && text[index].isWhitespace()) index++ }
        fun tree(depth: Int = 0): SgfNode {
            require(depth < 128) { "SGF 分支嵌套过深" }
            whitespace(); require(index < text.length && text[index++] == '(') { "SGF 缺少起始括号" }
            var first: SgfNode? = null; var tail: SgfNode? = null
            whitespace()
            while (index < text.length && text[index] == ';') {
                index++; count++; require(count <= 10_000) { "SGF 超过 10000 个节点" }
                val properties = linkedMapOf<String, List<String>>()
                whitespace()
                while (index < text.length && text[index] in 'A'..'Z') {
                    val begin = index
                    while (index < text.length && text[index] in 'A'..'Z') index++
                    val name = text.substring(begin, index); val values = mutableListOf<String>()
                    whitespace()
                    while (index < text.length && text[index] == '[') {
                        index++; val value = StringBuilder()
                        while (index < text.length && text[index] != ']') {
                            if (text[index] == '\\') {
                                index++; require(index < text.length) { "SGF 转义不完整" }
                                if (text[index] == '\r' || text[index] == '\n') {
                                    if (text[index++] == '\r' && index < text.length && text[index] == '\n') index++
                                    continue
                                }
                            }
                            value.append(text[index++])
                        }
                        require(index < text.length) { "SGF 属性未闭合" }; index++; values.add(value.toString()); whitespace()
                    }
                    require(values.isNotEmpty() && name !in properties) { "SGF 属性缺少值或重复：$name" }
                    properties[name] = values
                }
                val node = SgfNode(properties)
                if (first == null) first = node else tail!!.children.add(node)
                tail = node; whitespace()
            }
            require(first != null && tail != null) { "SGF 没有棋局节点" }
            while (index < text.length && text[index] == '(') { tail.children.add(tree(depth + 1)); whitespace() }
            require(index < text.length && text[index++] == ')') { "SGF 括号未闭合" }
            return first
        }
    }

    fun importSgf(text: String): GameSession {
        require(text.length <= MAX_TEXT)
        val reader = SgfReader(text); val root = reader.tree(); reader.whitespace()
        require(reader.index == text.length) { "请导入单局 SGF，当前文件包含多个棋局或多余内容" }
        fun value(node: SgfNode, key: String) = node.properties[key]?.singleOrNull()
        require(value(root, "GM") in listOf(null, "1")) { "仅支持围棋 SGF（GM[1]）" }
        val size = value(root, "SZ")?.toIntOrNull() ?: 19
        require(size in listOf(9, 13, 19)) { "仅支持 9、13、19 路围棋" }
        val board = GoBoard(size)
        fun coord(text: String): BoardCoordinate {
            require(text.length == 2 && text.all { it in 'a' until ('a'.code + size).toChar() }) { "SGF 坐标越界：$text" }
            return BoardCoordinate(text[0] - 'a', text[1] - 'a')
        }
        for ((property, side) in listOf("AB" to PlayerSide.BLACK, "AW" to PlayerSide.WHITE, "AE" to null)) for (entry in root.properties[property].orEmpty()) {
            val ends = entry.split(':'); require(ends.size in 1..2) { "SGF 摆子坐标无效" }
            val first = coord(ends.first()); val last = coord(ends.last())
            require(first.x <= last.x && first.y <= last.y) { "SGF 摆子范围无效" }
            for (y in first.y..last.y) for (x in first.x..last.x) {
                require(side == null || board[x, y] == null) { "SGF 摆子位置重复" }
                board[x, y] = side
            }
        }
        val player = value(root, "PL")
        require(player in listOf(null, "B", "W")) { "SGF 行棋方无效" }
        board.turn = if (player == "W" || (player == null && (value(root, "HA")?.toIntOrNull() ?: 0) >= 2)) PlayerSide.WHITE else PlayerSide.BLACK
        board.lastCapturedCoord = value(root, "YJKO")?.let(::coord)
        board.blackPrisoners = value(root, "YJBP")?.toIntOrNull() ?: 0
        board.whitePrisoners = value(root, "YJWP")?.toIntOrNull() ?: 0
        board.consecutivePasses = value(root, "YJPA")?.toIntOrNull() ?: 0
        val komi = value(root, "KM")?.toDoubleOrNull() ?: 7.5
        require(komi.isFinite() && komi in -100.0..100.0) { "SGF 贴目无效" }
        var session = GameSession.create(GameType.GO, GameMode.PVP, size, PositionSnapshot.of(board)).copy(
            komi = komi, scoringRule = if (value(root, "RU").orEmpty().contains("Japan", true)) GoScoringRule.TERRITORY else GoScoringRule.AREA,
            metadata = root.properties.mapValues { it.value.first() }).rename(value(root, "GN") ?: "导入的围棋棋谱")
        fun visit(start: SgfNode, startParentId: String, rootNode: Boolean): String {
            var node = start
            var parentId = startParentId
            var isRoot = rootNode
            while (true) {
                require(isRoot || node.properties.keys.none { it in listOf("AB", "AW", "AE", "PL") }) { "暂不支持棋谱中途重新摆棋或更改轮次，请拆成独立局面" }
                require(!("B" in node.properties && "W" in node.properties)) { "SGF 同一节点不能同时走黑白两手" }
                session = session.copy(cursorId = parentId, liveNodeId = parentId, declaredOutcome = null)
                if (value(node, "YJRS") == "1") session = session.append(GameEvent(EventKind.RESUME, session.current.position.turn))
                val color = when { "B" in node.properties -> PlayerSide.BLACK; "W" in node.properties -> PlayerSide.WHITE; else -> null }
                if (color != null) {
                    val moveText = value(node, if (color == PlayerSide.BLACK) "B" else "W") ?: throw IllegalArgumentException("SGF 走步值无效")
                    val event = if (moveText.isEmpty() || (moveText == "tt" && size <= 19)) GameEvent(EventKind.PASS, color) else {
                        val target = coord(moveText)
                        GameEvent(EventKind.MOVE, color, MoveRecord(target, target, color.displayName, "${color.displayName} ${coordinateName(GameType.GO, target, size)}"))
                    }
                    session = session.append(event)
                }
                value(node, "C")?.let { session = session.annotate(listOf(session.current.comment, it).filter(String::isNotBlank).joinToString("\n")) }
                val here = session.cursorId
                if (node.children.size == 1) {
                    parentId = here
                    isRoot = false
                    node = node.children.single()
                    continue
                }
                var firstEnd = here
                for ((index, child) in node.children.withIndex()) {
                    val end = visit(child, here, false)
                    if (index == 0) firstEnd = end
                }
                return firstEnd
            }
        }
        val main = visit(root, session.root.id, true)
        session = selectMain(session, main)
        val result = value(root, "RE")
        if (result != null && result !in listOf("?", "Void", "")) {
            val winner = when { result.startsWith("B+", true) -> PlayerSide.BLACK; result.startsWith("W+", true) -> PlayerSide.WHITE; result in listOf("0", "Draw", "Jigo") -> null; else -> throw IllegalArgumentException("未识别 SGF 结果：$result") }
            session = session.finish(winner, "棋谱记录结果：$result", result.substringAfter('+', "").toDoubleOrNull())
        }
        return session.validate()
    }

    private fun selectMain(session: GameSession, end: String): GameSession {
        val preferred = session.nodes.filter { it.parentId != null }.groupBy { it.parentId!! }.mapValues { it.value.first().id }.toMutableMap()
        session.path(end).zipWithNext().forEach { preferred[it.first.id] = it.second.id }
        return session.copy(cursorId = end, liveNodeId = end, preferredChildren = preferred)
    }
}
