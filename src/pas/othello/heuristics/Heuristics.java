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

        // We need to weight it differently based on game phase
        // Return the value between -1.0 and 1.0
        double totalScore = pieceScore + cornerScore + edgeScore + chanceScore + positionalScore;

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
        
        double ratio = (double)(maxCnt - minCnt) / (maxCnt + minCnt);
        return weight * ratio;
    }

    private static double calcCornerScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxCorners = 0;
        int minCorners = 0;
        
        // Check all four corners: (0,0), (0,7), (7,0), (7,7)
        int[][] corners = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
        
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
        
        double ratio = (double)(maxCorners - minCorners) / 4.0; // 4 total corners
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
                if ((i == 0 && j == 0) || (i == 0 && j == boardSize-1) || 
                    (i == boardSize-1 && j == 0) || (i == boardSize-1 && j == boardSize-1)) {
                    continue;
                }
                
                // Check if on edge
                if (i == 0 || i == boardSize-1 || j == 0 || j == boardSize-1) {
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
        
        double ratio = (double)(maxEdges - minEdges) / totalEdgeSpots;
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
        double ratio = (double)(maxMoveCount - minMoveCount) / (maxMoveCount + minMoveCount);
        return 0.15 * ratio;
    }

    private static double calcPositionalScore(GameView view, PlayerType maxPlayer, PlayerType minPlayer) {
        PlayerType[][] cells = view.getCells();
        int maxPositionalScore = 0;
        int minPositionalScore = 0;
        
        // Position weights matrix (higher is better)
        // Corners are best, edges are good, avoid spots next to corners
        int[][] weights = {
            {100, -20,  10,   5,   5,  10, -20, 100},
            {-20, -40,  -5,  -5,  -5,  -5, -40, -20},
            { 10,  -5,   5,   1,   1,   5,  -5,  10},
            {  5,  -5,   1,   1,   1,   1,  -5,   5},
            {  5,  -5,   1,   1,   1,   1,  -5,   5},
            { 10,  -5,   5,   1,   1,   5,  -5,  10},
            {-20, -40,  -5,  -5,  -5,  -5, -40, -20},
            {100, -20,  10,   5,   5,  10, -20, 100}
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
        
        double ratio = (double)(maxPositionalScore - minPositionalScore) / (2.0 * maxPossibleScore);
        return 0.1 * ratio; // Lower weight as it's less critical than corners/edges
    }

}
