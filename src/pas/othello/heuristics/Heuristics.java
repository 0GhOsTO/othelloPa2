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

    public static double calculateHeuristicValue(Node node) {
        // TODO: complete me!
        GameView view = node.getGameView();
        PlayerType maxPlayer = node.getMaxPlayerType();
        PlayerType minPlayer = (maxPlayer == view.getCurrentPlayerType()) ? view.getOtherPlayerType()
                : view.getCurrentPlayerType();

        // Count my pieces vs opponent pieces
        // Early game, it's not that important
        // Late game, it's very important
        double pieceScore = calcPieceScore(view, maxPlayer, minPlayer);

        // Corner Control (4 corners)
        // Taking corners that are 0,0, 0,7, 7,0, 7,7 is very important
        double cornerScore = calcCornerScore(view, maxPlayer, minPlayer);

        // Edge Stability (Things at the edge can not be flipped)
        // Pieces on the edge are unlikeley to be flipped
        double edgeScore = calEdgeScore(view, maxPlayer, minPlayer);

        // Possible moves (# of possible moves vs me and opponent)
        // More possible moves allows more options and flexibility.
        double chanceScore = calcChanceScore(view, maxPlayer, minPlayer);

        // Positional Weighting
        // Avoid the corners adjacent the edges in the early game.
        double positionalScore = calcPositionalScore(view, maxPlayer, minPlayer);

        // Stability Score - Count stable disks that cannot be flipped
        double stabilityScore = calcStabilityScore(view, maxPlayer, minPlayer);

        // Piece Differential in Endgame - Heavy weight on piece count in final moves
        double pieceDifferentialScore = calcPieceDifferentialScore(view, maxPlayer, minPlayer);

        // Central Control - Control of the central 4x4 area
        double centralControlScore = calcCentralControlScore(view, maxPlayer, minPlayer);

        // We need to weight it differently based on game phase
        // Return the value between -1.0 and 1.0
        double totalScore = pieceScore + cornerScore + edgeScore + chanceScore + positionalScore +
                stabilityScore + pieceDifferentialScore + centralControlScore;

        // Clamp the result to [-1.0, 1.0] to match terminal utility range
        return Math.max(-1.0, Math.min(1.0, totalScore));
    }

    private static double calcPieceScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxCnt = 0;
        int minCnt = 0;
        int totPieces = 0;

        // Count pieces everytime
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                PlayerType owner = cells[i][j];
                if (owner == maxPlayer) {
                    maxCnt++;
                    totPieces++;
                } else if (owner == minPlayer) {
                    minCnt++;
                    totPieces++;
                }
            }
        }

        if (maxCnt + minCnt == 0) {
            return 0.0;
        }

        // In early game (< 32 pieces), piece count is less important
        // In late game (>= 32 pieces), piece count becomes more important
        double weight = (totPieces < 32) ? 0.1 : 0.3;

        double ratio = (double) (maxCnt - minCnt) / (maxCnt + minCnt);
        return weight * ratio;
    }

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

    private static double calEdgeScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxEdges = 0;
        int minEdges = 0;
        int boardSize = cells.length;

        // Count pieces on edges (but not corners, as they're counted separately)
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

        // Edge pieces are stable and valuable
        int totalEdgeSpots = 4 * (boardSize - 2); // Total edge spots minus corners
        if (maxEdges + minEdges == 0) {
            return 0.0;
        }

        double ratio = (double) (maxEdges - minEdges) / totalEdgeSpots;
        return 0.2 * ratio;
    }

    private static double calcChanceScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        // Get number of legal moves for each player
        Set<Coordinate> maxMoves = view.getFrontier(maxPlayer);
        Set<Coordinate> minMoves = view.getFrontier(minPlayer);

        int maxMoveCount = maxMoves.size();
        int minMoveCount = minMoves.size();

        if (maxMoveCount + minMoveCount == 0) {
            return 0.0;
        }

        // Mobility is important - having more moves gives flexibility
        double ratio = (double) (maxMoveCount - minMoveCount) / (maxMoveCount + minMoveCount);
        return 0.15 * ratio;
    }

    private static double calcPositionalScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxPositionalScore = 0;
        int minPositionalScore = 0;

        // Position weights matrix (higher is better)
        // Corners are best, edges are good, avoid spots next to corners
        int[][] weights = {
                { 100, -20, 10, 5, 5, 10, -20, 100 },
                { -20, -40, -5, -5, -5, -5, -40, -20 },
                { 10, -5, 5, 1, 1, 5, -5, 10 },
                { 5, -5, 1, 1, 1, 1, -5, 5 },
                { 5, -5, 1, 1, 1, 1, -5, 5 },
                { 10, -5, 5, 1, 1, 5, -5, 10 },
                { -20, -40, -5, -5, -5, -5, -40, -20 },
                { 100, -20, 10, 5, 5, 10, -20, 100 }
        };

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
        int maxPossibleScore = 64 * 100; // If all pieces were in corners
        if (maxPositionalScore == 0 && minPositionalScore == 0) {
            return 0.0;
        }

        double ratio = (double) (maxPositionalScore - minPositionalScore) / (2.0 * maxPossibleScore);
        return 0.1 * ratio; // Lower weight as it's less critical than corners/edges
    }

    // Count the stable pieces of the plays.
    private static double calcStabilityScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxStable = 0;
        int minStable = 0;

        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                PlayerType owner = cells[i][j];
                if (owner == null)
                    continue;

                // A piece is stable if it's in a corner or on an edge with no empty spaces to
                // flip through
                boolean isStable = isStablePiece(cells, i, j, owner);

                if (isStable) {
                    if (owner == maxPlayer) {
                        maxStable++;
                    } else if (owner == minPlayer) {
                        minStable++;
                    }
                }
            }
        }

        if (maxStable + minStable == 0)
            return 0.0;

        double ratio = (double) (maxStable - minStable) / (maxStable + minStable);
        return 0.25 * ratio; // It is quite important to have the stable pieces.
    }

    // Helper method to check if a piece is stable
    private static boolean isStablePiece(PlayerType[][] cells, int row, int col, PlayerType player) {
        int boardSize = cells.length;

        // Corner pieces are always stable: Things are locked in at the corner
        if ((row == 0 && col == 0) || (row == 0 && col == boardSize - 1) ||
                (row == boardSize - 1 && col == 0) || (row == boardSize - 1 && col == boardSize - 1)) {
            return true;
        }

        // Another check of the edge pieces.
        // Edge pieces are stable if there are no gaps in the line to the corner
        if (row == 0 || row == boardSize - 1 || col == 0 || col == boardSize - 1) {
            return isEdgeStable(cells, row, col, player);
        }

        return false; // Interior pieces are rarely truly stable in simple analysis
    }

    // Helper to check edge stability
    private static boolean isEdgeStable(PlayerType[][] cells, int row, int col, PlayerType player) {
        int boardSize = cells.length;

        // Check if this edge piece has a solid line to at least one corner
        if (row == 0) { // Top edge
            return (checkLineToCorner(cells, row, col, 0, -1, player) || // Left to corner
                    checkLineToCorner(cells, row, col, 0, 1, player)); // Right to corner
        } else if (row == boardSize - 1) { // Bottom edge
            return (checkLineToCorner(cells, row, col, 0, -1, player) ||
                    checkLineToCorner(cells, row, col, 0, 1, player));
        } else if (col == 0) { // Left edge
            return (checkLineToCorner(cells, row, col, -1, 0, player) ||
                    checkLineToCorner(cells, row, col, 1, 0, player));
        } else if (col == boardSize - 1) { // Right edge
            return (checkLineToCorner(cells, row, col, -1, 0, player) ||
                    checkLineToCorner(cells, row, col, 1, 0, player));
        }

        return false;
    }

    // Helper to check solid line to corner
    private static boolean checkLineToCorner(PlayerType[][] cells, int row, int col, int dRow, int dCol,
            PlayerType player) {
        int r = row + dRow;
        int c = col + dCol;

        while (r >= 0 && r < cells.length && c >= 0 && c < cells[0].length) {
            if (cells[r][c] != player) {
                return false;
            }
            r += dRow;
            c += dCol;
        }

        return true;
    }

    // Piece Differential in End Game
    private static double calcPieceDifferentialScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int totalPieces = 0;
        int maxCount = 0;
        int minCount = 0;

        // Count pieces
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                if (cells[i][j] != null) {
                    totalPieces++;
                    if (cells[i][j] == maxPlayer) {
                        maxCount++;
                    } else if (cells[i][j] == minPlayer) {
                        minCount++;
                    }
                }
            }
        }

        // This heuristic becomes more important in endgame
        if (totalPieces < 50)
            return 0.0; // Not endgame yet

        if (maxCount + minCount == 0)
            return 0.0;

        // In endgame, every piece counts heavily
        double ratio = (double) (maxCount - minCount) / (maxCount + minCount);
        double endgameWeight = Math.min(1.0, (totalPieces - 50) / 14.0);

        return 0.3 * endgameWeight * ratio;
    }

    // Central Control Strategy - Control of central squares = more mobility +
    // flexibility to win the game
    private static double calcCentralControlScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxCentralControl = 0;
        int minCentralControl = 0;

        // Central 4x4 area is extremely important so we better grab it.
        for (int i = 2; i <= 5; i++) {
            for (int j = 2; j <= 5; j++) {
                PlayerType owner = cells[i][j];

                if (owner == maxPlayer) {
                    maxCentralControl++;
                } else if (owner == minPlayer) {
                    minCentralControl++;
                }
            }
        }

        if (maxCentralControl + minCentralControl == 0)
            return 0.0;

        double ratio = (double) (maxCentralControl - minCentralControl) / 16.0;
        return 0.15 * ratio;
    }

}
