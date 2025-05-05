package sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.stream.Collectors;

import java.util.Optional;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import model.ChefQueue;
import model.Dish;
import model.Graph;
import model.GraphModel;
import model.GraphModel.NodeKind;
import model.Order;
import model.RobotQueue;

/**
 * Orchestrates order generation, cooking, and delivery using Dijkstra routing.
 */
public class SimulationEngine {
    /**
     * Listener interface for new order events.
     */
    public interface OrderListener {
               void onOrderPlaced(int tableId, Dish dish);
    }

    private final List<OrderListener> orderListeners = new ArrayList<>();
    /**
     * Register a listener to receive order placement events.
     */
    public void addOrderListener(OrderListener listener) {
        orderListeners.add(listener);
    }

    private final ChefQueue[] chefs = new ChefQueue[Dish.values().length];
    private final RobotQueue robotQ = new RobotQueue();
    private final GraphModel graphModel;
    private Graph simGraph;
    private final Random random = new Random();
    private final Timeline tickTimeline;
    private boolean robotBusy = false;

    public SimulationEngine(GraphModel gm) {
        this.graphModel = gm;
        // Initialize chef queues
        for (int i = 0; i < chefs.length; i++) {
            chefs[i] = new ChefQueue();
        }
        // Build internal Graph for pathfinding
        buildSimGraph();
        // Setup tick loop for cooking & delivery
        tickTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        tickTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    /**
     * Builds simGraph from the UI's GraphModel for Dijkstra computations.
     */
    private void buildSimGraph() {
        simGraph = new Graph();
        Map<String, String> idToName = graphModel.nodes().stream()
            .collect(Collectors.toMap(GraphModel.Node::id, GraphModel.Node::name));
        for (GraphModel.Edge e : graphModel.edges()) {
            String src = idToName.get(e.from);
            String dst = idToName.get(e.to);
            simGraph.addEdge(src, dst, (int) e.weight);
        }
    }

    /**
     * Starts scheduling orders and begins the cook/serve loop.
     */
    public void startSimulation() {
        scheduleTableOrders();
        tickTimeline.play();
    }

    /**
     * Randomizes total orders per table and schedules batches with cooldowns.
     */
    private void scheduleTableOrders() {
        Map<String, Integer> ordersMap = new HashMap<>();
        for (GraphModel.Node node : graphModel.nodes()) {
            if (node.type().seats > 0) { // only actual tables
                int T = node.type().seats;
                int min = Math.max(1, T - 1);
                int max = T + 3;
                int total = random.nextInt(max - min + 1) + min;
                ordersMap.put(node.id(), total);
            }
        }
        for (var entry : ordersMap.entrySet()) {
            String tableId = entry.getKey();
            int remaining = entry.getValue();
            while (remaining > 0) {
                int batch = random.nextInt(Math.min(3, remaining)) + 1;
                double cooldown = 1 + random.nextDouble() * 4; // seconds
                scheduleOrderBatch(tableId, batch, cooldown);
                remaining -= batch;
            }
        }
    }

    /**
     * Schedules a batch of orders for a table after a delay.
     */
    private void scheduleOrderBatch(String tableId, int batchSize, double delaySec) {
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(delaySec), e -> {
            for (int i = 0; i < batchSize; i++) {
                Dish d = Dish.values()[random.nextInt(Dish.values().length)];
                placeOrder(tableId, d);
            }
        }));
        tl.play();
    }

    /**
     * Places an order into the chef queue and notifies listeners.
     */
    public void placeOrder(String tableId, Dish d) {
        // Lookup the NodeInfo so we can get the real table index
        Optional<GraphModel.NodeInfo> info = graphModel.getNodeInfo(tableId);

        // Only enqueue real TABLE nodes
        if (info.isPresent() && info.get().kind == NodeKind.TABLE) {
            int tableNum = info.get().number;  // <-- the correct "1", "2", "3", etc.
            Order order = new Order(tableNum, d, System.currentTimeMillis());
            chefs[d.ordinal()].enqueue(order);

            // Notify UI & log
            System.out.println("[ORDER] Table " + tableNum + " → " + d.name());
            for (OrderListener l : orderListeners) {
                l.onOrderPlaced(tableNum, d);
            }
        }
        // else ignore kitchens / junctions
    }

    /**
     * Tick handler: updates cooks and dispatches robot deliveries.
     */
    private void tick() {
        long now = System.currentTimeMillis();
        // 1) Process chef queues
        for (ChefQueue cq : chefs) {
            for (Order done : cq.update(now)) {
                robotQ.add(done);
                System.out.println("[COOKED] " 
                    + done.dish().name() 
                    + " for Table " + done.tableNumber());
            }
        }
        // 2) Dispatch robot for available orders
        if (!robotBusy && !robotQ.getQueue().isEmpty()) {
            var queue = robotQ.getQueue();
            List<Order> trip = robotQ.dispatch(3);
            System.out.println("[DISPATCH] Robot taking " + trip.size() + " orders: " + trip.stream().map(o -> o.dish().name() + "→T" + o.tableNumber()).collect(Collectors.joining(", ")));
            Queue<Order> tripQueue = new ArrayDeque<>(trip);
            
            ServeRobot robot = new ServeRobot(
                simGraph,
                graphModel.kitchenId().orElse("K"),
                queue
            );
            
            robotBusy = true;
            new Thread(() -> {
                robot.serve();
                robotBusy = false;
            }).start();
        }
    }

    // -------- UI getters --------
    public ChefQueue[] chefQueues() { return chefs; }
    public RobotQueue robotQueue()    { return robotQ; }
    public boolean isRobotBusy()       { return robotBusy; }

    /**
     * Expose the underlying GraphModel for UI components.
     */
    public GraphModel getGraphModel() {
        return graphModel;
    }
}
