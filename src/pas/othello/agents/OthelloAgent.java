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

            // If pA IS the max player, then pB must be the other one,
            if (playerA == maxPlayer) {
                minPlayer = playerB;
            }
            // Otherwise pA is the other one,
            else {
                minPlayer = playerA;
            }

            // get piece counts
            PlayerType[][] cells = view.getCells();
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

            // If this node is terminal, it has no children.
            if (this.isTerminal() || view.isGameOver()) {
                return children; // empty
            }

            PlayerType currentPlayer = view.getCurrentPlayerType();
            PlayerType otherPlayer = view.getOtherPlayerType();

            // Frontier = set of legal moves for current player
            java.util.Set<Coordinate> frontier = view.getFrontier(currentPlayer);

            // CASE 1: current player HAS legal moves
            if (!frontier.isEmpty()) {

                for (Coordinate move : frontier) {
                    // Build a temporary mutable Game from this view
                    Game g = new Game(view);

                    // Play that move on the Game
                    g.applyMove(move);

                    // now the other player gets to go
                    g.setCurrentPlayerType(otherPlayer);

                    // recompute the frontiers for the other player
                    g.calculateFrontiers();

                    // Get a fresh read-only snapshot for the child node
                    Game.GameView childView = g.getView();

                    // Create the child node
                    OthelloNode childNode = new OthelloNode(
                            this.getMaxPlayerType(),
                            childView,
                            this.getDepth() + 1);

                    // Record what move led us here
                    childNode.setLastMove(move);

                    // Add to the list of children
                    children.add(childNode);
                }

            } else {
                // the case where the current player has NO legal moves. Pass
                Game g = new Game(view);

                // Increment turn number for the pass move
                g.setTurnNumber(g.getTurnNumber() + 1);

                // Don't apply a move. Just hand the turn to the other player.
                g.setCurrentPlayerType(otherPlayer);

                // Recompute what that other player will be allowed to do.
                g.calculateFrontiers();

                // Turn that updated Game back into a view for the child
                Game.GameView passView = g.getView();

                // Always create a pass node when current player has no moves
                // The game termination logic should be handled elsewhere (isTerminal method)
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
        // if you change OthelloNode's constructor, you will want to change this!
        // Note: I am starting the initial depth at 0 (because I like to count up)
        // change this if you want to count depth differently
        return new OthelloNode(this.getMyPlayerType(), game, 0);
    }

    @Override
    public Node treeSearch(Node n) {
        // Clear transposition table for new search (to prevent stale data)
        transpositionTable.clear();
        
        // Use minimax with alpha-beta pruning
        // Adaptive depth based on game phase and available time
        int maxDepth = calculateSearchDepth(n);
        
        // Start the minimax search with time tracking
        long startTime = System.currentTimeMillis();
        MinimaxResult result = minimax(n, maxDepth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true, startTime);
        return result.bestNode;
    }
    
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
            // Early game: fewer pieces, more branching factor, use shallow search
            return 3;
        } else if (pieceCount <= 40) {
            // Mid game: balanced approach
            return 4;
        } else {
            // End game: fewer legal moves, can search deeper
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
    
    // Generate a unique hash for the game state
    private String getBoardHash(GameView view) {
        PlayerType[][] cells = view.getCells();
        StringBuilder sb = new StringBuilder();
        sb.append(view.getCurrentPlayerType().toString()).append("|");
        
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < cells[i].length; j++) {
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
    private MinimaxResult minimax(Node node, int depth, double alpha, double beta, boolean maximizingPlayer, long startTime) {
        // Time cutoff to prevent infinite loops (80% of max thinking time)
        long maxTime = (long)(this.getMaxThinkingTimeInMS() * 0.8);
        if (System.currentTimeMillis() - startTime > maxTime) {
            // Time cutoff reached, return heuristic evaluation
            double utility = src.pas.othello.heuristics.Heuristics.calculateHeuristicValue(node);
            return new MinimaxResult(utility, node);
        }
        String boardHash = getBoardHash(node.getGameView());
        String memoKey = boardHash + "|" + depth + "|" + maximizingPlayer;
        
        // Check transposition table first
        if (transpositionTable.containsKey(memoKey)) {
            double cachedValue = transpositionTable.get(memoKey);
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
            
            // Store in transposition table
            transpositionTable.put(memoKey, utility);
            return new MinimaxResult(utility, node);
        }
        
        List<Node> children = node.getChildren();
        
        // If no children (shouldn't happen with proper getChildren implementation)
        if (children.isEmpty()) {
            double utility = node.isTerminal() ? node.getTerminalUtility() 
                : src.pas.othello.heuristics.Heuristics.calculateHeuristicValue(node);
            return new MinimaxResult(utility, node);
        }
        
        // Order children for better alpha-beta pruning
        children = src.pas.othello.ordering.MoveOrderer.orderChildren(children);
        
        Node bestChild = children.get(0);
        
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            
            for (Node child : children) {
                MinimaxResult eval = minimax(child, depth - 1, alpha, beta, false, startTime);
                
                if (eval.utility > maxEval) {
                    maxEval = eval.utility;
                    bestChild = child;
                }
                
                alpha = Math.max(alpha, eval.utility);
                if (beta <= alpha) {
                    break; // Beta cutoff
                }
            }
            
            // Store result in transposition table
            transpositionTable.put(memoKey, maxEval);
            return new MinimaxResult(maxEval, bestChild);
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            
            for (Node child : children) {
                MinimaxResult eval = minimax(child, depth - 1, alpha, beta, true, startTime);
                
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

        // return the move inside that node (null check to prevent NPE)
        return moveNode != null ? moveNode.getLastMove() : null;
    }

    @Override
    public void afterGameEnds(final GameView game) {
    }
}
