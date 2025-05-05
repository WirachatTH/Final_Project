package sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Optional;
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

        for (Order o : batch) {
            String dest = lookupNodeName(o.tableNumber());
            System.out.println("[DEBUG] dijkstra from=" + current + " to=" + dest);
            List<String> segment = graph.dijkstra(current, dest);
            if (segment.size() > 1) {
                fullPath.addAll(segment.subList(1, segment.size()));
                current = dest;
            } else {
                System.err.println("[ROBOT] Error: No path from " + current + " to " + dest);
            }
        }
        List<String> back = graph.dijkstra(current, kitchenNode);
        if (back.size() > 1) {
            fullPath.addAll(back.subList(1, back.size()));
        }

        // 3) Print route
        System.out.println("[ROBOT] Route: " + String.join(" -> ", fullPath));

        // 4) Print delivery summary and completion
        String delivered = batch.stream()
            .map(o -> lookupNodeName(o.tableNumber()))
            .collect(Collectors.joining(", "));
        System.out.println("[ROBOT] Delivered to: " + delivered + "; returning to kitchen.");
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
}
