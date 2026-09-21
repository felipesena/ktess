package com.personal.ktess.board

data class Position(val file: Int, val rank: Int) {
    companion object {
        fun fromAlgebraic(s: String): Position {
            return Position(1, 1)
        }
    }

    fun toAlgebraic(): String = "a1"
}
