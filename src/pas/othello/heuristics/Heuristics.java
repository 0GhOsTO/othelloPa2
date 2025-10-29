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

        return totalScore;
    }

}
