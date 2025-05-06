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
import javafx.application.Platform;
import javafx.util.Duration;
import model.ChefQueue;
import model.Dish;
import model.Graph;
import model.GraphModel;
import model.GraphModel.NodeKind;
import model.Order;
import model.RobotQueue;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Optional;
import java.util.stream.Collectors;
import model.GraphModel;
import model.GraphModel.NodeInfo;
import model.GraphModel.NodeKind;

/**
 * Orchestrates order generation, cooking, and delivery using Dijkstra routing.
 */
public class SimulationEngine {
    /**
     * Listener interface for new order events.
     */
    private long simulationStartTime = 0;
    private long lastEmptyQueueTime = 0;

    public interface OrderListener {
        void onOrderPlaced(int tableId, Dish dish);
    }

    /**
     * Listener interface for simulation start.
     */
    public interface SimulationStartListener {
        void onSimulationStart();
    }

    /**
     * Listener interface for robot dispatch.
     */
    public interface RobotDispatchListener {
        void onRobotDispatch(List<Order> orders, List<String> route);
    }

    /**
     * Listener interface for delivery events.
     */
    public interface DeliveryListener {
        void onDelivery(String tableName);
    }
    
    /**
     * Listener interface for simulation completion.
     */
    public interface SimulationCompletionListener {
        void onSimulationComplete();
    }

    private final List<OrderListener> orderListeners = new ArrayList<>();
    private final List<SimulationStartListener> simulationStartListeners = new ArrayList<>();
    private final List<RobotDispatchListener> robotDispatchListeners = new ArrayList<>();
    private final List<DeliveryListener> deliveryListeners = new ArrayList<>();
    private final List<SimulationCompletionListener> simulationCompletionListeners = new ArrayList<>();
    
    private boolean simulationCompleted = false;

    private static SimulationEngine instance;

    /**
     * Register a listener to receive order placement events.
     */
    public void addOrderListener(OrderListener listener) {
        orderListeners.add(listener);
    }

    /**
     * Register a listener to receive simulation start events.
     */
    public void addSimulationStartListener(SimulationStartListener listener) {
        simulationStartListeners.add(listener);
    }

    /**
     * Register a listener to receive robot dispatch events.
     */
    public void addRobotDispatchListener(RobotDispatchListener listener) {
        robotDispatchListeners.add(listener);
    }

    /**
     * Register a listener to receive delivery events.
     */
    public void addDeliveryListener(DeliveryListener listener) {
        deliveryListeners.add(listener);
    }
    
    /**
     * Register a listener to receive simulation completion events.
     */
    public void addSimulationCompletionListener(SimulationCompletionListener listener) {
        simulationCompletionListeners.add(listener);
    }
    
    /**
     * Notify listeners that the simulation has completed.
     */
    private void notifySimulationComplete() {
        for (SimulationCompletionListener listener : simulationCompletionListeners) {
            listener.onSimulationComplete();
        }
    }

    /**
     * Get the singleton instance.
     */
    public static SimulationEngine getInstance() {
        return instance;
    }

    private final ChefQueue[] chefs = new ChefQueue[Dish.values().length];
    private final RobotQueue robotQ = new RobotQueue();
    private final GraphModel graphModel;
    private Graph simGraph;
    private final Random random = new Random();
    private final Timeline tickTimeline;
    private boolean robotBusy = false;

