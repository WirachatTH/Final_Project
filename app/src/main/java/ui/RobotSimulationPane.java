package ui;

import javafx.animation.PathTransition;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.util.Callback;
import model.GraphModel;
import model.Order;
import sim.SimulationEngine;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RobotSimulationPane extends BorderPane implements SimulationEngine.ResetListener {
    private static final int CELL_SIZE = 60;
    private static final int CELLS = 8;

    private Label timerLabel;
    private Timeline timerTimeline;
    private long startTimeMillis;
    private int elapsedSeconds = 0;

    private final SimulationEngine sim;
    private final GraphModel graphModel;
    private final Pane gridPane;
    private final TableView<RobotCargo> cargoTable;
    private final Label statusLabel;
    private Circle robotDot;
    private boolean initialized = false;
    private PathTransition robotAnimation;
    private boolean isAnimationRunning = false;

    private Set<String> tablesToServe = new HashSet<>();
    
    //map to track which tables have food ordered
    private final Map<String, Circle> foodMarkers = new HashMap<>();
    
    //track is the table has been served to prevent duplication in one route. (K -> T2-1 -> T2-1 -> K)
    private final Set<String> deliveredTables = new HashSet<>();
    private final boolean DEBUG = true;

    public static class RobotCargo { //robot cargo containing food, its table
        private final String food;
        private final String table;

        public RobotCargo(String food, String table) {
            this.food = food;
            this.table = table;
        }

        public String getFood() { return food; }
        public String getTable() { return table; }
    }

    public RobotSimulationPane(SimulationEngine sim) {
        this.sim = sim;
        //add this pane as a reset listener
        if (sim != null) {
            sim.addResetListener(this);
        }

        this.graphModel = sim.getGraphModel();
        
        //grid pane for the restaurant layout visualization
        gridPane = new Pane();
        gridPane.setPrefSize(CELLS * CELL_SIZE, CELLS * CELL_SIZE);
        gridPane.getStyleClass().add("grid-editor-root");
        
        //robot representation
        robotDot = new Circle(10, Color.GREEN);
        robotDot.setStroke(Color.BLACK);
        robotDot.setVisible(false);
        
        //cargo table setup
        cargoTable = new TableView<>();
        cargoTable.setPlaceholder(new Label("Robot is not carrying any food"));

        //food column - use explicit cell factory
        TableColumn<RobotCargo, String> foodCol = new TableColumn<>("Food");
        foodCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<RobotCargo, String>, javafx.beans.value.ObservableValue<String>>() {
            @Override
            public javafx.beans.value.ObservableValue<String> call(TableColumn.CellDataFeatures<RobotCargo, String> data) {
                return new SimpleStringProperty(data.getValue().getFood());
            }
        });
        foodCol.setPrefWidth(200);

        //table column - use explicit cell factory
        TableColumn<RobotCargo, String> tableCol = new TableColumn<>("Table");
        tableCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<RobotCargo, String>, javafx.beans.value.ObservableValue<String>>() {
            @Override
            public javafx.beans.value.ObservableValue<String> call(TableColumn.CellDataFeatures<RobotCargo, String> data) {
                return new SimpleStringProperty(data.getValue().getTable());
            }
        });
        tableCol.setPrefWidth(100);

        cargoTable.getColumns().addAll(foodCol, tableCol);
        cargoTable.setItems(FXCollections.observableArrayList());
        cargoTable.setPrefHeight(150);
        
        //status label
        statusLabel = new Label("Waiting for simulation to start...");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        
        //initial placeholder
        Label placeholder = new Label("Simulation not started yet.\nDesign your restaurant layout and start the simulation to see robot movement.");
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setStyle("-fx-font-size: 32px; -fx-text-fill: #FFFFFF; -fx-font-weight: bold");
        
        //timer label
        timerLabel = new Label("Timer: 00:00");
        timerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");

        //layout
        VBox infoBox = new VBox(10);
        infoBox.setPadding(new Insets(10));
        Label cargo = new Label("Robot Cargo:");
        cargo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        infoBox.getChildren().addAll(
            cargo, 
            cargoTable,
            new HBox(10, statusLabel, timerLabel) //timer next to status
        );
        
        setCenter(placeholder);
        setBottom(infoBox);
        
        //set up listeners for all events
        setupEventListeners();
    }

    @Override
    public void onReset() { //when the reset was clicked
        Platform.runLater(() -> {
            System.out.println("[DEBUG] Robot simulation pane reset beginning");
            
            //stop any running animations
            if (isAnimationRunning && robotAnimation != null) {
                robotAnimation.stop();
                isAnimationRunning = false;
            }
            
            //clear any running timers
            stopTimer();
            
            //clear food markers
            clearFoodMarkers();
            
            //clear tables to serve set
            tablesToServe.clear();
            
            //clear delivered tables set
            deliveredTables.clear();
            
            //clear cargo table
            cargoTable.getItems().clear();
            cargoTable.refresh();
            
            //reset status label
            statusLabel.setText("Ready for new simulation");
            
            //reset timer label
            timerLabel.setText("Timer: 00:00");
            
            //remove the grid and add back the placeholder
            setCenter(null);
            
            //create and set the placeholder
            Label placeholder = new Label("Simulation not started yet.\nDesign your restaurant layout and start the simulation to see robot movement.");
            placeholder.setAlignment(Pos.CENTER);
            placeholder.setStyle("-fx-font-size: 32px; -fx-text-fill: #FFFFFF; -fx-font-weight: bold");
            setCenter(placeholder);
            
            //change initialized back to false
            initialized = false;
            
            System.out.println("[DEBUG] Robot simulation pane reset completed");
        });
    }
    
    //add a method to rebuild the grid from scratch
    private void rebuildGrid() {
        //clear the grid pane
        gridPane.getChildren().clear();
        
        //redraw grid lines
        drawGridLines();
        
        //redraw all nodes (tables, kitchen, junctions)
        for (GraphModel.Node node : graphModel.nodes()) {
            // Redraw node (existing node drawing code)
        }
        
        //add the robot dot back
        gridPane.getChildren().add(robotDot);
    }

    private void setupEventListeners() {
        //clear the tablesToServe set in the simulation start listener
        sim.addSimulationStartListener(new SimulationEngine.SimulationStartListener() { //listener for sim start
            @Override
            public void onSimulationStart() {
                Platform.runLater(() -> {
                    //always fully reinitialize the layout
                    initializeLayout();
                    initialized = true;
                    
                    //clear any existing food markers
                    clearFoodMarkers();
                    //clear delivered tables set
                    deliveredTables.clear();
                    //clear tables to serve set
                    tablesToServe.clear();
                    //reset the status label
                    statusLabel.setText("Simulation started");
                    
                    //start the timer
                    startTimer();
                    
                    System.out.println("[DEBUG] Simulation started - grid initialized");
                });
            }
        });

        sim.addSimulationCompletionListener(new SimulationEngine.SimulationCompletionListener() { //listener for sim completion
            @Override
            public void onSimulationComplete() {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if (initialized && gridPane.getParent() != null) {
                            statusLabel.setText("Simulation ends");
                            System.out.println("[UI] Simulation completed - updating status label");

                            
                            //stop the timer
                            stopTimer();
                            
                            //calculate final time and format the total elapsed time
                            long totalElapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000;
                            int minutes = (int)(totalElapsedSeconds / 60);
                            int seconds = (int)(totalElapsedSeconds % 60);
                            timerLabel.setText(String.format("Total time: %02d:%02d", minutes, seconds));
                        }
                    }
                });
            }
        });
        
        //when receiving orders in the dispatch, record which tables need food
        sim.addRobotDispatchListener(new SimulationEngine.RobotDispatchListener() {
            @Override
            public void onRobotDispatch(List<Order> orders, List<String> route) {
                if (DEBUG) {
                    System.out.println("---------------------------------------");
                    System.out.println("[DEBUG] Robot dispatch event with " + orders.size() + " orders");
                    System.out.println("[DEBUG] Route: " + String.join(" -> ", route));
                }
                
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        //reset delivered tables for new round
                        deliveredTables.clear();
                        
                        //reset table state
                        cargoTable.getItems().clear();
                        cargoTable.refresh();
                        
                        //clear and rebuild the tables to serve set
                        tablesToServe.clear();
                        
                        //update cargo table with new orders and record tables to serve
                        updateCargoTable(orders);
                        
                        //record which tables should receive food (from orders)
                        for (Order order : orders) {
                            String tableName = getNodeName(order.tableNumber());
                            tablesToServe.add(tableName);
                            if (DEBUG) {
                                System.out.println("[DEBUG] Table " + tableName + " will receive food");
                            }
                        }
                        
                        //start animation after a short delay
                        PauseTransition pause = new PauseTransition(Duration.millis(200));
                        pause.setOnFinished(e -> animateRobotAlongRoute(route, orders));
                        pause.play();
                    }
                });
            }
        });
        
        //listen for delivery events from actual ServeRobot
        sim.addDeliveryListener(new SimulationEngine.DeliveryListener() {
            @Override
            public void onDelivery(String tableName) {
                if (DEBUG) {
                    System.out.println("[DEBUG] Delivery event from ServeRobot for table: " + tableName);
                }
            }
        });
        
        //listen for simulation completion
        sim.addSimulationCompletionListener(new SimulationEngine.SimulationCompletionListener() {
            @Override
            public void onSimulationComplete() {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        statusLabel.setText("Simulation ends");
                        System.out.println("[UI] Simulation completed - updating status label");
                    }
                });
            }
        });
    }
    
    private void addFoodToTable(String tableName) { //add a notification mark when the food is served
        //find the node by name
        GraphModel.Node tableNode = findNodeByName(tableName);
        if (tableNode == null) {
            System.err.println("[ERROR] Could not find table node: " + tableName);
            return;
        }
        
        //remove existing food marker if any
        Circle existingMarker = foodMarkers.get(tableName);
        if (existingMarker != null) {
            gridPane.getChildren().remove(existingMarker);
        }
        
        //create a small circle to represent food
        Circle foodMarker = new Circle(tableNode.x() + 15, tableNode.y() - 15, 8, Color.ORANGE);
        foodMarker.setStroke(Color.BLACK);
        foodMarker.setStrokeWidth(1);
        
        //add to grid and track in map
        gridPane.getChildren().add(foodMarker);
        foodMarkers.put(tableName, foodMarker);
        
        System.out.println("[FOOD] Added food marker to table: " + tableName);
    }

    private void clearFoodMarkers() { //clear all food markers
        for (Circle marker : foodMarkers.values()) {
            gridPane.getChildren().remove(marker);
        }
        foodMarkers.clear();
        System.out.println("[FOOD] Cleared all food markers");
    }
    
    private void initializeLayout() { //redrawing the entire grid UI
        //clear placeholder and ensure gridPane is empty before rebuilding
        setCenter(null);
        gridPane.getChildren().clear();
        
        //draw the grid lines
        drawGridLines();
        
        //draw all nodes (tables, kitchen, junctions)
        for (GraphModel.Node node : graphModel.nodes()) {
            double x = node.x();
            double y = node.y();
            
            Circle circle = new Circle(x, y, CELL_SIZE * 0.35, Color.web(node.type().colorHex));
            circle.setStroke(Color.BLACK);
            
            Text label = new Text(node.name());
            label.setX(x - label.getBoundsInLocal().getWidth() / 2);
            label.setY(y);
            
            gridPane.getChildren().addAll(circle, label);
        }
        
        //draw all paths
        for (GraphModel.Edge edge : graphModel.edges()) {
            GraphModel.Node src = findNode(edge.from);
            GraphModel.Node dst = findNode(edge.to);
            
            if (src != null && dst != null) {
                double srcX = src.x();
                double srcY = src.y();
                double dstX = dst.x();
                double dstY = dst.y();
                
                //if have cell path points, draw segments through them
                if (edge.cells != null && !edge.cells.isEmpty()) {
                    double prevX = srcX;
                    double prevY = srcY;
                    
                    for (Point p : edge.cells) {
                        double cellX = p.x * CELL_SIZE + CELL_SIZE / 2.0;
                        double cellY = p.y * CELL_SIZE + CELL_SIZE / 2.0;
                        
                        Line segment = new Line(prevX, prevY, cellX, cellY);
                        segment.getStyleClass().add("path-line");
                        segment.setStrokeWidth(3);
                        segment.setStroke(Color.RED);
                        gridPane.getChildren().add(segment);
                        
                        prevX = cellX;
                        prevY = cellY;
                    }
                    
                    //final segment to destination
                    Line lastSegment = new Line(prevX, prevY, dstX, dstY);
                    lastSegment.getStyleClass().add("path-line");
                    lastSegment.setStrokeWidth(3);
                    lastSegment.setStroke(Color.RED);
                    gridPane.getChildren().add(lastSegment);
                } else {
                    //direct path if no cells specified
                    Line directPath = new Line(srcX, srcY, dstX, dstY);
                    directPath.getStyleClass().add("path-line");
                    directPath.setStrokeWidth(3);
                    directPath.setStroke(Color.RED);
                    gridPane.getChildren().add(directPath);
                }
            }
        }
        
        //add the robot to the kitchen position
        String kitchenId = graphModel.kitchenId().orElse(null);
        if (kitchenId != null) {
            GraphModel.Node kitchen = findNode(kitchenId);
            if (kitchen != null) {
                //(re)create the robot dot to ensure it's born in the kitchen
                robotDot = new Circle(10, Color.GREEN);
                robotDot.setStroke(Color.BLACK);
                robotDot.setCenterX(kitchen.x());
                robotDot.setCenterY(kitchen.y());
                robotDot.setVisible(true);
                gridPane.getChildren().add(robotDot);
            }
        }
        
        //setting the gridPane as the center
        setCenter(gridPane);
        
        System.out.println("[DEBUG] Grid layout initialized with " + graphModel.nodes().size() + " nodes and " + 
                           graphModel.edges().size() + " edges");
    }
     
    private void drawGridLines() { //draw gridlines for the layout in this tab
        for (int i = 0; i <= CELLS; i++) {
            Line h = new Line(0, i * CELL_SIZE, CELLS * CELL_SIZE, i * CELL_SIZE);
            Line v = new Line(i * CELL_SIZE, 0, i * CELL_SIZE, CELLS * CELL_SIZE);
            h.setStroke(Color.BLACK);
            v.setStroke(Color.BLACK);
            gridPane.getChildren().addAll(h, v);
        }
    }
    
    private GraphModel.Node findNode(String id) { //find a node by its ID
        for (GraphModel.Node node : graphModel.nodes()) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        return null;
    }
    
    private GraphModel.Node findNodeByName(String name) { //find a node by its name
        for (GraphModel.Node node : graphModel.nodes()) {
            if (node.name().equals(name)) {
                return node;
            }
        }
        return null;
    }
    
    private GraphModel.Edge findEdge(String idA, String idB) { //find an edge between two nodes(ID)
        for (GraphModel.Edge edge : graphModel.edges()) {
            if ((edge.from.equals(idA) && edge.to.equals(idB)) ||
                (edge.from.equals(idB) && edge.to.equals(idA))) {
                return edge;
            }
        }
        return null;
    }
    
    private void updateCargoTable(List<Order> orders) { //update the cargo table
        if (DEBUG) {
            System.out.println("[DEBUG] Updating cargo table with " + orders.size() + " orders");
        }
        
        //create a new list for the cargo items
        List<RobotCargo> cargoItems = new ArrayList<>();
        
        //add each order to the list
        for (Order order : orders) {
            String tableName = getNodeName(order.tableNumber());
            String foodName = order.dish().name;
            if (DEBUG) {
                System.out.println("[DEBUG] Adding cargo item: " + foodName + " for " + tableName);
            }
            cargoItems.add(new RobotCargo(foodName, tableName));
        }
        
        //create a new observable list
        ObservableList<RobotCargo> observableItems = FXCollections.observableArrayList(cargoItems);
        
        //clear existing items first
        cargoTable.getItems().clear();
        
        //set the new items
        cargoTable.setItems(observableItems);
        
        //log state after update
        if (DEBUG) {
            System.out.println("[DEBUG] Table now has " + cargoTable.getItems().size() + " items");
        }
        
        //make sure table is visible
        cargoTable.setVisible(true);
        
        //force refresh
        cargoTable.refresh();
    }
      
    private String getNodeName(int tableNumber) { //get node name by its number
        return graphModel.nodes().stream()
            .filter(n -> graphModel.getNodeInfo(n.id())
                .filter(info -> info.kind == GraphModel.NodeKind.TABLE && info.number == tableNumber)
                .isPresent()
            )
            .findFirst()
            .map(GraphModel.Node::name)
            .orElse(String.valueOf(tableNumber));
    }
    
    private void handleTableDelivery(String tableName) { //only mark tables that should receive food
        if (deliveredTables.contains(tableName)) {
            return;
        }
        
        System.out.println("[DELIVERY] Processing delivery for table: " + tableName);
        
        //only add food marker if this table is in the tablesToServe set
        if (tablesToServe.contains(tableName)) {
            //manually add visual food marker to the table
            addFoodToTable(tableName);
            
            //check if there are any cargo items for this table
            List<RobotCargo> deliveredItems = new ArrayList<>();
            
            for (RobotCargo cargo : cargoTable.getItems()) {
                if (cargo.getTable().equals(tableName)) {
                    deliveredItems.add(cargo);
                }
            }
            
            if (!deliveredItems.isEmpty()) {
                System.out.println("[DELIVERY] Delivering " + deliveredItems.size() + " food items to " + tableName);
                
                //remove items from cargo table
                cargoTable.getItems().removeAll(deliveredItems);
                cargoTable.refresh();
                
                //also notify the simulation engine (original mechanism)
                //this is important for the simulation logic
                sim.notifyDelivery(tableName);
            } else {
                System.out.println("[DELIVERY WARNING] No matching cargo items found for " + tableName);
            }
        } else {
            System.out.println("[PASS] Robot passing through table " + tableName + " (no delivery)");
        }
        
        //mark as delivered/visited to avoid duplicate deliveries
        deliveredTables.add(tableName);
    }
    
    private int getNodeIndexInRoute(String nodeName, List<String> route) { //get a node index in a serving route
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).equals(nodeName)) {
                return i;
            }
        }
        return -1;
    }
    
    private Map<String, Double> calculateNodeDistances(List<String> route) { //calculate the distance between each pair of nodes in this route
        Map<String, Double> distances = new HashMap<>();
        
        if (route.isEmpty()) {
            return distances;
        }
        
        //start with first node
        String currentNodeName = route.get(0);
        GraphModel.Node currentNode = findNodeByName(currentNodeName);
        
        //first node is at distance 0
        distances.put(currentNodeName, 0.0);
        double totalDistance = 0.0;
        
        //calculate distance to each subsequent node
        for (int i = 1; i < route.size(); i++) {
            String nextNodeName = route.get(i);
            GraphModel.Node nextNode = findNodeByName(nextNodeName);
            
            if (nextNode == null) {
                continue;
            }
            
            //find the edge between current and next node
            GraphModel.Edge edge = findEdge(currentNode.id(), nextNode.id());
            double segmentDistance = 0.0;
            
            if (edge != null) {
                segmentDistance = edge.weight + 1; //add 1 for the edge itself
            } else {
                //calculate direct distance if no edge found
                segmentDistance = Math.sqrt(
                    Math.pow(nextNode.x() - currentNode.x(), 2) + 
                    Math.pow(nextNode.y() - currentNode.y(), 2)
                ) / CELL_SIZE;
            }
            
            totalDistance += segmentDistance;
            distances.put(nextNodeName, totalDistance);
            
            //move to next node
            currentNode = nextNode;
        }
        
        return distances;
    }
    
    private Path createPathFromRoute(List<String> route) { //create an object following the route
        Path path = new Path();
        
        if (route.isEmpty()) {
            return path;
        }
        
        String currentNodeName = route.get(0);
        GraphModel.Node currentNode = findNodeByName(currentNodeName);
        if (currentNode == null) return path;
        
        //start at first node
        path.getElements().add(new MoveTo(currentNode.x(), currentNode.y()));
        
        //follow each segment in the route
        for (int i = 1; i < route.size(); i++) {
            String nextNodeName = route.get(i);
            GraphModel.Node nextNode = findNodeByName(nextNodeName);
            
            if (nextNode == null) continue;
            
            //find the edge between current and next node
            GraphModel.Edge edge = findEdge(currentNode.id(), nextNode.id());
            
            if (edge != null && edge.cells != null && !edge.cells.isEmpty()) {
                //check if we need to reverse the cell points based on direction
                List<Point> pathCells = new ArrayList<>(edge.cells);
                boolean reversed = edge.from.equals(nextNode.id());
                
                if (reversed) {
                    //reverse the cell points if we're going in the opposite direction
                    List<Point> reversedCells = new ArrayList<>();
                    for (int j = pathCells.size() - 1; j >= 0; j--) {
                        reversedCells.add(pathCells.get(j));
                    }
                    pathCells = reversedCells;
                }
                
                //follow the path through the edge's cell points
                for (Point cellPoint : pathCells) {
                    double cellX = cellPoint.x * CELL_SIZE + CELL_SIZE / 2.0;
                    double cellY = cellPoint.y * CELL_SIZE + CELL_SIZE / 2.0;
                    path.getElements().add(new LineTo(cellX, cellY));
                }
                
                //add final segment to destination node
                path.getElements().add(new LineTo(nextNode.x(), nextNode.y()));
            } else {
                //direct edge (no path cells) - just draw straight line
                path.getElements().add(new LineTo(nextNode.x(), nextNode.y()));
            }
            
            //move to next node
            currentNode = nextNode;
        }
        
        return path;
    }
    
    private void animateRobotAlongRoute(List<String> route, List<Order> orders) { //robot animation
        //cancel any existing animation
        if (isAnimationRunning) {
            if (robotAnimation != null) {
                robotAnimation.stop();
            }
            isAnimationRunning = false;
        }
        
        if (route.isEmpty()) {
            return;
        }
        
        //reset delivered tables for new round
        deliveredTables.clear();
        
        statusLabel.setText("Robot in motion: " + String.join(" -> ", route));
        
        //find all tables in the route (not kitchen or junction)
        List<String> tableNodesInRoute = new ArrayList<>();
        for (String nodeName : route) {
            if (!nodeName.startsWith("K") && !nodeName.startsWith("J")) {
                tableNodesInRoute.add(nodeName);
                System.out.println("[TABLE] Table " + nodeName + " is in route");
            }
        }
        
        //calculate exact node distances
        Map<String, Double> nodeDistances = calculateNodeDistances(route);
        
        //create the path
        Path path = createPathFromRoute(route);
        
        //get total distance (from last node)
        double totalDistance = nodeDistances.getOrDefault(route.get(route.size() - 1), 0.0);
        if (totalDistance == 0.0) {
            //fallback if distances couldn't be calculated
            totalDistance = route.size();
        }
        
        //create the animation
        PathTransition transition = new PathTransition();
        transition.setDuration(Duration.seconds(0.5 * totalDistance)); //0.5 second per block
        transition.setPath(path);
        transition.setNode(robotDot);
        transition.setCycleCount(1);
        
        //set LINEAR interpolator for constant speed motion
        transition.setInterpolator(javafx.animation.Interpolator.LINEAR);
        
        //keep track of animation
        robotAnimation = transition;
        
        //schedule deliveries using timed animations
        for (String tableName : tableNodesInRoute) {
            //find exact node index - important to get right route position
            int nodeIndex = getNodeIndexInRoute(tableName, route);
            if (nodeIndex == -1) continue;
            
            //use calculated distance from node distances map
            double exactDistance = nodeDistances.getOrDefault(tableName, 0.0);
            
            //create a pause transition that waits until it's time to deliver
            //slightly adjust timing (subtract a tiny bit) to ensure we're exactly at the node
            double deliveryTime = 0.5 * exactDistance;
            
            System.out.println("[TIMING] Table " + tableName + " at index " + nodeIndex + 
                              ", distance: " + exactDistance + 
                              ", delivery time: " + deliveryTime + " seconds");
            
            PauseTransition deliveryTimer = new PauseTransition(Duration.seconds(deliveryTime));
            deliveryTimer.setOnFinished(e -> {
                System.out.println("[TIMED] Time to deliver to: " + tableName);
                
                //manually trigger delivery
                handleTableDelivery(tableName);
            });
            deliveryTimer.play();
        }
        
        //start the animation
        isAnimationRunning = true;
        transition.play();
        
        //when animation completes...
        transition.setOnFinished(e -> {
            //first notify we're done
            isAnimationRunning = false;
            statusLabel.setText("Robot returned to kitchen");
            
            //clear all food markers when the robot returns to kitchen
            clearFoodMarkers();
            
            //clear the cargo table
            cargoTable.getItems().clear();
            cargoTable.refresh();
            System.out.println("[DEBUG] Cleared cargo table after animation");
            
            //create a slight delay before notifying simulation engine
            PauseTransition completionDelay = new PauseTransition(Duration.millis(500));
            completionDelay.setOnFinished(event -> {
                //notify simulation engine that movement is complete
                sim.notifyRobotMovementComplete();
            });
            completionDelay.play();
        });
    }

    private void startTimer() {
        //reset timer state
        elapsedSeconds = 0;
        if (timerTimeline != null) {
            timerTimeline.stop();
        }
        
        //record start time
        startTimeMillis = System.currentTimeMillis();
        
        //update timer label
        updateTimerLabel();
        
        //create and start the timer
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            elapsedSeconds++;
            updateTimerLabel();
        }));
        timerTimeline.setCycleCount(Timeline.INDEFINITE);
        timerTimeline.play();
    }

    private void stopTimer() {
        if (timerTimeline != null) {
            timerTimeline.stop();
        }
    }

    private void updateTimerLabel() {
        int minutes = elapsedSeconds / 60;
        int seconds = elapsedSeconds % 60;
        timerLabel.setText(String.format("Timer: %02d:%02d", minutes, seconds));
    }

    public String getCurrentTimerText() {
        //extract just the time part (MM:SS) from the timer label
        String labelText = timerLabel.getText();
        if (labelText.startsWith("Timer: ")) {
            return labelText.substring(7); //remove "Timer: "
        } else if (labelText.startsWith("Total time: ")) {
            return labelText.substring(12); //remove "Total time: "
        }
        return labelText;
    }
}
