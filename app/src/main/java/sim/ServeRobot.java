package sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import model.Graph;
import model.Order;

/**
 * Robot that collects up to 3 completed orders and delivers them using Dijkstra pathfinding.
 */
public class ServeRobot {
    private final Graph graph;
    private final String kitchenNode;
    private final Queue<Order> serveQueue;

    public ServeRobot(Graph graph, String kitchenNode, Queue<Order> serveQueue) {
        this.graph = graph;
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

        // Log dispatched orders
        String ordersList = batch.stream()
            .map(o -> o.dish().name() + ">T" + o.tableNumber())
            .collect(Collectors.joining(", "));
        System.out.println("[ROBOT] Dispatching orders: " + ordersList);

        // 2) Compute full path: kitchen -> each table -> kitchen
        List<String> fullPath = new ArrayList<>();
        String current = kitchenNode;
        fullPath.add(current);

        for (Order o : batch) {
            String dest = String.valueOf(o.tableNumber());
            List<String> segment = graph.dijkstra(current, dest);
            if (segment.size() > 1) {
                fullPath.addAll(segment.subList(1, segment.size()));
                current = dest;
            } else {
                System.err.println("[ROBOT] Error: No path from " + current + " to " + dest);
            }
        }
        // Return to kitchen
        List<String> back = graph.dijkstra(current, kitchenNode);
        if (back.size() > 1) {
            fullPath.addAll(back.subList(1, back.size()));
        }

        // 3) Print route
        System.out.println("[ROBOT] Route: " + String.join(" -> ", fullPath));

        // 4) Print delivery summary and completion
        String delivered = batch.stream()
            .map(o -> "T" + o.tableNumber())
            .collect(Collectors.joining(", "));
        System.out.println("[ROBOT] Delivered to: " + delivered + "; returning to kitchen.");
    }
}
