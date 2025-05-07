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

public class SimulationEngine {
    private long simulationStartTime = 0;
    private long lastEmptyQueueTime = 0;

    public interface OrderListener {
        void onOrderPlaced(int tableId, Dish dish);
    }

    //sim start listener
    public interface SimulationStartListener {
        void onSimulationStart();
    }

    //robot dispatcher 
    public interface RobotDispatchListener {
        void onRobotDispatch(List<Order> orders, List<String> route);
    }

    //new delivery events listener
    public interface DeliveryListener {
        void onDelivery(String tableName);
    }
    
    //sim complete listener
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

    //register a listener for order placement
    public void addOrderListener(OrderListener listener) {
        orderListeners.add(listener);
    }

    //register a listener for sim start 
    public void addSimulationStartListener(SimulationStartListener listener) {
        simulationStartListeners.add(listener);
    }

    //register a listener to receive robot dispatch movements
    public void addRobotDispatchListener(RobotDispatchListener listener) {
        robotDispatchListeners.add(listener);
    }

    //register a listener to receive delivery events
    public void addDeliveryListener(DeliveryListener listener) {
        deliveryListeners.add(listener);
    }
    
    //register a listener for sim complete
    public void addSimulationCompletionListener(SimulationCompletionListener listener) {
        simulationCompletionListeners.add(listener);
    }
    
    //notification for sim complete
    private void notifySimulationComplete() {
        for (SimulationCompletionListener listener : simulationCompletionListeners) {
            listener.onSimulationComplete();
        }
    }

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
        instance = this; //set singleton instance
        this.graphModel = gm;
        //initialize chef queues
        for (int i = 0; i < chefs.length; i++) {
            chefs[i] = new ChefQueue();
        }
        //setup tick loop for cooking & delivery
        tickTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        tickTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    //clone the graph from the gridEditor UI
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

    //notification for sim start
    private void notifySimulationStart() {
        for (SimulationStartListener listener : simulationStartListeners) {
            listener.onSimulationStart();
        }
    }

    //notification for robot being dispatched
    private void notifyRobotDispatched(List<Order> orders, List<String> route) {
        System.out.println("[DEBUG] Notifying " + robotDispatchListeners.size() + " listeners about dispatch");
        for (RobotDispatchListener listener : robotDispatchListeners) {
            listener.onRobotDispatch(orders, route);
        }
    }

    //notification for delivery success
    public void notifyDelivery(String tableName) {
        for (DeliveryListener listener : deliveryListeners) {
            listener.onDelivery(tableName);
        }
    }

    //notification for robot movement completed
    public void notifyRobotMovementComplete() {
        robotBusy = false;
    }

