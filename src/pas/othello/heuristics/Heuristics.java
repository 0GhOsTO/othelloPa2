package src.pas.othello.heuristics;

// SYSTEM IMPORTS
import edu.bu.pas.othello.traversal.Node;

// JAVA PROJECT IMPORTS

import edu.bu.pas.othello.game.Game.GameView;
import edu.bu.pas.othello.game.PlayerType;
import edu.bu.pas.othello.utils.Coordinate;
import java.util.Set;

public class Heuristics
        extends Object {
    
    // This function gives the board a score.
    // It looks at corners, edges, number of moves, piece counts, then
    // combines them into one value.
    // The output is a a number between -1 and 1 (higher is better for the max player).
    public static double calculateHeuristicValue(Node node) {
        // TODO: complete me!

        GameView view = node.getGameView();
        PlayerType maxPlayer = node.getMaxPlayerType();
        PlayerType minPlayer = (maxPlayer == view.getCurrentPlayerType()) ? view.getOtherPlayerType()
                : view.getCurrentPlayerType();

        // Count total pieces on board
        int totalPieces = countTotalPieces(view.getCells());
        
        // Taking a corner is very important in Othello
        double cornerScore = calcCornerScore(view, maxPlayer, minPlayer);

        // If you are adjacent to an empty corner, there is a penalty
        double cornerAdjacentPenalty = calcCornerAdjacentPenalty(view, maxPlayer, minPlayer);

        // edge pieces are also valuable
        double edgeScore = calEdgeScore(view, maxPlayer, minPlayer);

        // Mobility: number of legal moves available
        // More important in opening/midgame, less in endgame
        double mobilityScore = calcMobilityScore(view, maxPlayer, minPlayer, totalPieces);

        // the piece count matters different in different stages of the game.
        // in the beginning, having fewer pieces is better.
        // in the end, having more pieces is better.
        double pieceScore = calcAdaptivePieceScore(view, maxPlayer, minPlayer, totalPieces);

        // this is a regular positiaonal score based on a weight matrix,
        // does not include corners and edges.
        double positionalScore = calcPositionalScore(view, maxPlayer, minPlayer);

        // Trying to make the last move towards the end of the game.
        double parityScore = calcParityScore(view, maxPlayer, totalPieces);

        // the number of empty squares to your pieces
        // checking the future mobility potential.
        double potentialMobilityScore = calcPotentialMobilityScore(view, maxPlayer, minPlayer, totalPieces);

        // We need to weight it differently based on game phase
        // Return the value between -1.0 and 1.0
        double totalScore = cornerScore + cornerAdjacentPenalty + edgeScore + mobilityScore + 
                           pieceScore + positionalScore + parityScore + 
                           potentialMobilityScore;

        // Clamp the result to [-1.0, 1.0] to match terminal utility range
        return Math.max(-1.0, Math.min(1.0, totalScore));
    }

    // Helper to count total pieces on board
    private static int countTotalPieces(PlayerType[][] cells) {
        int count = 0;
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                if (cells[i][j] != null) {
                    count++;
                }
            }
        }
        return count;
    }

    // Adaptive piece score based on game phase
    private static double calcAdaptivePieceScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer, int totalPieces) {
        PlayerType[][] cells = view.getCells();
        int maxCnt = 0;
        int minCnt = 0;

        // Count pieces
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                PlayerType owner = cells[i][j];
                if (owner == maxPlayer) {
                    maxCnt++;
                } else if (owner == minPlayer) {
                    minCnt++;
                }
            }
        }

        if (maxCnt + minCnt == 0) {
            return 0.0;
        }

        double ratio = (double) (maxCnt - minCnt) / (maxCnt + minCnt);
        
        // Weighting based on the current game phase. 
        double weight;
        if (totalPieces <= 16) {
            // Start with the fewer amount of the pieces.
            weight = -0.05;
        } else if (totalPieces <= 48) {
            // Piece do not matter
            weight = 0.05;
        } else {
            // Piece count is extremely important at the end. 
            weight = 0.5;
        }
        
        return weight * ratio;
    }

    // Penalty for having pieces adjacent to empty corners
    private static double calcCornerAdjacentPenalty(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int boardSize = cells.length;
        double maxPenalty = 0.0;
        double minPenalty = 0.0;

        // Check all 4 corners and their adjacent squares
        int[][] corners = {{0, 0}, {0, boardSize-1}, {boardSize-1, 0}, {boardSize-1, boardSize-1}};
        
        for (int[] corner : corners) {
            int cr = corner[0];
            int cc = corner[1];
            
            // If corner is EMPTY, penalize adjacent squares heavily
            if (cells[cr][cc] == null) {
                // Diagonal to the corner. VERY BAD
                int xr = (cr == 0) ? 1 : boardSize - 2;
                int xc = (cc == 0) ? 1 : boardSize - 2;
                if (cells[xr][xc] == maxPlayer) {
                    maxPenalty += 0.15; // Heavy penalty for X-square next to empty corner
                } else if (cells[xr][xc] == minPlayer) {
                    minPenalty += 0.15;
                }
                
                // Orthogonal to the corner. ALSO BAD
                int c1r = (cr == 0) ? 1 : boardSize - 2;
                int c2c = (cc == 0) ? 1 : boardSize - 2;
                if (cells[c1r][cc] == maxPlayer) {
                    maxPenalty += 0.10;
                } else if (cells[c1r][cc] == minPlayer) {
                    minPenalty += 0.10;
                }
                if (cells[cr][c2c] == maxPlayer) {
                    maxPenalty += 0.10;
                } else if (cells[cr][c2c] == minPlayer) {
                    minPenalty += 0.10;
                }
            }
        }
        
        // Return negative penalty (BAD SQUARE)
        return -(maxPenalty - minPenalty);
    }

    // Mobility calculation
    private static double calcMobilityScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer, int totalPieces) {
        
        // Get number of legal moves for each player
        Set<Coordinate> maxMoves = view.getFrontier(maxPlayer);
        Set<Coordinate> minMoves = view.getFrontier(minPlayer);

        int maxMoveCount = maxMoves.size();
        int minMoveCount = minMoves.size();

        if (maxMoveCount + minMoveCount == 0) {
            return 0.0;
        }

        double ratio = (double) (maxMoveCount - minMoveCount) / (maxMoveCount + minMoveCount);
        
        // Mobility is MORE important in opening/midgame, LESS important in endgame
        double weight;
        if (totalPieces <= 30) {
            weight = 0.20; // Very important early
        } else if (totalPieces <= 50) {
            weight = 0.15; // Still important mid-game
        } else {
            weight = 0.05; // Less important in endgame
        }
        
        // Return weighted mobility score
        return weight * ratio;
    }

    // Trying to make the last move in the end session of the game. 
    private static double calcParityScore(GameView view, PlayerType maxPlayer, int totalPieces) {
        int emptySquares = 64 - totalPieces;
        
        // Only matters in late game
        if (totalPieces < 50) {
            return 0.0;
        }
        
        // In Othello, having the last move is advantageous in endgame
        // If ODD number of empty squares remain, the player to move now gets the last move
        // If EVEN number remain, the opponent gets the last move
        PlayerType currentPlayer = view.getCurrentPlayerType();
        boolean maxPlayerToMove = (currentPlayer == maxPlayer);
        
        if (emptySquares % 2 == 1) {
            // Odd squares: current player gets last move
            return maxPlayerToMove ? 0.02 : -0.02;
        } else {
            // Even squares: opponent gets last move
            return maxPlayerToMove ? -0.02 : 0.02;
        }
    }

    // Counting the empty spaces adjacent to the enemy's pieces.
    private static double calcPotentialMobilityScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer, int totalPieces) {
        // Only relevant in opening/midgame
        if (totalPieces > 48) {
            return 0.0;
        }
        
        PlayerType[][] cells = view.getCells();
        int boardSize = cells.length;
        int maxPotential = 0;
        int minPotential = 0;

        // Count empty squares adjacent to each player's pieces
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                if (cells[i][j] == null) {
                    // This square is empty
                    boolean adjacentToMax = false;
                    boolean adjacentToMin = false;
                    
                    for (int di = -1; di <= 1; di++) {
                        for (int dj = -1; dj <= 1; dj++) {
                            if (di == 0 && dj == 0) continue;
                            int ni = i + di;
                            int nj = j + dj;
                            if (ni >= 0 && ni < boardSize && nj >= 0 && nj < boardSize) {
                                if (cells[ni][nj] == maxPlayer) adjacentToMax = true;
                                if (cells[ni][nj] == minPlayer) adjacentToMin = true;
                            }
                        }
                    }
                    
                    if (adjacentToMax) maxPotential++;
                    if (adjacentToMin) minPotential++;
                }
            }
        }

        if (maxPotential + minPotential == 0) {
            return 0.0;
        }

        double ratio = (double) (maxPotential - minPotential) / (maxPotential + minPotential);
        return 0.08 * ratio;
    }

    // Corner score calculation
    private static double calcCornerScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxCorners = 0;
        int minCorners = 0;

        // Check all four corners: (0,0), (0,7), (7,0), (7,7)
        int[][] corners = { { 0, 0 }, { 0, 7 }, { 7, 0 }, { 7, 7 } };

        for (int[] corner : corners) {
            int ro = corner[0];
            int co = corner[1];
            PlayerType owner = cells[ro][co];

            if (owner == maxPlayer) {
                maxCorners++;
            } else if (owner == minPlayer) {
                minCorners++;
            }
        }

        // Corners are very valuable, weight heavily
        if (maxCorners + minCorners == 0) {
            return 0.0;
        }

        double ratio = (double) (maxCorners - minCorners) / 4.0; // 4 total corners
        return 0.4 * ratio; // High weight for corners
    }

    // Edge score calculation
    private static double calEdgeScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxEdges = 0;
        int minEdges = 0;
        int boardSize = cells.length;

        // Count pieces on edges (but not corners)
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                // Skip corners (already counted in corner score)
                if ((i == 0 && j == 0) || (i == 0 && j == boardSize - 1) ||
                        (i == boardSize - 1 && j == 0) || (i == boardSize - 1 && j == boardSize - 1)) {
                    continue;
                }

                // Check if on edge
                if (i == 0 || i == boardSize - 1 || j == 0 || j == boardSize - 1) {
                    PlayerType owner = cells[i][j];
                    if (owner == maxPlayer) {
                        maxEdges++;
                    } else if (owner == minPlayer) {
                        minEdges++;
                    }
                }
            }
        }

        // Edges are valuable, but less than corners
        int totalEdgeSpots = 4 * (boardSize - 2); // Total edge spots minus corners
        if (maxEdges + minEdges == 0) {
            return 0.0;
        }

        double ratio = (double) (maxEdges - minEdges) / totalEdgeSpots;
        return 0.2 * ratio;
    }

    // Positional score calculation using weight matrix
    private static double calcPositionalScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxPositionalScore = 0;
        int minPositionalScore = 0;

        // Position weights matrix (higher is better)
        // Corners are excluded here (counted separately)
        int[][] weights = {
                { 0, -20, 10, 5, 5, 10, -20, 0 },      
                { -20, -40, -5, -5, -5, -5, -40, -20 },
                { 10, -5, 5, 1, 1, 5, -5, 10 },
                { 5, -5, 1, 1, 1, 1, -5, 5 },
                { 5, -5, 1, 1, 1, 1, -5, 5 },
                { 10, -5, 5, 1, 1, 5, -5, 10 },
                { -20, -40, -5, -5, -5, -5, -40, -20 },
                { 0, -20, 10, 5, 5, 10, -20, 0 }
        };

        // Calculate positional scores
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                PlayerType owner = cells[i][j];
                if (owner == maxPlayer) {
                    maxPositionalScore += weights[i][j];
                } else if (owner == minPlayer) {
                    minPositionalScore += weights[i][j];
                }
            }
        }

        // Normalize the positional score
        int maxPossibleScore = 64 * 40;
        if (maxPositionalScore == 0 && minPositionalScore == 0) {
            return 0.0;
        }

        double ratio = (double) (maxPositionalScore - minPositionalScore) / (2.0 * maxPossibleScore);
        return 0.08 * ratio; // Slightly reduced weight since corners removed
    }

}