    public SimulationEngine(GraphModel gm) {
        instance = this; // Set singleton instance
        this.graphModel = gm;
        // Initialize chef queues
        for (int i = 0; i < chefs.length; i++) {
            chefs[i] = new ChefQueue();
        }
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
     * Notify listeners that simulation has started.
     */
    private void notifySimulationStart() {
        for (SimulationStartListener listener : simulationStartListeners) {
            listener.onSimulationStart();
        }
    }

    /**
     * Notify listeners that robot has been dispatched with orders.
     */
    private void notifyRobotDispatched(List<Order> orders, List<String> route) {
        System.out.println("[DEBUG] Notifying " + robotDispatchListeners.size() + " listeners about dispatch");
        for (RobotDispatchListener listener : robotDispatchListeners) {
            listener.onRobotDispatch(orders, route);
        }
    }

    /**
     * Notify listeners that a delivery has been made.
     */
    public void notifyDelivery(String tableName) {
        for (DeliveryListener listener : deliveryListeners) {
            listener.onDelivery(tableName);
        }
    }

    /**
     * Method for RobotSimulationPane to call when animation completes.
     */
    public void notifyRobotMovementComplete() {
        robotBusy = false;
    }

    /**
     * Starts scheduling orders and begins the cook/serve loop.
     */
    public void startSimulation() {
        buildSimGraph();
        simulationCompleted = false;
        simulationStartTime = System.currentTimeMillis();
        lastEmptyQueueTime = 0;
        scheduleTableOrders();
        notifySimulationStart();
        tickTimeline.play();
    }

    private String getTableNodeName(int tableNumber) {
        return graphModel.nodes().stream()
            .filter(n -> {
                Optional<GraphModel.NodeInfo> info = graphModel.getNodeInfo(n.id());
                return info.isPresent()
                    && info.get().kind == GraphModel.NodeKind.TABLE
                    && info.get().number == tableNumber;
            })
            .findFirst()
            .map(GraphModel.Node::name)
            .orElse("T?");  // fallback
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
     * Helper to get kitchen node name.
     */
    private String getKitchenNodeName() {
        String kId = graphModel.kitchenId().orElseThrow();
        return graphModel.nodes().stream()
            .filter(n -> n.id().equals(kId))
            .findFirst()
            .map(GraphModel.Node::name)
            .orElse("K");
    }
    
    /**
     * Check if all orders are complete and robot is idle
     */
    private boolean isAllComplete() {
        // If we just started (less than 10 seconds ago), don't consider it complete
        if (System.currentTimeMillis() - simulationStartTime < 10000) {
            return false;
        }
        
        // Check if robot queue is empty
        if (!robotQ.getQueue().isEmpty()) {
            return false;
        }
        
        // Check if all chef queues are empty
        for (ChefQueue cq : chefs) {
            if (!cq.getQueueReadonly().isEmpty()) {
                return false;
            }
        }
        
        // Check if robot is idle
        if (robotBusy) {
            return false;
        }
        
        // Add delay after robot is idle to ensure everything completed
        if (lastEmptyQueueTime == 0) {
            lastEmptyQueueTime = System.currentTimeMillis();
            return false;
        } else {
            // Wait at least 2 seconds after all queues are empty
            if (System.currentTimeMillis() - lastEmptyQueueTime > 2000) {
                return true;
            }
            return false;
        }
    }

    /**
     * Tick handler: updates cooks and dispatches robot deliveries.
     */
    private void tick() {
    long now = System.currentTimeMillis();

    // 1) Process chefs: move any finished orders into the robot queue
    for (ChefQueue cq : chefs) {
        for (Order done : cq.update(now)) {
            robotQ.add(done);
            System.out.println("[COOKED] " 
                + done.dish().name() 
                + " for Table " 
                + done.tableNumber());
        }
    }

    // 2) Dispatch robot if it's free and there are ready dishes
    if (!robotBusy && !robotQ.getQueue().isEmpty()) {
        List<Order> trip = robotQ.dispatch(3);

        // Add this debug print
        System.out.println("[DEBUG] Dispatching " + trip.size() + " orders for robot");
        for (Order o : trip) {
            System.out.println("[DEBUG] Order: " + o.dish().name() + " for " + getTableNodeName(o.tableNumber()));
        }

        // Build a log string like "Tea from T2-3, Egg Tart from T4-1"
        String dispatchLog = trip.stream()
            .map(o -> o.dish().name() 
                + " from " 
                + getTableNodeName(o.tableNumber()))
            .collect(Collectors.joining(", "));
        System.out.println("[DISPATCH] Robot taking " 
            + trip.size() 
            + " orders: " 
            + dispatchLog);

        // Hand off to ServeRobot
        Queue<Order> tripQueue = new ArrayDeque<>(trip);
        String kitchenName = getKitchenNodeName();

        // Create ServeRobot with reference to this SimulationEngine instance
        ServeRobot robot = new ServeRobot(
            simGraph,
            graphModel,
            kitchenName,
            tripQueue,
            this  // Pass the current SimulationEngine instance
        );

        // Get the route first
        List<String> route = robot.calculateRoute();
        
        // Notify listeners about the dispatch with the route
        notifyRobotDispatched(new ArrayList<>(trip), route);
        
        robotBusy = true;
        
        // Start serving in a separate thread
        new Thread(() -> {
            robot.serve();
        }).start();
    }
    
    // 3) Check if all orders are complete - ONLY IF SIMULATION IS ACTUALLY RUNNING
    // Don't check immediately at startup, only after some time has passed
    // Add delay before checking completion
    if (System.currentTimeMillis() - simulationStartTime > 5000) {
        if (isAllComplete()) {
            // Only notify once
            if (!simulationCompleted) {
                simulationCompleted = true;
                System.out.println("[SIMULATION] All orders served! Simulation complete.");
                Platform.runLater(() -> {
                    notifySimulationComplete();
                    notifyTabControlListeners(); // Added this line
                });
            }
        }
    }
}

    // -------- UI getters --------
    public ChefQueue[] chefQueues() { return chefs; }
    public RobotQueue robotQueue() { return robotQ; }
    public boolean isRobotBusy() { return robotBusy; }

    /**
     * Expose the underlying GraphModel for UI components.
     */
    public GraphModel getGraphModel() {
        return graphModel;
    }

    /**
     * TabControlListener interface for managing UI tabs when simulation completes.
     */
    public interface TabControlListener {
        void onSimulationComplete();
    }

    private final List<TabControlListener> tabControlListeners = new ArrayList<>();

    public void addTabControlListener(TabControlListener listener) {
        tabControlListeners.add(listener);
    }

    private void notifyTabControlListeners() {
        for (TabControlListener listener : tabControlListeners) {
            listener.onSimulationComplete();
        }
    }


    /**
     * Notify listeners that the simulation has been reset.
     */
    private void notifyReset() {
        Platform.runLater(() -> {
            for (OrderListener listener : orderListeners) {
                if (listener instanceof ResetListener) {
                    ((ResetListener) listener).onReset();
                }
            }
        });
    }

    /**
     * Listener interface for simulation reset events.
     */
    public interface ResetListener {
        void onReset();
    }

    private final List<ResetListener> resetListeners = new ArrayList<>();

    /**
     * Register a listener to receive reset events.
     */
    public void addResetListener(ResetListener listener) {
        resetListeners.add(listener);
    }

    /**
     * Notify listeners that the simulation has been reset.
     */
    private void notifyResetListeners() {
        for (ResetListener listener : resetListeners) {
            listener.onReset();
        }
    }

    /**
     * Resets the simulation state completely.
     */
    public void resetState() {
        // Stop the timeline if it's running
        tickTimeline.stop();
        
        // Reset all chef queues
        for (ChefQueue cq : chefs) {
            cq.clear();
        }
        
        // Clear the robot queue
        robotQ.clear();
        
        // Reset the robot busy state
        robotBusy = false;
        
        // Reset simulation completion flag
        simulationCompleted = false;
        
        // Clear any stored timestamps
        simulationStartTime = 0;
        lastEmptyQueueTime = 0;
        
        // Notify listeners about reset
        Platform.runLater(() -> {
            notifyResetListeners();
        });
    }
}