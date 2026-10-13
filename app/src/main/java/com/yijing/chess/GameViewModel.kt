package com.yijing.chess

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yijing.chess.chess.*
import com.yijing.chess.go.GoAI
import com.yijing.chess.model.*
import com.yijing.chess.parser.NotationCodec
import com.yijing.chess.session.*
import com.yijing.chess.storage.AppPreferences
import com.yijing.chess.xiangqi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppPage { HOME, GAME, LIBRARY, LESSONS }
data class UiMessage(val id: Long, val text: String, val undoDelete: Boolean = false)
data class PromotionRequest(val from: BoardCoordinate, val to: BoardCoordinate)
data class AppState(
    val loaded: Boolean = false,
    val page: AppPage = AppPage.HOME,
    val sessions: List<GameSession> = emptyList(),
    val currentId: String? = null,
    val preferences: AppPreferences = AppPreferences(),
    val selected: BoardCoordinate? = null,
    val legalTargets: List<BoardCoordinate> = emptyList(),
    val hint: MoveRecord? = null,
    val hintText: String = "",
    val thinking: Boolean = false,
    val hintThinking: Boolean = false,
    val aiPaused: Boolean = false,
    val pendingGo: BoardCoordinate? = null,
    val promotion: PromotionRequest? = null,
    val editorPiece: Char? = null,
    val outcome: GameOutcome = GameOutcome(),
    val saveStatus: String = "",
    val flipped: Boolean = false,
    val message: UiMessage? = null,
    val lessonSolved: Boolean = false,
    val pendingImport: GameSession? = null,
    val importing: Boolean = false
) {
    val current: GameSession? get() = sessions.firstOrNull { it.id == currentId }
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as YiJingApplication
    private val repository = app.repository
    private val _state = MutableStateFlow(AppState(preferences = repository.preferences()))
    val state = _state.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0L
    private var messageId = 0L

    init {
        viewModelScope.launch {
            runCatching { repository.load() }.onSuccess { library ->
                _state.update { it.copy(loaded = true, sessions = library.sessions) }
                if (library.warnings.isNotEmpty()) notify(library.warnings.joinToString("\n"))
            }.onFailure { _state.update { it.copy(loaded = true) }; notify("棋局库读取失败，现有文件已保留：${it.message}") }
        }
    }

    fun notify(text: String, undoDelete: Boolean = false) { _state.update { it.copy(message = UiMessage(++messageId, text, undoDelete)) } }
    fun consumeMessage(id: Long) { _state.update { if (it.message?.id == id) it.copy(message = null) else it } }

    fun navigate(page: AppPage) {
        cancelSearch()
        persistCurrent()
        _state.update { it.copy(page = page, selected = null, legalTargets = emptyList(), pendingGo = null, promotion = null) }
        if (page == AppPage.GAME) triggerAi()
    }

    fun open(session: GameSession) {
        cancelSearch()
        val live = session.returnToLive()
        setSession(live)
        _state.update { it.copy(page = AppPage.GAME, currentId = live.id, selected = null, legalTargets = emptyList(),
            hint = null, hintText = "", pendingGo = null, promotion = null, flipped = live.humanSide != live.gameType.firstSide(),
            outcome = live.outcome(), lessonSolved = false, editorPiece = null, aiPaused = false) }
        live.migrationNotice?.let(::notify)
        triggerAi()
    }

    fun newGame(type: GameType, mode: GameMode, size: Int = 19, human: PlayerSide = type.firstSide(), difficulty: Int = 2,
        thinkMillis: Long = 2500, komi: Double = 7.5, rule: GoScoringRule = GoScoringRule.AREA) {
        val session = GameSession.create(type, mode, size).copy(humanSide = human, difficulty = difficulty,
            thinkMillis = thinkMillis, komi = komi, scoringRule = rule)
        open(session)
    }

    fun restart() {
        val current = _state.value.current ?: return
        newGame(current.gameType, current.mode, current.current.position.boardSize, current.humanSide, current.difficulty,
            current.thinkMillis, current.komi, current.scoringRule)
    }

    private fun setSession(session: GameSession, save: Boolean = true) {
        _state.update { old ->
            val sessions = (old.sessions.filter { it.id != session.id } + session).sortedByDescending { it.updatedAt }
            old.copy(sessions = sessions, outcome = if (old.currentId == session.id) session.outcome() else old.outcome,
                saveStatus = if (save && old.currentId == session.id) "正在保存…" else old.saveStatus)
        }
        if (save) persist(session)
    }

    private fun persist(session: GameSession) {
        app.applicationScope.launch {
            runCatching { repository.save(session) }.onSuccess {
                _state.update { if (it.currentId == session.id && it.current?.revision == session.revision)
                    it.copy(saveStatus = if (session.isDraft) "草稿已自动保存" else "已自动保存") else it }
            }.onFailure {
                _state.update { value -> if (value.currentId == session.id) value.copy(saveStatus = "保存失败，点击重试") else value }
                notify("保存失败，棋局仍保留在当前页面：${it.message}")
            }
        }
    }

    fun persistCurrent() { _state.value.current?.let(::persist) }

    private inline fun change(action: (GameSession) -> GameSession) {
        val session = _state.value.current ?: return
        cancelSearch()
        runCatching { action(session) }.onSuccess { next ->
            setSession(next)
            _state.update { it.copy(selected = null, legalTargets = emptyList(), hint = null, hintText = "", pendingGo = null, promotion = null, outcome = next.outcome()) }
        }.onFailure { notify(it.message ?: "操作未完成") }
    }

    fun tap(coord: BoardCoordinate) {
        val state = _state.value; val session = state.current ?: return
        if (session.isDraft) { editSquare(coord); return }
        if (state.outcome.phase == GamePhase.SCORING && !session.isReview) { change { it.toggleDead(coord) }; return }
        if (session.isReview) { notify("正在查看历史，返回实战或选择从此推演后走子"); return }
        if (state.outcome.phase == GamePhase.FINISHED || state.lessonSolved || state.thinking) return
        if (session.mode == GameMode.PVE && session.current.position.turn != session.humanSide) { notify("轮到 AI，请继续思考或切换双人模式"); return }
        if (session.gameType == GameType.GO) {
            val board = session.current.position.go()
            if (!board.isMoveLegal(coord, board.turn)) { notify(if (coord == board.lastCapturedCoord) "这里是劫点，请先在别处走一手" else "此处已有棋子或落子后无气"); return }
            if (board.size == 19 && state.preferences.confirmGo19 && state.pendingGo != coord) { _state.update { it.copy(pendingGo = coord) }; return }
            performMove(coord, coord)
            return
        }
        val pieceSide = when (session.gameType) {
            GameType.CHESS -> session.current.position.chess()[coord]?.side
            else -> session.current.position.xiangqi()[coord]?.side
        }
        if (state.selected == coord) { _state.update { it.copy(selected = null, legalTargets = emptyList()) }; return }
        if (pieceSide == session.current.position.turn) {
            val targets = SessionRules.legalMoves(session.current.position, false).filter { it.from == coord }.map { it.to }.distinct()
            _state.update { it.copy(selected = coord, legalTargets = targets) }
        } else if (state.selected != null && coord in state.legalTargets) {
            val legal = SessionRules.legalMoves(session.current.position).filter { it.from == state.selected && it.to == coord }
            if (legal.any { it.promotion != null }) _state.update { it.copy(promotion = PromotionRequest(state.selected, coord)) }
            else performMove(state.selected, coord)
        } else {
            _state.update { it.copy(selected = null, legalTargets = emptyList()) }
        }
    }

    fun confirmGo() { _state.value.pendingGo?.let { performMove(it, it) } }
    fun cancelGo() { _state.update { it.copy(pendingGo = null) } }
    fun promote(type: ChessPieceType) { _state.value.promotion?.let { performMove(it.from, it.to, type.name) } }
    fun cancelPromotion() { _state.update { it.copy(promotion = null) } }

    fun performMove(from: BoardCoordinate, to: BoardCoordinate, promotion: String? = null) {
        val session = _state.value.current ?: return
        val lesson = Lessons.all.firstOrNull { it.id == session.lessonId }
        if (lesson != null && (lesson.from != from || lesson.to != to)) { notify("再想一想：${lesson.description}"); return }
        change { it.play(from, to, promotion) }
        if (lesson != null && _state.value.current?.moveCount == 1) {
            _state.update { it.copy(lessonSolved = true, hintText = lesson.explanation) }
            notify("完成练习：${lesson.explanation}")
        } else triggerAi()
    }

    fun pass() {
        val session = _state.value.current ?: return
        if (session.mode == GameMode.PVE && session.current.position.turn != session.humanSide) return
        change { it.pass() }; triggerAi()
    }
    fun undo() { change { it.undo() }; _state.update { it.copy(lessonSolved = false) }; triggerAi() }
    fun flip() { _state.update { it.copy(flipped = !it.flipped) } }
    fun review(nodeId: String) { change { it.review(nodeId) } }
    fun previous() { change { it.previous() } }
    fun next() { change { it.next() } }
    fun returnToLive() { change { it.returnToLive() }; triggerAi() }
    fun branchHere() { change { it.branchHere() }; triggerAi() }
    fun annotate(comment: String) { change { it.annotate(comment) }; triggerAi() }
    fun rename(name: String) { change { it.rename(name) }; triggerAi() }
    fun renameSaved(session: GameSession, name: String) { setSession(session.rename(name)) }

    fun setMode(mode: GameMode, human: PlayerSide, difficulty: Int, budget: Long) {
        val session = _state.value.current ?: return
        if (mode == GameMode.EDITOR && !session.isDraft) { startEditor(); return }
        change { it.copy(mode = mode, humanSide = human, difficulty = difficulty, thinkMillis = budget).touch() }
        triggerAi()
    }

    fun startEditor() {
        val session = _state.value.current ?: return
        val draft = GameSession.create(session.gameType, GameMode.EDITOR, session.current.position.boardSize, session.current.position)
            .copy(title = "${session.gameType.displayName}摆谱草稿", komi = session.komi, scoringRule = session.scoringRule)
        open(draft)
    }

    fun startDraft(mode: GameMode, human: PlayerSide, difficulty: Int, budget: Long) {
        val draft = _state.value.current ?: return
        val errors = SessionRules.playableErrors(draft.current.position)
        if (errors.isNotEmpty()) { notify(errors.joinToString("；")); return }
        open(GameSession.create(draft.gameType, mode, draft.current.position.boardSize, draft.current.position).copy(
            humanSide = human, difficulty = difficulty, thinkMillis = budget, komi = draft.komi, scoringRule = draft.scoringRule))
    }

    fun selectEditorPiece(piece: Char?) { _state.update { it.copy(editorPiece = piece) } }
    private fun editSquare(coord: BoardCoordinate) {
        val piece = _state.value.editorPiece
        change { session ->
            val position = session.current.position
            val next = when (session.gameType) {
                GameType.CHESS -> position.chess().let { board -> board[coord] = piece?.let(ChessPiece::fromFenChar); PositionSnapshot.of(board) }
                GameType.XIANGQI -> position.xiangqi().let { board -> board[coord] = piece?.let(XiangqiPiece::fromFenChar); PositionSnapshot.of(board) }
                GameType.GO -> position.go().let { board ->
                    board[coord] = when (piece) { 'B' -> PlayerSide.BLACK; 'W' -> PlayerSide.WHITE; else -> null }
                    board.lastCapturedCoord = null; board.consecutivePasses = 0; PositionSnapshot.of(board)
                }
            }
            session.replaceDraft(next)
        }
    }

    fun clearDraft(initial: Boolean = false) { change { it.replaceDraft(if (initial) SessionRules.initial(it.gameType, it.current.position.boardSize) else SessionRules.empty(it.gameType, it.current.position.boardSize)) } }
    fun draftTurn(side: PlayerSide) { change { session ->
        val position = session.current.position
        val next = when (session.gameType) {
            GameType.CHESS -> position.chess().let { it.turn = side; PositionSnapshot.of(it) }
            GameType.XIANGQI -> position.xiangqi().let { it.turn = side; PositionSnapshot.of(it) }
            GameType.GO -> position.go().let { it.turn = side; PositionSnapshot.of(it) }
        }
        session.replaceDraft(next)
    } }

    fun draftChessRights(rights: String, enPassant: String) { change { session ->
        require(session.gameType == GameType.CHESS && session.isDraft)
        val parts = session.current.position.state.split(" ").toMutableList()
        parts[2] = rights.ifBlank { "-" }; parts[3] = enPassant.trim().ifBlank { "-" }
        session.replaceDraft(PositionSnapshot.of(ChessBoard.fromFen(parts.joinToString(" "))))
    } }

    fun resumeGo() { change { it.resumeGo() }; triggerAi() }
    fun confirmScore() { change { session -> val score = scoreGo(session)
        session.finish(score.winner, "黑 ${score.black} · 白 ${score.white}（含贴目 ${session.komi}）", score.margin)
    } }
    fun resign() { change { val side = if (it.mode == GameMode.PVE) it.humanSide else it.current.position.turn
        it.finish(it.gameType.opposite(side), "${side.displayName}认输") } }
    fun agreeDraw() { change { it.finish(null, "双方同意和棋") } }
    fun claimDraw() { change { it.finish(null, requireNotNull(SessionRules.drawClaim(it))) } }

    fun updatePreferences(preferences: AppPreferences) {
        _state.update { it.copy(preferences = preferences) }
        app.applicationScope.launch { runCatching { repository.savePreferences(preferences) }.onFailure { notify("设置保存失败：${it.message}") } }
    }

    fun importText(text: String) = viewModelScope.launch {
        _state.update { it.copy(importing = true) }
        runCatching { withContext(Dispatchers.Default) { NotationCodec.importText(text) } }
            .onSuccess { imported -> _state.update { it.copy(pendingImport = imported) } }
            .onFailure { notify("导入失败：${it.message}") }
        _state.update { it.copy(importing = false) }
    }
    fun confirmImport() { _state.value.pendingImport?.let(::open); _state.update { it.copy(pendingImport = null) } }
    fun cancelImport() { _state.update { it.copy(pendingImport = null) } }

    fun delete(session: GameSession) {
        if (_state.value.currentId == session.id) { cancelSearch(); _state.update { it.copy(currentId = null, page = AppPage.LIBRARY) } }
        app.applicationScope.launch {
            runCatching { repository.delete(session) }.onSuccess {
                _state.update { value -> value.copy(sessions = value.sessions.filter { it.id != session.id }) }
                notify("已删除「${session.title}」", true)
            }.onFailure { notify("删除失败：${it.message}") }
        }
    }

    fun undoDelete() = app.applicationScope.launch {
        runCatching { repository.undoDelete() }.onSuccess { restored -> restored?.let { setSession(it); notify("已恢复棋局") } }.onFailure { notify("恢复失败：${it.message}") }
    }

    fun openLesson(lesson: Lesson) = open(lesson.session())
    fun stopThinking() { cancelSearch(); _state.update { it.copy(aiPaused = true) } }
    fun continueThinking() { _state.update { it.copy(aiPaused = false) }; triggerAi() }
    fun hint() { if (!_state.value.thinking && !_state.value.hintThinking) search(hintOnly = true) }

    private fun triggerAi() {
        val state = _state.value; val session = state.current ?: return
        if (state.page == AppPage.GAME && session.mode == GameMode.PVE && !session.isReview && !state.aiPaused &&
            session.current.position.turn != session.humanSide && session.outcome().phase == GamePhase.PLAYING) search(hintOnly = false)
    }

    private fun cancelSearch() {
        generation++; searchJob?.cancel(); searchJob = null
        _state.update { it.copy(thinking = false, hintThinking = false, aiPaused = false) }
    }

    private fun search(hintOnly: Boolean) {
        val session = _state.value.current ?: return
        if (session.isDraft || session.outcome().phase != GamePhase.PLAYING) return
        cancelSearch()
        val token = generation
        _state.update { it.copy(thinking = !hintOnly, hintThinking = hintOnly, hint = null, hintText = "", pendingGo = null) }
        searchJob = viewModelScope.launch {
            try {
                val position = session.current.position
                val move = when (session.gameType) {
                    GameType.CHESS -> ChessAI.findBestMove(position.chess(), position.turn, session.difficulty, session.thinkMillis).move
                    GameType.XIANGQI -> XiangqiAI.findBestMove(position.xiangqi(), position.turn, session.difficulty, session.thinkMillis).move
                    GameType.GO -> {
                        data class Choice(val point: BoardCoordinate?)
                        val choice = withTimeoutOrNull(session.thinkMillis) { Choice(GoAI.findBestMove(position.go(), position.turn, session.difficulty)) }
                        val point = if (choice != null) choice.point else withContext(Dispatchers.Default) { SessionRules.legalMoves(position).firstOrNull()?.to }
                        point?.let { MoveRecord(it, it, position.turn.displayName, "${position.turn.displayName} ${coordinateName(GameType.GO, it, position.boardSize)}") }
                    }
                }
                ensureActive()
                val live = _state.value.current
                if (token != generation || live?.id != session.id || live.revision != session.revision || live.current.position != position) return@launch
                _state.update { it.copy(thinking = false, hintThinking = false) }
                if (hintOnly) {
                    _state.update { it.copy(hint = move, hintText = if (move == null) "建议停着，检查双方是否准备结算" else explainHint(session, move)) }
                } else {
                    val next = if (move == null && session.gameType == GameType.GO) session.pass() else if (move != null) session.play(move.from, move.to, move.promotion) else session
                    setSession(next)
                    _state.update { it.copy(outcome = next.outcome(), selected = null, legalTargets = emptyList()) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (token == generation) { _state.update { it.copy(aiPaused = !hintOnly) }; notify("计算未完成，可重试：${error.message}") } }
            finally { if (token == generation) _state.update { it.copy(thinking = false, hintThinking = false) } }
        }
    }

    private fun explainHint(session: GameSession, move: MoveRecord): String {
        val purpose = when {
            move.notation.endsWith('#') -> "这步可以形成将死"
            move.notation.endsWith('+') -> "这步将军，迫使对方应将"
            session.gameType == GameType.GO -> {
                val before = session.current.position
                val after = SessionRules.after(before, GameEvent(EventKind.MOVE, before.turn, move))
                val captures = after.blackPrisoners + after.whitePrisoners - before.blackPrisoners - before.whitePrisoners
                if (captures > 0) "这步可以提掉 $captures 颗棋子" else "参考方向：补气、连接与争取空间"
            }
            move.capturedText != null -> "这步可以吃掉对方棋子"
            else -> "参考方向：改善子力位置并保持局面协调"
        }
        return "推荐 ${move.notation} · $purpose"
    }

    override fun onCleared() { cancelSearch(); super.onCleared() }
}
