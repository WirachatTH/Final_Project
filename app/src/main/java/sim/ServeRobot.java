package sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

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
        // 1) Collect up to 3 orders
        List<Order> batch = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Order o = serveQueue.poll();
            if (o == null) break;
            batch.add(o);
        }
        if (batch.isEmpty()) {
            System.out.println("No items to serve.");
            return;
        }

        // 2) Announce and gather destination nodes
        List<String> nodes = new ArrayList<>();
        System.out.println("\n--- Serving Round ---");
        for (Order o : batch) {
            System.out.println("Serving " + o.dish().name() + " to Table " + o.tableNumber());
            nodes.add(String.valueOf(o.tableNumber()));
        }

        // 3) Compute full path: kitchen -> each table -> kitchen
        List<String> fullPath = new ArrayList<>();
        String current = kitchenNode;
        fullPath.add(current);
        for (String dest : nodes) {
            List<String> segment = graph.dijkstra(current, dest);
            if (segment.size() > 1) {
                fullPath.addAll(segment.subList(1, segment.size()));
                current = dest;
            } else {
                System.err.println("Error: No path found from " + current + " to " + dest);
            }
        }
        List<String> back = graph.dijkstra(current, kitchenNode);
        if (back.size() > 1) {
            fullPath.addAll(back.subList(1, back.size()));
        }

        // 4) Print route
        System.out.println("Route: " + String.join(" -> ", fullPath));
    }
}
