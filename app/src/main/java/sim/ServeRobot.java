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

public class ServeRobot { //handles the completed orders and send them to the robot
    private final Graph graph; //restaurant layout
    private final GraphModel graphModel; //the graph with all nodes and paths
    private final String kitchenNode; //the kitchen node
    private final Queue<Order> serveQueue; //queue of orders
    private final SimulationEngine simulationEngine; //reference to the main engine

    public ServeRobot(Graph graph, GraphModel graphModel, String kitchenNode, Queue<Order> serveQueue, SimulationEngine simulationEngine) {
        this.graph = graph;
        this.graphModel = graphModel;
        this.kitchenNode = kitchenNode;
        this.serveQueue = serveQueue;
        this.simulationEngine = simulationEngine;
    }

    public List<String> calculateRoute() { //collect up to 3 orders
        List<Order> batch = new ArrayList<>();
        for (Order o : serveQueue) {
            batch.add(o);
            if (batch.size() >= 3) break;
        }
        
        if (batch.isEmpty()) {
            return new ArrayList<>();
        }
        
        //create full path of kitchen -> each table -> kitchen
        List<String> fullPath = new ArrayList<>();
        String current = kitchenNode;
        fullPath.add(current);
        
        //create a map to track unique tables and their dishes
        Map<Integer, String> tableToNode = new HashMap<>();
        for (Order o : batch) {
            int tableNum = o.tableNumber();
            String nodeName = lookupNodeName(tableNum);
            tableToNode.put(tableNum, nodeName);
        }
        
        //create array of unique tables to visit
        List<String> tablesToVisit = solveOrderOptimization(current, tableToNode);
        
        //visit each table in the optimized order
        for (String dest : tablesToVisit) {
            if (current.equals(dest)) {
                continue;
            }
            
            List<String> segment = graph.dijkstra(current, dest);
            
            if (segment.size() == 1 && adjacencyListContainsEdge(current, dest)) {
                segment = Arrays.asList(current, dest);
            }
            
            if (segment.size() > 1) {
                fullPath.addAll(segment.subList(1, segment.size()));
                current = dest;
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
        
        return fullPath;
    }

    //serves up to 3 orders in the robot queue
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

        //console log for debugging
        String ordersList = batch.stream()
            .map(o -> o.dish().name() + " from " + lookupNodeName(o.tableNumber()))
            .collect(Collectors.joining(", "));
        System.out.println("[DISPATCH] Robot taking " + batch.size() + " orders: " + ordersList);

        //createfull path: kitchen -> each table -> kitchen
        List<String> fullPath = new ArrayList<>();
        String current = kitchenNode;
        fullPath.add(current);

        //create a map to track unique tables and their dishes
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
                //the robot is already at this table, no travel needed
                continue;
            }
            
            List<String> segment = graph.dijkstra(current, dest);

            if (segment.size() == 1 && adjacencyListContainsEdge(current, dest)) {
                segment = Arrays.asList(current, dest);
            }
            
            if (segment.size() > 1) {
                fullPath.addAll(segment.subList(1, segment.size()));
                current = dest;
                
                //notify the delivery system that the table has been reached
                SimulationEngine.getInstance().notifyDelivery(dest);
            } else {
                System.err.println("[ROBOT] Error: No path from " + current + " to " + dest);
            }
        }
        
        //return to kitchen
        List<String> back = graph.dijkstra(current, kitchenNode);
        if (back.size() == 1 && adjacencyListContainsEdge(current, kitchenNode)) {
            back = Arrays.asList(current, kitchenNode);
        }
        if (back.size() > 1) {
            fullPath.addAll(back.subList(1, back.size()));
        }

        //output the log K -> ... -> K for debugging
        System.out.println("[ROBOT] Route: " + String.join(" -> ", fullPath));

       //calculate the total distance
       double total = 0;
       for (int i = 0; i < fullPath.size() - 1; i++) {
           String u = fullPath.get(i);
           String v = fullPath.get(i + 1);
           double segmentWeight = graph.getWeight(u, v) + 1;
           total += segmentWeight;
       }
       System.out.println("[ROBOT] Total distance (round trip): " + total);

       //summarize the delivery
       String delivered = batch.stream()
           .map(o -> lookupNodeName(o.tableNumber()))
           .collect(Collectors.joining(", "));
       System.out.println("[ROBOT] Delivered to: " + delivered + "; returning to kitchen.");
   }

   //greedy nearest-neighbor algorithm
   private List<String> solveOrderOptimization(String start, Map<Integer, String> tableToNode) {
       Set<String> uniqueTables = new HashSet<>(tableToNode.values());
       List<String> result = new ArrayList<>();
       
       //if only 0 or 1 table exists in the route, the solution is trivial
       if (uniqueTables.size() <= 1) {
           return new ArrayList<>(uniqueTables);
       }
       
       //greedy nearest-neighbor approach
       String current = start;
       Set<String> visited = new HashSet<>();
       
       while (visited.size() < uniqueTables.size()) {
           String nearest = null;
           double minDistance = Double.MAX_VALUE;
           
           for (String node : uniqueTables) {
               if (visited.contains(node)) continue;
               
               List<String> path = graph.dijkstra(current, node);
               double distance = path.size() - 1; //simple distance metric
               if (path.size() <= 1 && adjacencyListContainsEdge(current, node)) {
                   distance = 1; //direct edge case
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
               break; //prevent a disconnected node case (shouldn't happen)
           }
       }
       
       return result;
   }

   //search for the table name by its table number
   private String lookupNodeName(int tableNumber) {
       for (Node node : graphModel.nodes()) {
           Optional<NodeInfo> infoOpt = graphModel.getNodeInfo(node.id());
           if (infoOpt.isPresent() && infoOpt.get().kind == NodeKind.TABLE && infoOpt.get().number == tableNumber) {
               return node.name();
           }
       }
       return String.valueOf(tableNumber);
   }

   //check if there's a direct edge between src node and dest node
   private boolean adjacencyListContainsEdge(String src, String dest) {
       return graph
           .getAllEdges()
           .stream()
           .anyMatch(e -> e.getSrc().equals(src) && e.getDest().equals(dest));
   }
}