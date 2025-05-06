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

/**
 * Visual simulation of the robot's movement and cargo.
 * ADJUSTED TIMING VERSION - Ensures food markers appear exactly when the robot reaches a table.
 */
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
    
    // Map to track which tables have food on them - keyed by table name
    private final Map<String, Circle> foodMarkers = new HashMap<>();
    
    // Track delivered tables to prevent duplicates
    private final Set<String> deliveredTables = new HashSet<>();
    
    // Debug flag
    private final boolean DEBUG = true;

    /**
     * Data model for robot's cargo - simplified for reliability.
     */
    public static class RobotCargo {
        private final String food;
        private final String table;

        public RobotCargo(String food, String table) {
            this.food = food;
            this.table = table;
        }

        public String getFood() { return food; }
        public String getTable() { return table; }
    }

    /**
     * Constructor for the robot simulation pane.
     */
    public RobotSimulationPane(SimulationEngine sim) {
        this.sim = sim;
        // Add this pane as a reset listener
        if (sim != null) {
            sim.addResetListener(this);
        }

        this.graphModel = sim.getGraphModel();
        
        // Grid pane for the restaurant layout visualization
        gridPane = new Pane();
        gridPane.setPrefSize(CELLS * CELL_SIZE, CELLS * CELL_SIZE);
        gridPane.getStyleClass().add("grid-editor-root");
        
        // Robot representation
        robotDot = new Circle(10, Color.GREEN);
        robotDot.setStroke(Color.BLACK);
        robotDot.setVisible(false);
        
        // Cargo table setup - simplify
        cargoTable = new TableView<>();
        cargoTable.setPlaceholder(new Label("Robot is not carrying any food"));

        // Food column - use explicit cell factory
        TableColumn<RobotCargo, String> foodCol = new TableColumn<>("Food");
        foodCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<RobotCargo, String>, javafx.beans.value.ObservableValue<String>>() {
            @Override
            public javafx.beans.value.ObservableValue<String> call(TableColumn.CellDataFeatures<RobotCargo, String> data) {
                return new SimpleStringProperty(data.getValue().getFood());
            }
        });
        foodCol.setPrefWidth(200);

        // Table column - use explicit cell factory
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
        
        // Status label
        statusLabel = new Label("Waiting for simulation to start...");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        
        // Initial placeholder
        Label placeholder = new Label("Simulation not started yet.\nDesign your restaurant layout and start the simulation to see robot movement.");
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setStyle("-fx-font-size: 32px; -fx-text-fill: #FFFFFF; -fx-font-weight: bold");
        
        // Timer label
        timerLabel = new Label("Timer: 00:00");
        timerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");

        // Layout
        VBox infoBox = new VBox(10);
        infoBox.setPadding(new Insets(10));
        Label cargo = new Label("Robot Cargo:");
        cargo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        infoBox.getChildren().addAll(
            cargo, 
            cargoTable,
            new HBox(10, statusLabel, timerLabel) // Timer next to status
        );
        
        setCenter(placeholder);
        setBottom(infoBox);
        
        // Event listeners
        setupEventListeners();
    }

    @Override
    public void onReset() {
        Platform.runLater(() -> {
            // Stop any running animations
            if (isAnimationRunning && robotAnimation != null) {
                robotAnimation.stop();
                isAnimationRunning = false;
            }
            
            // Clear any running timers
            stopTimer();
            
            // Clear food markers
            clearFoodMarkers();
            
            // Clear tables to serve set
            tablesToServe.clear();
            
            // Clear delivered tables set
            deliveredTables.clear();
            
            // Clear cargo table
            cargoTable.getItems().clear();
            cargoTable.refresh();
            
            // Reset status label
            statusLabel.setText("Ready for new simulation");
            
            // Reset timer label
            timerLabel.setText("Timer: 00:00");
            
            // Remove the grid and add back the placeholder
            // THIS IS THE KEY FIX:
            setCenter(null); // First remove the current center content
            
            // Create and set the placeholder
            Label placeholder = new Label("Simulation not started yet.\nDesign your restaurant layout and start the simulation to see robot movement.");
            placeholder.setAlignment(Pos.CENTER);
            placeholder.setStyle("-fx-font-size: 32px; -fx-text-fill: #FFFFFF; -fx-font-weight: bold");
            setCenter(placeholder);
            
            // Reset the initialized flag so next time simulation starts it will rebuild the grid
            initialized = false;
        });
    }
    
    // Add a method to rebuild the grid from scratch
    private void rebuildGrid() {
        // Clear the grid pane
        gridPane.getChildren().clear();
        
        // Redraw grid lines
        drawGridLines();
        
        // Redraw all nodes (tables, kitchen, junctions)
        for (GraphModel.Node node : graphModel.nodes()) {
            // Redraw node (existing node drawing code)
        }
        
        // Add the robot dot back
        gridPane.getChildren().add(robotDot);
    }

    
    /**
     * Set up event listeners for simulation events.
     */
    private void setupEventListeners() {
        // 4. Make sure to clear the tablesToServe set in the simulation start listener
        sim.addSimulationStartListener(new SimulationEngine.SimulationStartListener() {
            @Override
            public void onSimulationStart() {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if (!initialized) {
                            initializeLayout();
                            initialized = true;
                        }
                        // Clear any existing food markers
                        clearFoodMarkers();
                        // Clear delivered tables set
                        deliveredTables.clear();
                        // Clear tables to serve set
                        tablesToServe.clear();
                        // Reset the status label
                        statusLabel.setText("Simulation started");
                        
                        // Start the timer
                        startTimer();
                    }
                });
            }
        });

        sim.addSimulationCompletionListener(new SimulationEngine.SimulationCompletionListener() {
            @Override
            public void onSimulationComplete() {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if (initialized && gridPane.getParent() != null) {
                            statusLabel.setText("Simulation ends");
                            System.out.println("[UI] Simulation completed - updating status label");
                            
                            // Stop the timer
                            stopTimer();
                            
                            // Calculate final time and format the total elapsed time
                            long totalElapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000;
                            int minutes = (int)(totalElapsedSeconds / 60);
                            int seconds = (int)(totalElapsedSeconds % 60);
                            timerLabel.setText(String.format("Total time: %02d:%02d", minutes, seconds));
                        }
                    }
                });
            }
        });
        
        // 2. When receiving orders in the dispatch, record which tables need food
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
                        // Reset delivered tables for new round
                        deliveredTables.clear();
                        
                        // Reset table state
                        cargoTable.getItems().clear();
                        cargoTable.refresh();
                        
                        // IMPORTANT: Clear and rebuild the tables to serve set
                        tablesToServe.clear();
                        
                        // Update cargo table with new orders and record tables to serve
                        updateCargoTable(orders);
                        
                        // Record which tables should receive food (from orders)
                        for (Order order : orders) {
                            String tableName = getNodeName(order.tableNumber());
                            tablesToServe.add(tableName);
                            if (DEBUG) {
                                System.out.println("[DEBUG] Table " + tableName + " will receive food");
                            }
                        }
                        
                        // Start animation after a short delay
                        PauseTransition pause = new PauseTransition(Duration.millis(200));
                        pause.setOnFinished(e -> animateRobotAlongRoute(route, orders));
                        pause.play();
                    }
                });
            }
        });
        
        // Listen for delivery events from actual ServeRobot - keep for compatibility
        sim.addDeliveryListener(new SimulationEngine.DeliveryListener() {
            @Override
            public void onDelivery(String tableName) {
                if (DEBUG) {
                    System.out.println("[DEBUG] Delivery event from ServeRobot for table: " + tableName);
                }
            }
        });
        
        // Listen for simulation completion
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
    
    /**
     * Add a visual food marker to a table
     */
    private void addFoodToTable(String tableName) {
        // Find the node by name
        GraphModel.Node tableNode = findNodeByName(tableName);
        if (tableNode == null) {
            System.err.println("[ERROR] Could not find table node: " + tableName);
            return;
        }
        
        // Remove existing food marker if any
        Circle existingMarker = foodMarkers.get(tableName);
        if (existingMarker != null) {
            gridPane.getChildren().remove(existingMarker);
        }
        
        // Create a small circle to represent food
        Circle foodMarker = new Circle(tableNode.x() + 15, tableNode.y() - 15, 8, Color.ORANGE);
        foodMarker.setStroke(Color.BLACK);
        foodMarker.setStrokeWidth(1);
        
        // Add to grid and track in map
        gridPane.getChildren().add(foodMarker);
        foodMarkers.put(tableName, foodMarker);
        
        System.out.println("[FOOD] Added food marker to table: " + tableName);
    }
    
    /**
     * Clear all food markers from the grid
     */
    private void clearFoodMarkers() {
        for (Circle marker : foodMarkers.values()) {
            gridPane.getChildren().remove(marker);
        }
        foodMarkers.clear();
        System.out.println("[FOOD] Cleared all food markers");
    }
    
    /**
     * Initialize the layout by drawing the restaurant grid.
     */
    private void initializeLayout() {
        // Clear placeholder
        setCenter(null);
        
        // Draw the grid lines
        drawGridLines();
        
        // Draw all nodes (tables, kitchen, junctions)
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
        
        // Draw all paths
        for (GraphModel.Edge edge : graphModel.edges()) {
            GraphModel.Node src = findNode(edge.from);
            GraphModel.Node dst = findNode(edge.to);
            
            if (src != null && dst != null) {
                double srcX = src.x();
                double srcY = src.y();
                double dstX = dst.x();
                double dstY = dst.y();
                
                // If we have cell path points, draw segments through them
                if (edge.cells != null && !edge.cells.isEmpty()) {
                    double prevX = srcX;
                    double prevY = srcY;
                    
                    for (Point p : edge.cells) {
                        double cellX = p.x * CELL_SIZE + CELL_SIZE / 2.0;
                        double cellY = p.y * CELL_SIZE + CELL_SIZE / 2.0;
                        
                        Line segment = new Line(prevX, prevY, cellX, cellY);
                        segment.getStyleClass().add("path-line");
                        gridPane.getChildren().add(segment);
                        
                        prevX = cellX;
                        prevY = cellY;
                    }
                    
                    // Final segment to destination
                    Line lastSegment = new Line(prevX, prevY, dstX, dstY);
                    lastSegment.getStyleClass().add("path-line");
                    gridPane.getChildren().add(lastSegment);
                } else {
                    // Direct path if no cells specified
                    Line directPath = new Line(srcX, srcY, dstX, dstY);
                    directPath.getStyleClass().add("path-line");
                    gridPane.getChildren().add(directPath);
                }
            }
        }
        
        // Add the robot to the kitchen position
        String kitchenId = graphModel.kitchenId().orElse(null);
        if (kitchenId != null) {
            GraphModel.Node kitchen = findNode(kitchenId);
            if (kitchen != null) {
                robotDot.setCenterX(kitchen.x());
                robotDot.setCenterY(kitchen.y());
                robotDot.setVisible(true);
                gridPane.getChildren().add(robotDot);
            }
        }
        
        setCenter(gridPane);
    }
    
    /**
     * Draw grid lines for the layout.
     */
    private void drawGridLines() {
        for (int i = 0; i <= CELLS; i++) {
            Line h = new Line(0, i * CELL_SIZE, CELLS * CELL_SIZE, i * CELL_SIZE);
            Line v = new Line(i * CELL_SIZE, 0, i * CELL_SIZE, CELLS * CELL_SIZE);
            h.setStroke(Color.BLACK);
            v.setStroke(Color.BLACK);
            gridPane.getChildren().addAll(h, v);
        }
    }
    
    /**
     * Find a node by its ID.
     */
    private GraphModel.Node findNode(String id) {
        for (GraphModel.Node node : graphModel.nodes()) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        return null;
    }
    
    /**
     * Find a node by its name (rather than ID).
     */
    private GraphModel.Node findNodeByName(String name) {
        for (GraphModel.Node node : graphModel.nodes()) {
            if (node.name().equals(name)) {
                return node;
            }
        }
        return null;
    }
    
    /**
     * Find an edge between two nodes by their IDs.
     */
    private GraphModel.Edge findEdge(String idA, String idB) {
        for (GraphModel.Edge edge : graphModel.edges()) {
            if ((edge.from.equals(idA) && edge.to.equals(idB)) ||
                (edge.from.equals(idB) && edge.to.equals(idA))) {
                return edge;
            }
        }
        return null;
    }
    
    /**
     * Update the cargo table with the current orders.
     */
    private void updateCargoTable(List<Order> orders) {
        if (DEBUG) {
            System.out.println("[DEBUG] Updating cargo table with " + orders.size() + " orders");
        }
        
        // Create a new list for the cargo items
        List<RobotCargo> cargoItems = new ArrayList<>();
        
        // Add each order to the list
        for (Order order : orders) {
            String tableName = getNodeName(order.tableNumber());
            String foodName = order.dish().name;
            if (DEBUG) {
                System.out.println("[DEBUG] Adding cargo item: " + foodName + " for " + tableName);
            }
            cargoItems.add(new RobotCargo(foodName, tableName));
        }
        
        // Create a new observable list
        ObservableList<RobotCargo> observableItems = FXCollections.observableArrayList(cargoItems);
        
        // Clear existing items first
        cargoTable.getItems().clear();
        
        // Set the new items
        cargoTable.setItems(observableItems);
        
        // Log state after update
        if (DEBUG) {
            System.out.println("[DEBUG] Table now has " + cargoTable.getItems().size() + " items");
        }
        
        // Make sure table is visible
        cargoTable.setVisible(true);
        
        // Force refresh
        cargoTable.refresh();
    }
        /**
     * Get the node name for a table number.
     */
    private String getNodeName(int tableNumber) {
        return graphModel.nodes().stream()
            .filter(n -> graphModel.getNodeInfo(n.id())
                .filter(info -> info.kind == GraphModel.NodeKind.TABLE && info.number == tableNumber)
                .isPresent()
            )
            .findFirst()
            .map(GraphModel.Node::name)
            .orElse(String.valueOf(tableNumber));
    }
    
    // 3. Modify the handleTableDelivery method to only mark tables that should receive food
    private void handleTableDelivery(String tableName) {
        // Skip if already delivered to this table in this round
        if (deliveredTables.contains(tableName)) {
            return;
        }
        
        System.out.println("[DELIVERY] Processing delivery for table: " + tableName);
        
        // Only add food marker if this table is in the tablesToServe set
        if (tablesToServe.contains(tableName)) {
            // Manually add visual food marker to the table
            addFoodToTable(tableName);
            
            // Check if there are any cargo items for this table
            List<RobotCargo> deliveredItems = new ArrayList<>();
            
            for (RobotCargo cargo : cargoTable.getItems()) {
                if (cargo.getTable().equals(tableName)) {
                    deliveredItems.add(cargo);
                }
            }
            
            if (!deliveredItems.isEmpty()) {
                System.out.println("[DELIVERY] Delivering " + deliveredItems.size() + " food items to " + tableName);
                
                // Remove items from cargo table
                cargoTable.getItems().removeAll(deliveredItems);
                cargoTable.refresh();
                
                // Also notify the simulation engine (original mechanism)
                // This is important for the simulation logic
                sim.notifyDelivery(tableName);
            } else {
                System.out.println("[DELIVERY WARNING] No matching cargo items found for " + tableName);
            }
        } else {
            System.out.println("[PASS] Robot passing through table " + tableName + " (no delivery)");
        }
        
        // Mark as delivered/visited to avoid duplicate deliveries
        deliveredTables.add(tableName);
    }
    
    /**
     * Gets the index of a node in the route
     */
    private int getNodeIndexInRoute(String nodeName, List<String> route) {
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).equals(nodeName)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Find the exact distance to each node in the route
     */
    private Map<String, Double> calculateNodeDistances(List<String> route) {
        Map<String, Double> distances = new HashMap<>();
        
        if (route.isEmpty()) {
            return distances;
        }
        
        // Start with first node
        String currentNodeName = route.get(0);
        GraphModel.Node currentNode = findNodeByName(currentNodeName);
        
        // First node is at distance 0
        distances.put(currentNodeName, 0.0);
        double totalDistance = 0.0;
        
        // Calculate distance to each subsequent node
        for (int i = 1; i < route.size(); i++) {
            String nextNodeName = route.get(i);
            GraphModel.Node nextNode = findNodeByName(nextNodeName);
            
            if (nextNode == null) {
                continue;
            }
            
            // Find the edge between current and next node
            GraphModel.Edge edge = findEdge(currentNode.id(), nextNode.id());
            
            // Add this segment's distance
            double segmentDistance = 0.0;
            
            if (edge != null) {
                segmentDistance = edge.weight + 1; // Add 1 for the edge itself
            } else {
                // Calculate direct distance if no edge found
                segmentDistance = Math.sqrt(
                    Math.pow(nextNode.x() - currentNode.x(), 2) + 
                    Math.pow(nextNode.y() - currentNode.y(), 2)
                ) / CELL_SIZE;
            }
            
            totalDistance += segmentDistance;
            distances.put(nextNodeName, totalDistance);
            
            // Move to next node
            currentNode = nextNode;
        }
        
        return distances;
    }
    
    /**
     * Create a Path object that follows the route
     */
    private Path createPathFromRoute(List<String> route) {
        Path path = new Path();
        
        if (route.isEmpty()) {
            return path;
        }
        
        String currentNodeName = route.get(0);
        GraphModel.Node currentNode = findNodeByName(currentNodeName);
        if (currentNode == null) return path;
        
        // Start at first node
        path.getElements().add(new MoveTo(currentNode.x(), currentNode.y()));
        
        // Follow each segment in the route
        for (int i = 1; i < route.size(); i++) {
            String nextNodeName = route.get(i);
            GraphModel.Node nextNode = findNodeByName(nextNodeName);
            
            if (nextNode == null) continue;
            
            // Find the edge between current and next node
            GraphModel.Edge edge = findEdge(currentNode.id(), nextNode.id());
            
            if (edge != null && edge.cells != null && !edge.cells.isEmpty()) {
                // Check if we need to reverse the cell points based on direction
                List<Point> pathCells = new ArrayList<>(edge.cells);
                boolean reversed = edge.from.equals(nextNode.id());
                
                if (reversed) {
                    // Reverse the cell points if we're going in the opposite direction
                    List<Point> reversedCells = new ArrayList<>();
                    for (int j = pathCells.size() - 1; j >= 0; j--) {
                        reversedCells.add(pathCells.get(j));
                    }
                    pathCells = reversedCells;
                }
                
                // Follow the path through the edge's cell points
                for (Point cellPoint : pathCells) {
                    double cellX = cellPoint.x * CELL_SIZE + CELL_SIZE / 2.0;
                    double cellY = cellPoint.y * CELL_SIZE + CELL_SIZE / 2.0;
                    path.getElements().add(new LineTo(cellX, cellY));
                }
                
                // Add final segment to destination node
                path.getElements().add(new LineTo(nextNode.x(), nextNode.y()));
            } else {
                // Direct edge (no path cells) - just draw straight line
                path.getElements().add(new LineTo(nextNode.x(), nextNode.y()));
            }
            
            // Move to next node
            currentNode = nextNode;
        }
        
        return path;
    }
    
    /**
     * Animate the robot along a route following the exact grid paths.
     * This version adjusts timing for accurate delivery point recognition.
     */
    private void animateRobotAlongRoute(List<String> route, List<Order> orders) {
        // Cancel any existing animation
        if (isAnimationRunning) {
            if (robotAnimation != null) {
                robotAnimation.stop();
            }
            isAnimationRunning = false;
        }
        
        if (route.isEmpty()) {
            return;
        }
        
        // Reset delivered tables for new round
        deliveredTables.clear();
        
        statusLabel.setText("Robot in motion: " + String.join(" -> ", route));
        
        // Find all tables in the route (not kitchen or junction)
        List<String> tableNodesInRoute = new ArrayList<>();
        for (String nodeName : route) {
            if (!nodeName.startsWith("K") && !nodeName.startsWith("J")) {
                tableNodesInRoute.add(nodeName);
                System.out.println("[TABLE] Table " + nodeName + " is in route");
            }
        }
        
        // Calculate exact node distances
        Map<String, Double> nodeDistances = calculateNodeDistances(route);
        
        // Create the path
        Path path = createPathFromRoute(route);
        
        // Get total distance (from last node)
        double totalDistance = nodeDistances.getOrDefault(route.get(route.size() - 1), 0.0);
        if (totalDistance == 0.0) {
            // Fallback if distances couldn't be calculated
            totalDistance = route.size();
        }
        
        // Create the animation
        PathTransition transition = new PathTransition();
        transition.setDuration(Duration.seconds(0.5 * totalDistance)); // 1 second per unit distance
        transition.setPath(path);
        transition.setNode(robotDot);
        transition.setCycleCount(1);
        
        // Set LINEAR interpolator for constant speed motion
        transition.setInterpolator(javafx.animation.Interpolator.LINEAR);
        
        // Keep track of animation
        robotAnimation = transition;
        
        // Schedule deliveries using timed animations - IMPORTANT: Adjust timing to be at table exactly
        for (String tableName : tableNodesInRoute) {
            // Find exact node index - important to get right route position
            int nodeIndex = getNodeIndexInRoute(tableName, route);
            if (nodeIndex == -1) continue;
            
            // Use calculated distance from node distances map
            double exactDistance = nodeDistances.getOrDefault(tableName, 0.0);
            
            // Create a pause transition that waits until it's time to deliver
            // Slightly adjust timing (subtract a tiny bit) to ensure we're exactly at the node
            double deliveryTime = 0.5 * exactDistance;
            
            System.out.println("[TIMING] Table " + tableName + " at index " + nodeIndex + 
                              ", distance: " + exactDistance + 
                              ", delivery time: " + deliveryTime + " seconds");
            
            PauseTransition deliveryTimer = new PauseTransition(Duration.seconds(deliveryTime));
            deliveryTimer.setOnFinished(e -> {
                System.out.println("[TIMED] Time to deliver to: " + tableName);
                
                // Manually trigger delivery
                handleTableDelivery(tableName);
            });
            deliveryTimer.play();
        }
        
        // Start the animation
        isAnimationRunning = true;
        transition.play();
        
        // When animation completes...
        transition.setOnFinished(e -> {
            // First notify we're done
            isAnimationRunning = false;
            statusLabel.setText("Robot returned to kitchen");
            
            // Clear all food markers when the robot returns to kitchen
            clearFoodMarkers();
            
            // Clear the cargo table
            cargoTable.getItems().clear();
            cargoTable.refresh();
            System.out.println("[DEBUG] Cleared cargo table after animation");
            
            // Important: Create a slight delay before notifying simulation engine
            // This ensures the UI is fully updated before the next round starts
            PauseTransition completionDelay = new PauseTransition(Duration.millis(500));
            completionDelay.setOnFinished(event -> {
                // Notify simulation engine that movement is complete
                sim.notifyRobotMovementComplete();
            });
            completionDelay.play();
        });
    }

    private void startTimer() {
        // Reset timer state
        elapsedSeconds = 0;
        if (timerTimeline != null) {
            timerTimeline.stop();
        }
        
        // Record start time
        startTimeMillis = System.currentTimeMillis();
        
        // Update timer label
        updateTimerLabel();
        
        // Create and start the timer
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
        // Extract just the time part (MM:SS) from the timer label
        String labelText = timerLabel.getText();
        if (labelText.startsWith("Timer: ")) {
            return labelText.substring(7); // Remove "Timer: "
        } else if (labelText.startsWith("Total time: ")) {
            return labelText.substring(12); // Remove "Total time: "
        }
        return labelText;
    }
}
