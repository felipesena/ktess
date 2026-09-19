# Ktess

Motor de xadrez em Kotlin, construído em fases incrementais, com o objetivo
duplo de:

1. Praticar Kotlin/Java + Spring Boot em um domínio não trivial.
2. Desenvolver intuição sólida sobre estruturas de dados e algoritmos de
   grafo/árvore (BFS, DFS, backtracking, busca em árvore de jogo com poda)
   recorrentes em entrevistas técnicas de big techs.

## Princípios

- O **motor** (`engine/`) é uma lib Kotlin pura, sem dependência de Spring —
  testável isoladamente, sem subir contexto HTTP nenhum.
- O **app** (`app/`) é a camada Spring Boot que expõe o motor via API. Só
  entra depois que o motor já está validado (Fase 7).
- Cada fase tem um critério de saída claro: só avança quando o anterior está
  testado e funcionando.

## Stack

- Kotlin + Gradle (Kotlin DSL)
- Spring Boot (camada de exposição, a partir da Fase 7)
- JUnit5/Kotest para testes
- Ferramentas de nvim já configuradas no dotfiles: `neotest`/`neotest-java`
  (TDD do motor), `nvim-dap`/`jdtls` (debug), `kulala.nvim` (testar endpoints
  HTTP a partir da Fase 7)

## Estrutura de pastas

```
ktess/
├── settings.gradle.kts          # declara os subprojetos (engine, app)
├── gradle.properties
├── gradlew, gradlew.bat
├── gradle/
│   ├── libs.versions.toml       # version catalog — versões centralizadas
│   └── wrapper/
├── build-logic/                 # convention plugins compartilhados (composite build)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── buildlogic.kotlin-common-conventions.gradle.kts
│       ├── buildlogic.kotlin-library-conventions.gradle.kts
│       └── buildlogic.kotlin-application-conventions.gradle.kts
├── engine/                      # motor: lib Kotlin pura, sem Spring
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/personal/ktess/
│       │   └── ...              # board, movegen, state, notation, search, analysis (por fase)
│       └── test/kotlin/         # espelha o main, 1:1
└── app/                         # camada Spring Boot, a partir da Fase 7
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/personal/ktess/app/
        └── test/kotlin/
```

`.gradle/`, `.kotlin/` e `build/` são diretórios gerados (cache do Gradle,
dados do plugin Kotlin, saída de build) — ficam fora do controle de versão
via `.gitignore`, por isso não aparecem na árvore acima.

`build-logic/` centraliza plugins de convenção do Gradle (versão de Kotlin,
toolchain de JVM, repositórios, configuração de teste) compartilhados entre
`engine/` e `app/`, evitando duplicar essa configuração em cada
`build.gradle.kts` — é um *composite build* separado, referenciado em
`settings.gradle.kts` via `includeBuild("build-logic")`.

## Representação de dados

- Tabuleiro: **mailbox** (`Array<Array<Piece?>>`, 8x8) — prioriza legibilidade
  e facilidade de debug sobre performance. Otimizações (0x88, bitboards) só
  entram se performance virar gargalo real (ver Fase 9).
- `Piece(type: PieceType, color: Color)` como `data class`.
- `GameState(board, turn, castlingRights, enPassantTarget, halfmoveClock,
  fullmoveNumber)` guarda tudo que a regra do xadrez exige além da posição
  das peças.

## Fases

### Fase 0 — Esqueleto do projeto

Módulo Gradle Kotlin DSL separado (`engine/`), sem dependência de Spring
ainda. `Position`, `Piece`, `Board` (mailbox 8x8), pretty-print do tabuleiro
em texto. CI básico (`./gradlew test`).

**Critério de saída**: monta a posição inicial e imprime o tabuleiro
corretamente.

### Fase 1 — Geração de movimentos pseudo-legais

Para cada tipo de peça, `movesFor(piece, position, board): List<Move>` — sem
checar se deixa o próprio rei em xeque ainda. Peão por último dentro da fase
(é o mais irregular: captura diagonal ≠ avanço, avanço duplo só na primeira
jogada).

**Critério de saída**: cada peça gera os movimentos geométricos corretos
isoladamente.

### Fase 2 — Legalidade real

- Bloqueio por peças no caminho (torre/bispo/dama param na primeira peça
  encontrada).
