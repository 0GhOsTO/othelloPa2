# Othello AI Agent

A Java-based adversarial search agent for **Othello (Reversi)** that selects moves using **minimax search with alpha-beta pruning**, adaptive search depth, heuristic move ordering, and transposition-table memoization.

The project was built on top of the included Othello game framework and focuses on efficient decision-making under a per-move search budget.

## Highlights

- **Minimax adversarial search** for selecting the strongest available move
- **Alpha-beta pruning** to eliminate branches that cannot affect the final decision
- **Adaptive search depth** based on game phase:
  - early game: depth 3
  - midgame: depth 4
  - late game: depth 6
- **Transposition table** backed by a `ConcurrentHashMap` to reuse previously evaluated board states
- **Heuristic move ordering** so promising moves are explored first, improving alpha-beta pruning effectiveness
- **Pass-state handling** when a player has no legal move
- **Phase-aware board evaluation** that changes priorities as the board develops

## Evaluation Function

Non-terminal board states are scored with a weighted combination of Othello-specific strategic features.

### Corner control
Corners are highly valuable because they can never be flipped after capture. The heuristic strongly rewards controlled corners.

### Corner-adjacent penalties
Squares directly beside an empty corner are penalized because occupying them can allow the opponent to take the corner.

### Edge control
Stable edge positions receive additional value, while remaining less important than corners.

### Mobility
The agent compares the number of legal moves available to each player. Mobility is weighted more heavily during the opening and middle game, when restricting the opponent's choices is especially important.

### Potential mobility
The evaluation also considers empty squares adjacent to existing pieces as an approximation of future move opportunities.

### Positional weighting
An 8x8 positional weight matrix rewards strategically useful squares and penalizes dangerous positions, particularly squares near unsecured corners.

### Piece differential
Piece count is weighted according to game phase. Early in the game, immediately maximizing pieces is not necessarily desirable; in the endgame, piece advantage becomes much more important.

### Endgame parity
Late-game evaluation considers the parity of the remaining empty squares to estimate which player is more likely to make the final move.

The combined heuristic is normalized to the same `[-1, 1]` range used by terminal win/loss utilities.

## Search Pipeline

For each turn, the agent performs the following process:

1. Creates a root node from the current game state.
2. Determines an appropriate search depth from the number of occupied squares.
3. Generates all legal child states, including pass states when required.
4. Orders candidate moves using heuristic evaluations.
5. Runs minimax recursively with alpha-beta pruning.
6. Reuses cached board evaluations through the transposition table.
7. Returns the move associated with the highest-value searched child.

## Project Structure

```text
.
├── lib/
│   ├── argparse4j-0.9.0.jar
│   ├── hamcrest-2.2.jar
│   ├── junit-4.12.jar
│   └── othello-0.0.1.jar
├── src/pas/othello/
│   ├── agents/
│   │   └── OthelloAgent.java
│   ├── heuristics/
│   │   └── Heuristics.java
│   └── ordering/
│       └── MoveOrderer.java
└── othello.srcs
```

### `OthelloAgent.java`
Implements game-tree construction, legal move generation, pass handling, adaptive-depth minimax, alpha-beta pruning, terminal utilities, and transposition-table caching.

### `Heuristics.java`
Evaluates non-terminal game states using strategic Othello features such as corner control, mobility, edge control, positional value, piece differential, parity, and potential mobility.

### `MoveOrderer.java`
Ranks child states before search so high-value moves are considered first at maximizing levels and low-value moves first at minimizing levels, increasing the opportunity for alpha-beta cutoffs.

## Building

The repository includes the required Java libraries in `lib/` and lists the project source files in `othello.srcs`.

On macOS/Linux:

```bash
javac -cp "lib/*" @othello.srcs
```

On Windows Command Prompt:

```bat
javac -cp "lib/*" @othello.srcs
```

The agent depends on the Othello framework contained in `lib/othello-0.0.1.jar`; gameplay is driven through that framework rather than a standalone GUI in this repository.

## Core Algorithms

| Component | Technique |
|---|---|
| Game-tree search | Minimax |
| Search optimization | Alpha-beta pruning |
| State reuse | Transposition-table memoization |
| Search depth | Adaptive by game phase |
| Branch ordering | Heuristic move ordering |
| Board evaluation | Multi-feature phase-aware heuristic |
| Terminal scoring | Win `+1`, loss `-1`, tie `0` |

## Tech Stack

- Java
- Adversarial search
- Minimax
- Alpha-beta pruning
- Memoization / transposition tables
- Heuristic search
- Object-oriented game-state modeling

## Notes

This repository contains the AI agent implementation and supporting evaluation/search logic. Compiled `.class` files and the framework dependencies are also included in the repository.