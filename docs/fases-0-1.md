# Fases 0 e 1 — Detalhamento

Visão de alto nível das classes de cada fase e como elas interagem entre si.
Sem detalhes de implementação — foco em responsabilidade de cada classe e no
fluxo de chamadas.

## Fase 0 — Esqueleto do projeto (`board/`)

### Classes

- **`Position`** — representa uma casa do tabuleiro (coluna/linha, 0-7 cada).
  Sabe converter de/para notação algébrica (`"e4"`). Não conhece `Board` nem
  `Piece`.
- **`Piece`** — representa uma peça: tipo (`PieceType`) + cor (`PieceColor`).
  Não conhece `Board` nem `Position`.
- **`Board`** — mailbox 8x8 (`Position -> Piece?`). Imutável: qualquer
  alteração retorna uma nova instância em vez de mutar a existente. Sabe
  montar a posição inicial e se imprimir em texto (pretty-print).

### Esqueleto

```kotlin
data class Position(val file: Int, val rank: Int) {
    companion object {
        fun fromAlgebraic(s: String): Position
    }

    fun toAlgebraic(): String
}
```

```kotlin
data class Piece(val type: PieceType, val color: PieceColor)

enum class PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }
enum class PieceColor { WHITE, BLACK }
```

```kotlin
class Board {
    operator fun get(pos: Position): Piece?
    fun with(pos: Position, piece: Piece?): Board

    companion object {
        fun empty(): Board
        fun initialPosition(): Board
    }

    override fun toString(): String
}
```

### Interações

```
Position ──┐
           ├──> Board.initialPosition() monta o tabuleiro
Piece   ───┘

Board.toString() percorre as Position de rank 8→1 / file a→h e consulta
Board.get(Position) para saber qual Piece (ou nenhuma) está em cada casa.
```

`Position` e `Piece` são independentes entre si — só se relacionam através de
`Board`, que é quem associa "qual peça está em qual casa".

A imutabilidade de `Board` já é pensada com a Fase 5 em mente (busca via
minimax gera/descarta milhares de posições por segundo — mutação
compartilhada é fonte clássica de bug nesse cenário).

### Critério de saída

Montar a posição inicial e imprimir o tabuleiro corretamente.

---

## Fase 1 — Geração de movimentos pseudo-legais (`movegen/`)

### Classes

- **`Move`** — representa um lance candidato: casa de origem, casa de
  destino, peça movida, peça capturada (se houver). Não valida legalidade
  (xeque, roque, en passant) — isso fica para a Fase 2.
- **`MoveGenerator`** — ponto de entrada da geração de movimentos. Para cada
  tipo de peça, aplica a geometria correspondente (cavalo, peças que
  deslizam — bispo/torre/dama —, rei, peão). O peão é implementado por
  último por ser o mais irregular (captura diagonal diferente de avanço,
  avanço duplo só no primeiro lance).

### Esqueleto

```kotlin
data class Move(
    val from: Position,
    val to: Position,
    val piece: Piece,
    val capturedPiece: Piece? = null
)
```

```kotlin
object MoveGenerator {
    fun pseudoLegalMoves(board: Board, from: Position): List<Move>
}
```

### Interações

```
Board ──> MoveGenerator (recebe Board + Position de origem)
              │
              ├─ consulta Board.get(origem) para saber qual Piece está lá
              ├─ usa Position para calcular casas candidatas conforme o tipo
              │  de peça
              ├─ consulta Board.get(destino) para saber se a casa está
              │  vazia, tem captura ou bloqueia o caminho
              └─ devolve uma lista de Move
```

`GameState` não participa desta fase — a geração pseudo-legal depende só de
`Board` e `Position`. Informações como turno, direitos de roque e alvo de en
passant só entram na Fase 2, quando a legalidade real (xeque, roque, en
passant) é verificada.

### Critério de saída

Cada peça gera, isoladamente, os movimentos geométricos corretos (sem ainda
considerar se o lance deixa o próprio rei em xeque).
