package com.personal.ktess.board

data class Piece(val type: PieceType, val color: PieceColor)

enum class PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }
enum class PieceColor { WHITE, BLACK }
