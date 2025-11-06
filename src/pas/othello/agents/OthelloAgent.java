package src.pas.othello.agents;

// SYSTEM IMPORTS
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// JAVA PROJECT IMPORTS
import edu.bu.pas.othello.agents.TimedTreeSearchAgent;
import edu.bu.pas.othello.game.Game.GameView;
import edu.bu.pas.othello.game.Game;
import edu.bu.pas.othello.game.PlayerType;
import edu.bu.pas.othello.traversal.Node;
import edu.bu.pas.othello.utils.Coordinate;

public class OthelloAgent
        extends TimedTreeSearchAgent {

    // Transposition table for memoization
    // It allows me to check if there is already computed version
    // The reason of using the concurrent hashmap is to make it thread safe
    // Allow the multiple threads to access without making any issues.
    // I just implemented this becaue just in case if I have to access the table
    // multiple times concurrently.
    private final java.util.Map<String, Double> transpositionTable = new java.util.concurrent.ConcurrentHashMap<>();

    public static class OthelloNode
            extends Node {
        public OthelloNode(final PlayerType maxPlayerType, // who is MAX (me)
                final GameView gameView, // current state of the game
                final int depth) // the depth of this node
        {
            super(maxPlayerType, gameView, depth);
        }

        @Override
        public double getTerminalUtility() {
            GameView view = this.getGameView();

            // if we are not at a terminal node, return 0
            if (!this.isTerminal()) {
                return 0d;
            }

            // max player type
            PlayerType maxPlayer = this.getMaxPlayerType();
            PlayerType playerA = this.getCurrentPlayerType();
            PlayerType playerB = this.getOtherPlayerType();
            PlayerType minPlayer;

            // If the player A is is max, then other one; vice versa.
            if (playerA == maxPlayer) {
                minPlayer = playerB;
            } else {
                minPlayer = playerA;
            }

            // get piece counts
            PlayerType[][] cells = view.getCells();
            // # of things for the maxPlayer and minPlayer.
            int maxScore = 0;
            int minScore = 0;

            // count pieces
            for (int i = 0; i < cells.length; i++) {
                for (int j = 0; j < cells[i].length; j++) {
                    PlayerType owner = cells[i][j];
                    if (owner == maxPlayer) {
                        maxScore++;
                    } else if (owner == minPlayer) {
                        minScore++;
                    }
                }
            }

            if (maxScore > minScore) {
                return 1.0; // MAX wins
            } else if (maxScore < minScore) {
                return -1.0; // MAX loses
            } else {
                return 0.0; // tie
            }
        }

        @Override
        public List<Node> getChildren() {
            List<Node> children = new ArrayList<>();

            Game.GameView view = this.getGameView();
            PlayerType curPlayer = view.getCurrentPlayerType();
            PlayerType oPlayer = view.getOtherPlayerType();

            // Legal moves from the current player.
            java.util.Set<Coordinate> frontier = view.getFrontier(curPlayer);

            // Current player has the legal moves.
            if (!frontier.isEmpty()) {
                // for the every move the frontier can make...
                for (Coordinate move : frontier) {
                    // Build a temporary mutable Game from this view
                    Game g = new Game(view);
                    // Play that move on the Game
                    g.applyMove(move);
                    // turn change
                    g.setCurrentPlayerType(oPlayer);
                    // recompute the frontiers for the other player
                    g.calculateFrontiers();
                    // Get snapshot for the child node
                    Game.GameView childView = g.getView();
                    // Create the child node
                    OthelloNode childNode = new OthelloNode(
                            this.getMaxPlayerType(),
                            childView,
                            // Increment the depth.
                            this.getDepth() + 1);
                    // Record what move led us here
                    childNode.setLastMove(move);
                    // Add to the list of children
                    children.add(childNode);
                }
            } else {
                // Skippping my turn and passing to the enemy.
                // the case where the current player has NO legal moves. Pass
                Game g = new Game(view);
                // Increment turn number for the pass move
                g.setTurnNumber(g.getTurnNumber() + 1);
                // Do not apply the move and give turn to the another person.
                g.setCurrentPlayerType(oPlayer);
                // Recompute what that other player will be allowed to do.
                g.calculateFrontiers();
                // put the updated one as a child
                Game.GameView passView = g.getView();
                // Always create a pass node when current player has no moves
                OthelloNode passNode = new OthelloNode(
                        this.getMaxPlayerType(),
                        passView,
                        this.getDepth() + 1);
                // No coordinate was placed because this was a pass
                passNode.setLastMove(null);
                children.add(passNode);
            }
            return children;
        }
    }

    private final Random random;

    public OthelloAgent(final PlayerType myPlayerType,
            final long maxMoveThinkingTimeInMS) {
        super(myPlayerType,
                maxMoveThinkingTimeInMS);
        this.random = new Random();
    }

    public final Random getRandom() {
        return this.random;
    }

    @Override
    public OthelloNode makeRootNode(final GameView game) {
        // Starting with the depth of 0
        return new OthelloNode(this.getMyPlayerType(), game, 0);
    }

    @Override
    public Node treeSearch(Node n) {
        // This is for the memoization table
        transpositionTable.clear();
        // Using minimax with alpha-beta pruning
        // This is the maxDepth
        int maxDepth = calculateSearchDepth(n);
        // Kick off the minimax search
        MinimaxResult result = minimax(n, maxDepth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
        return result.bestNode;
    }

    // Calculating how deep we weant to go depends on the game phase.
    private int calculateSearchDepth(Node node) {
        GameView view = node.getGameView();
        PlayerType[][] cells = view.getCells();

        // Count total pieces on board
        int pieceCount = 0;
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                if (cells[i][j] != null) {
                    pieceCount++;
                }
            }
        }

        // Adaptive depth based on game phase
        if (pieceCount <= 10) {
            // During the early game, we search shallower but wider
            return 3;
        } else if (pieceCount <= 40) {
            // During the middle of the game, we search more in depth
            return 4;
        } else {
            // At last, find the one that can demolish the opponent.
            return 6;
        }
    }

    // Helper class to return both utility and best node from minimax
    private static class MinimaxResult {
        public final double utility;
        public final Node bestNode;

        public MinimaxResult(double utility, Node bestNode) {
            this.utility = utility;
            this.bestNode = bestNode;
        }
    }

    // generating the memoization key using the Stringbuilder
    // Saving keys in form of "boardState|depth|maximizingPlayer"
    private String getBoardHash(GameView view) {
        PlayerType[][] cells = view.getCells();
        // much more efficient way to build the string
        StringBuilder sb = new StringBuilder();
        sb.append(view.getCurrentPlayerType().toString()).append("|");
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
                // if the cell is empty,
                if (cells[i][j] == null) {
                    sb.append("_");
                } else {
                    sb.append(cells[i][j] == PlayerType.BLACK ? "B" : "W");
                }
            }
        }
        return sb.toString();
    }

    // Minimax with alpha-beta pruning and memoization
    private MinimaxResult minimax(Node node, int depth, double alpha, double beta, boolean maximizingPlayer) {
        // Generate memoization key
        String boardHash = getBoardHash(node.getGameView());
        String memoKey = boardHash + "|" + depth + "|" + maximizingPlayer;
        // Check transposition table first if it contains the value or not
        if (transpositionTable.containsKey(memoKey)) {
            double cachedValue = transpositionTable.get(memoKey);
            // if there is a key, return the cached value.
            return new MinimaxResult(cachedValue, node);
        }

        // Base case: terminal node or maximum depth reached
        if (node.isTerminal() || depth == 0) {
            double utility;
            if (node.isTerminal()) {
                utility = node.getTerminalUtility();
            } else {
                // Use heuristic evaluation
                utility = src.pas.othello.heuristics.Heuristics.calculateHeuristicValue(node);
            }
            // Store the key value pair in transposition table
            transpositionTable.put(memoKey, utility);
            return new MinimaxResult(utility, node);
        }

        // Recursive case: explore children
        List<Node> children = node.getChildren();

        // If no children ( just in case juuuuust in case)
        if (children.isEmpty()) {
            double utility = node.isTerminal() ? node.getTerminalUtility()
                    : src.pas.othello.heuristics.Heuristics.calculateHeuristicValue(node);
            return new MinimaxResult(utility, node);
        }
        // Order children for better alpha-beta pruning
        children = src.pas.othello.ordering.MoveOrderer.orderChildren(children);
        Node bestChild = children.get(0);
        // if we are maximizing the player,
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            // for the every child node ...
            for (Node child : children) {
                MinimaxResult eval = minimax(child, depth - 1, alpha, beta, false);
                // evaluate the utility and choose the best one.
                if (eval.utility > maxEval) {
                    maxEval = eval.utility;
                    bestChild = child;
                }
                // alpha is the best value
                alpha = Math.max(alpha, eval.utility);
                if (beta <= alpha) {
                    break; // Beta is getting pruned
                }
            }
            // Caching result in transposition table
            transpositionTable.put(memoKey, maxEval);
            return new MinimaxResult(maxEval, bestChild);
        } else {
            // minimizing the player to be in the POSITIVE_INFINITY
            double minEval = Double.POSITIVE_INFINITY;
            // Look out for the every child.
            for (Node child : children) {
                MinimaxResult eval = minimax(child, depth - 1, alpha, beta, true);
                if (eval.utility < minEval) {
                    minEval = eval.utility;
                    bestChild = child;
                }

                beta = Math.min(beta, eval.utility);
                if (beta <= alpha) {
                    break; // Alpha cutoff
                }
            }

            // Store result in transposition table
            transpositionTable.put(memoKey, minEval);
            return new MinimaxResult(minEval, bestChild);
        }
    }

    @Override
    public Coordinate chooseCoordinateToPlaceTile(final GameView game) {
        // TODO: this move will be called once per turn
        // you may want to use this method to add to data structures and whatnot
        // that your algorithm finds useful

        // make the root node
        Node node = this.makeRootNode(game);
        // call tree search
        Node moveNode = this.treeSearch(node);
        // return the move inside that node check if null
        return moveNode != null ? moveNode.getLastMove() : null;
    }

    @Override
    public void afterGameEnds(final GameView game) {
    }
}
