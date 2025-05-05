package sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import model.Graph;
import model.GraphModel;
import model.GraphModel.Node;
import model.GraphModel.NodeInfo;
import model.GraphModel.NodeKind;
import model.Order;

/**
 * Robot that collects up to 3 completed orders and delivers them using Dijkstra pathfinding.
 */
public class ServeRobot {
    private final Graph graph;
    private final GraphModel graphModel;
    private final String kitchenNode;
    private final Queue<Order> serveQueue;

    public ServeRobot(Graph graph, GraphModel graphModel, String kitchenNode, Queue<Order> serveQueue) {
        this.graph = graph;
        this.graphModel = graphModel;
        this.kitchenNode = kitchenNode;
        this.serveQueue = serveQueue;
    }

    /**
     * Serve up to 3 orders: drain them from the queue, compute the delivery route, and print it.
     */
    public void serve() {
        System.out.println("[ROBOT] Starting delivery run...");

        // 1) Collect up to 3 orders
        List<Order> batch = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Order o = serveQueue.poll();
            if (o == null) break;
            batch.add(o);
        }
        if (batch.isEmpty()) {
            System.out.println("[ROBOT] No items to serve.");
            return;
        }

        // Log dispatched orders with true node names
        String ordersList = batch.stream()
            .map(o -> o.dish().name() + " from " + lookupNodeName(o.tableNumber()))
            .collect(Collectors.joining(", "));
        System.out.println("[DISPATCH] Robot taking " + batch.size() + " orders: " + ordersList);

        // 2) Compute full path: kitchen -> each table -> kitchen
        List<String> fullPath = new ArrayList<>();
        String current = kitchenNode;
        fullPath.add(current);

        // Create a map to track unique tables and their dishes
        Map<Integer, String> tableToNode = new HashMap<>();
        for (Order o : batch) {
            int tableNum = o.tableNumber();
            String nodeName = lookupNodeName(tableNum);
            tableToNode.put(tableNum, nodeName);
        }

        // Create array of unique tables to visit
        List<String> tablesToVisit = solveOrderOptimization(current, tableToNode);
        
        // Now visit each table in the optimized order
        for (String dest : tablesToVisit) {
            if (current.equals(dest)) {
                // Already at this table; no travel needed.
                continue;
            }
            
            List<String> segment = graph.dijkstra(current, dest);
            
            // handle direct neighbor case if Dijkstra returned only the end node
            if (segment.size() == 1 && adjacencyListContainsEdge(current, dest)) {
                segment = Arrays.asList(current, dest);
            }
            
            if (segment.size() > 1) {
                fullPath.addAll(segment.subList(1, segment.size()));
                current = dest;
            } else {
                System.err.println("[ROBOT] Error: No path from " + current + " to " + dest);
            }
        }
        
        // Return to kitchen
        List<String> back = graph.dijkstra(current, kitchenNode);
        if (back.size() == 1 && adjacencyListContainsEdge(current, kitchenNode)) {
            back = Arrays.asList(current, kitchenNode);
        }
        if (back.size() > 1) {
            fullPath.addAll(back.subList(1, back.size()));
        }

        // at this point fullPath should be something like ["K","J1","T4-5","J1","K"]
        System.out.println("[ROBOT] Route: " + String.join(" -> ", fullPath));

        // Compute total distance over every leg, including the final return-to-K leg
        double total = 0;
        for (int i = 0; i < fullPath.size() - 1; i++) {
            String u = fullPath.get(i);
            String v = fullPath.get(i + 1);
            double segmentWeight = graph.getWeight(u, v) + 1;
            total += segmentWeight;
            // Uncomment for debugging
            // System.out.println("[DEBUG] Segment " + u + " -> " + v + " weight: " + segmentWeight);
        }
        System.out.println("[ROBOT] Total distance (round trip): " + total);

        // then the delivery summary…
        String delivered = batch.stream()
            .map(o -> lookupNodeName(o.tableNumber()))
            .collect(Collectors.joining(", "));
        System.out.println("[ROBOT] Delivered to: " + delivered + "; returning to kitchen.");
    }

    /**
     * Solve the optimization problem to find the best order to visit tables.
     * This is a simplified approach that uses a greedy algorithm to find the nearest unvisited node.
     * For a small number of points (3 or fewer), this will give reasonably good results.
     * 
     * @param start Starting node (kitchen)
     * @param tableToNode Map of table numbers to node names
     * @return List of node names in the optimal visiting order
     */
    private List<String> solveOrderOptimization(String start, Map<Integer, String> tableToNode) {
        Set<String> uniqueTables = new HashSet<>(tableToNode.values());
        List<String> result = new ArrayList<>();
        
        // If we only have 0 or 1 unique table, the solution is trivial
        if (uniqueTables.size() <= 1) {
            return new ArrayList<>(uniqueTables);
        }
        
        // Use a greedy nearest-neighbor approach
        String current = start;
        Set<String> visited = new HashSet<>();
        
        while (visited.size() < uniqueTables.size()) {
            String nearest = null;
            double minDistance = Double.MAX_VALUE;
            
            for (String node : uniqueTables) {
                if (visited.contains(node)) continue;
                
                List<String> path = graph.dijkstra(current, node);
                double distance = path.size() - 1; // Simple distance metric
                if (path.size() <= 1 && adjacencyListContainsEdge(current, node)) {
                    distance = 1; // Direct edge case
                }
                
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = node;
                }
            }
            
            if (nearest != null) {
                result.add(nearest);
                visited.add(nearest);
                current = nearest;
            } else {
                break; // This shouldn't happen unless there's a disconnected node
            }
        }
        
        return result;
    }

    /**
     * Lookup the node name (e.g., "T4-2") for the given table index.
     * Falls back to the numeric index if not found or not a TABLE node.
     */
    private String lookupNodeName(int tableNumber) {
        for (Node node : graphModel.nodes()) {
            Optional<NodeInfo> infoOpt = graphModel.getNodeInfo(node.id());
            if (infoOpt.isPresent() && infoOpt.get().kind == NodeKind.TABLE && infoOpt.get().number == tableNumber) {
                return node.name();
            }
        }
        return String.valueOf(tableNumber);
    }

    /**
     * Returns true if there is a direct edge between src and dest in the graph.
     */
    private boolean adjacencyListContainsEdge(String src, String dest) {
        return graph
            .getAllEdges()
            .stream()
            .anyMatch(e -> e.getSrc().equals(src) && e.getDest().equals(dest));
    }
}