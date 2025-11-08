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
        
        // Sort the children based on their heuristic values
        // for MAX levels, sort descending, and for MIN levels, sort ascending
        orderedChildren.sort((node1, node2) -> {
            double value1 = getNodeValue(node1);
            double value2 = getNodeValue(node2);
            
            // Determine if we're at a MAX or MIN level
            boolean isMaxLevel = isMaximizingLevel(node1);
            
            if (isMaxLevel) {
                // MAX level so best moves first
                return Double.compare(value2, value1);
            } else {
                // MIN level so worst moves first
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
