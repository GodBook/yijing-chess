package com.yijing.chess.model

/** A move's actor is recorded by GameEvent, so passes never need fake coordinates. */
data class MoveRecord(
    val from: BoardCoordinate,
    val to: BoardCoordinate,
    val pieceText: String,
    val notation: String,
    val capturedText: String? = null,
    val promotion: String? = null
)