    //begins the cooking loop
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
            .orElse("T?");  //fallback case
    }

    //randomize orders per table based on n-1 and n+3 rule
    private void scheduleTableOrders() {
        Map<String, Integer> ordersMap = new HashMap<>();
        for (GraphModel.Node node : graphModel.nodes()) {
            if (node.type().seats > 0) { //only actual tables
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
                double cooldown = 1 + random.nextDouble() * 4; //seconds
                scheduleOrderBatch(tableId, batch, cooldown);
                remaining -= batch;
            }
        }
    }

    //Schedules a batch of orders for a table after a delay.
    private void scheduleOrderBatch(String tableId, int batchSize, double delaySec) {
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(delaySec), e -> {
            for (int i = 0; i < batchSize; i++) {
                Dish d = Dish.values()[random.nextInt(Dish.values().length)];
                placeOrder(tableId, d);
            }
        }));
        tl.play();
    }

    //place orders in chef queue
    public void placeOrder(String tableId, Dish d) {
        //lookup the NodeInfo to get the real table index
        Optional<GraphModel.NodeInfo> info = graphModel.getNodeInfo(tableId);

        //only enqueue real TABLE nodes
        if (info.isPresent() && info.get().kind == NodeKind.TABLE) {
            int tableNum = info.get().number;  // <-- the correct "1", "2", "3", etc.
            Order order = new Order(tableNum, d, System.currentTimeMillis());
            chefs[d.ordinal()].enqueue(order);

            //console log for debugging
            System.out.println("[ORDER] Table " + tableNum + " → " + d.name());
            for (OrderListener l : orderListeners) {
                l.onOrderPlaced(tableNum, d);
            }
        }
    }

    //get the kicthen node name
    private String getKitchenNodeName() {
        String kId = graphModel.kitchenId().orElseThrow();
        return graphModel.nodes().stream()
            .filter(n -> n.id().equals(kId))
            .findFirst()
            .map(GraphModel.Node::name)
            .orElse("K");
    }
    
    //check if the robot stops moving and all orders are completed
    private boolean isAllComplete() {
        //only considers the sim as completed once the sim has been running for 10 seconds
        if (System.currentTimeMillis() - simulationStartTime < 10000) {
            return false;
        }
        
        //check if robot queue is empty
        if (!robotQ.getQueue().isEmpty()) {
            return false;
        }
        
        //check if all chef queues are empty
        for (ChefQueue cq : chefs) {
            if (!cq.getQueueReadonly().isEmpty()) {
                return false;
            }
        }
        
        //check if robot is idle
        if (robotBusy) {
            return false;
        }
        
        //add delay after robot is idle to ensure everything completed
        if (lastEmptyQueueTime == 0) {
            lastEmptyQueueTime = System.currentTimeMillis();
            return false;
        } else {
            //wait at least 2 seconds after all queues are empty
            if (System.currentTimeMillis() - lastEmptyQueueTime > 2000) {
                return true;
            }
            return false;
        }
    }

    //update cooks and robot dispatcher
    private void tick() {
    long now = System.currentTimeMillis();

    //process chefs: move any finished orders into the robot queue
    for (ChefQueue cq : chefs) {
        for (Order done : cq.update(now)) {
            robotQ.add(done);
            System.out.println("[COOKED] " 
                + done.dish().name() 
                + " for Table " 
                + done.tableNumber());
        }
    }

    //dispatch robot if it's free and there are ready dishes
    if (!robotBusy && !robotQ.getQueue().isEmpty()) {
        List<Order> trip = robotQ.dispatch(3);

        //console log for debugging
        System.out.println("[DEBUG] Dispatching " + trip.size() + " orders for robot");
        for (Order o : trip) {
            System.out.println("[DEBUG] Order: " + o.dish().name() + " for " + getTableNodeName(o.tableNumber()));
        }

        //(food) from (table) console log for debugging
        String dispatchLog = trip.stream()
            .map(o -> o.dish().name() 
                + " from " 
                + getTableNodeName(o.tableNumber()))
            .collect(Collectors.joining(", "));
        System.out.println("[DISPATCH] Robot taking " 
            + trip.size() 
            + " orders: " 
            + dispatchLog);

        //hand off to ServeRobot
        Queue<Order> tripQueue = new ArrayDeque<>(trip);
        String kitchenName = getKitchenNodeName();

        //create ServeRobot with reference to this SimulationEngine instance
        ServeRobot robot = new ServeRobot(
            simGraph,
            graphModel,
            kitchenName,
            tripQueue,
            this  //pass the current SimulationEngine instance
        );

        //get the route first
        List<String> route = robot.calculateRoute();
        
        //notify listeners about the dispatch with the route
        notifyRobotDispatched(new ArrayList<>(trip), route);
        
        robotBusy = true;
        
        //start serving in a separate thread
        new Thread(() -> {
            robot.serve();
        }).start();
    }
    
    //Check if all orders are complete - ONLY IF SIMULATION IS ACTUALLY RUNNING
    //don't check immediately at startup, only after some time has passed
    //add delay before checking completion
    if (System.currentTimeMillis() - simulationStartTime > 5000) {
        if (isAllComplete()) {
            //only notify once
            if (!simulationCompleted) {
                simulationCompleted = true;
                System.out.println("[SIMULATION] All orders served! Simulation complete.");
                Platform.runLater(() -> {
                    notifySimulationComplete();
                    notifyTabControlListeners(); //added this line
                });
            }
        }
    }
}

    //UI
    public ChefQueue[] chefQueues() { return chefs; }
    public RobotQueue robotQueue() { return robotQ; }
    public boolean isRobotBusy() { return robotBusy; }

    //receive components from the gridEditor UI
    public GraphModel getGraphModel() {
        return graphModel;
    }

    //managing UI tabs
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


    //notification for reset
    private void notifyReset() {
        Platform.runLater(() -> {
            for (OrderListener listener : orderListeners) {
                if (listener instanceof ResetListener) {
                    ((ResetListener) listener).onReset();
                }
            }
        });
    }

    //listener for reset
    public interface ResetListener {
        void onReset();
    }

    private final List<ResetListener> resetListeners = new ArrayList<>();

    //register reset listener
    public void addResetListener(ResetListener listener) {
        resetListeners.add(listener);
    }

    //notification for rest
    private void notifyResetListeners() {
        for (ResetListener listener : resetListeners) {
            listener.onReset();
        }
    }

    //reset the sim state
    public void resetState() {
        //stop the timeline if it's running
        tickTimeline.stop();
        
        //reset all chef queues
        for (ChefQueue cq : chefs) {
            cq.clear();
        }
        
        //clear the robot queue
        robotQ.clear();
        
        //reset the robot busy state
        robotBusy = false;
        
        //reset simulation completion flag
        simulationCompleted = false;
        
        //clear any stored timestamps
        simulationStartTime = 0;
        lastEmptyQueueTime = 0;
        
        //notify listeners about reset
        Platform.runLater(() -> {
            notifyResetListeners();
        });
    }
}