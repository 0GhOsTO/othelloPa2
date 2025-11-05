package src.pas.othello.ordering;


// SYSTEM IMPORTS
import edu.bu.pas.othello.traversal.Node;

import java.util.List;


// JAVA PROJECT IMPORTS



public class MoveOrderer
    extends Object
{

    public static List<Node> orderChildren(List<Node> children)
    {
        if (children == null || children.isEmpty()) {
            return children;
        }
        
        // Create a copy of the list to avoid modifying the original
        List<Node> orderedChildren = new java.util.ArrayList<>(children);
        
        // Sort children by heuristic value (best moves first for pruning efficiency)
        // For MAX player perspective, we want higher values first
        // For MIN player perspective, we want lower values first
        orderedChildren.sort((node1, node2) -> {
            double value1 = getNodeValue(node1);
            double value2 = getNodeValue(node2);
            
            // Determine if we're at a MAX or MIN level
            // We can infer this from comparing current player with max player
            boolean isMaxLevel = isMaximizingLevel(node1);
            
            if (isMaxLevel) {
                // MAX level: sort in descending order (best moves first)
                return Double.compare(value2, value1);
            } else {
                // MIN level: sort in ascending order (worst moves for MAX first)
                return Double.compare(value1, value2);
            }
        });
        
        return orderedChildren;
    }
    
    private static double getNodeValue(Node node) {
        if (node.isTerminal()) {
            return node.getTerminalUtility();
        } else {
            return src.pas.othello.heuristics.Heuristics.calculateHeuristicValue(node);
        }
    }
    
    private static boolean isMaximizingLevel(Node node) {
        // If current player is the same as max player, this is a maximizing level
        return node.getGameView().getCurrentPlayerType() == node.getMaxPlayerType();
    }

}