- `isSquareAttacked(position, byColor, board)`.
- Filtra pseudo-legais que deixam o próprio rei em xeque.
- Movimentos especiais: roque, en passant, promoção.

**Critério de saída**: motor recusa movimentos ilegais e aceita os especiais
corretamente.

### Fase 3 — Estado de jogo e condições de término

- Xeque-mate / afogamento.
- Empates: repetição tripla, regra dos 50 lances, material insuficiente.

**Critério de saída**: o motor arbitra uma partida do início ao fim sozinho.

### Fase 4 — Notação e testes de correção (FEN + Perft)

- Parser/serializador de FEN.
- **Perft testing**: conta nós da árvore de movimentos até profundidade N e
  compara com valores de referência publicados — forma padrão de achar bugs
  sutis de geração de movimento.
- Notação algébrica (SAN) — opcional, pode adiar.

**Critério de saída**: `perft` bate com os valores de referência em
profundidade 4-5.

### Fase 5 — Motor de busca

- Função de avaliação: material + tabela posicional básica.
- Minimax + poda alfa-beta (DFS na árvore de jogadas).
- Iterative deepening com corte por tempo.
- Extensão opcional: tabela de transposição (hash de posição).

**Critério de saída**: o motor escolhe uma jogada razoável em tempo
aceitável.

### Fase 6 — Análise de posição

Constrói em cima da Fase 5 para responder, dada uma posição qualquer: **quem
está melhor e qual o melhor movimento** — o mesmo tipo de saída que um
engine de análise (Stockfish, etc.) mostra.

- `analyzePosition(state: GameState): PositionAnalysis`, retornando:
  - `score`: avaliação numérica (ex. em centipawns, positivo = vantagem das
    brancas, negativo = das pretas).
  - `verdict`: tradução legível do score (`"Brancas com vantagem clara"`,
    `"Posição equilibrada"`, `"Pretas melhor"`, etc.) — faixas de score
    mapeadas pra rótulos.
  - `bestMove`: melhor jogada encontrada pela busca (Fase 5), em notação SAN
    se a Fase 4 já cobriu isso.
  - Opcional: `principalVariation` — sequência de jogadas esperada
    (melhor resposta do adversário, e assim por diante, até a profundidade
    de busca) — mostra "por que" aquele é o melhor movimento, não só qual é.
- Reaproveita o mesmo minimax/alfa-beta da Fase 5, sem precisar de novo
  algoritmo — a novidade aqui é reportar e formatar avaliação (score, melhor
  linha) e não só devolver o lance escolhido.

**Critério de saída**: dada qualquer posição válida (inclusive carregada via
FEN), o motor devolve avaliação, veredito legível e melhor movimento
consistentes com o que um humano forte concordaria em posições simples/táticas
óbvias (ex. captura de dama de graça deve aparecer como vantagem enorme).

### Fase 7 — Expor via Spring Boot

Agora entra o módulo `app/`, dependendo do `engine/`.

- `POST /games` — cria partida.
- `POST /games/{id}/moves` — submete jogada, valida contra o motor.
- `GET /games/{id}` — estado atual.
- `POST /games/{id}/ai-move` — pede jogada do motor.
- `GET /games/{id}/analysis` — expõe a Fase 6 (score, veredito, melhor
  movimento, variante principal) para a posição atual da partida.
- Se quiser partida em tempo real entre dois humanos: WebSocket em vez de só
  REST.
- Testar os endpoints com `kulala.nvim` (arquivos `.http` versionados no
  repo).

**Critério de saída**: dá pra jogar uma partida completa via requisições
HTTP, contra o motor ou entre dois "clientes", e consultar a análise da
posição a qualquer momento.

### Fase 8 — Extensões (opcional)

- Persistência (salvar/retomar partidas — Postgres).
- Livro de aberturas, avaliação melhor (piece-square tables mais refinadas).
- Migração pra bitboards, só se performance virar gargalo real.

## Como cada fase usa o setup do nvim

- **Fases 1-6**: `neotest`/`neotest-java` para TDD do motor (sem Spring no
  meio), `nvim-dap` para debugar geração de movimento e a busca do minimax
  passo a passo.
- **Fase 7**: `jdtls`/debug de Spring, `kulala.nvim` para testar os
  endpoints HTTP.
