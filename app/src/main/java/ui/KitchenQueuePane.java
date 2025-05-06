package ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.GraphModel;
import model.GraphModel.NodeKind;
import model.Order;
import model.Dish;
import model.ChefQueue;
import sim.SimulationEngine;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.*;

/**
 * Displays separate queues per Dish type in tabs, and logs new orders.
 */
public class KitchenQueuePane extends VBox implements SimulationEngine.ResetListener {
    private final SimulationEngine sim;
    private final GraphModel graphModel;
    private final ListView<String> orderLog = new ListView<>();
    private final TabPane dishTabs = new TabPane();
    private final Label robotStatus = new Label();

    /** Represents a row in a dish-specific table. */
    public record DishRow(String table, String status) {}

    public KitchenQueuePane(SimulationEngine sim) {
        this.sim = sim;

        if (sim != null) {
            sim.addResetListener(this);
        }

        this.graphModel = sim.getGraphModel();
        setSpacing(8);

        // Listen for new orders
        sim.addOrderListener((tableId, dish) ->
            Platform.runLater(() ->
                orderLog.getItems().add("Table " + getNodeName(tableId) + " ordered " + dish.name())
            )
        );

        // Create one tab per Dish type
        for (Dish d : Dish.values()) {
            TableView<DishRow> tv = new TableView<>();
            tv.getStyleClass().add("kitchen-queue-table");
            TableColumn<DishRow, String> colTable = new TableColumn<>("Table");
            colTable.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().table()));
            TableColumn<DishRow, String> colStatus = new TableColumn<>("Status");
            colStatus.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().status()));
            tv.getColumns().addAll(colTable, colStatus);
            tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            VBox.setVgrow(tv, Priority.ALWAYS);

            Tab tab = new Tab(d.name(), tv);
            tab.setClosable(false);
            tv.getStyleClass().add("kitchen-queue-table");
            dishTabs.getTabs().add(tab);
            dishTabs.getStyleClass().add("stroked-tabs");
        }

        // Expand orderLog and dishTabs vertically
        VBox.setVgrow(orderLog, Priority.ALWAYS);
        VBox.setVgrow(dishTabs, Priority.ALWAYS);
        
        Label orderEvents = new Label("Order Events");
        Label kitchenQueues = new Label("Kitchen Queues");
        orderEvents.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        kitchenQueues.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        orderLog.getStyleClass().add("table-view");
        getChildren().addAll(
            orderEvents, orderLog,
            new Separator(), kitchenQueues, dishTabs,
            robotStatus
        );

        // Refresh every second
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh()));
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();
    }

    @Override
    public void onReset() {
        Platform.runLater(() -> {
            // Clear the order log
            orderLog.getItems().clear();
            
            // Reset all dish tables
            for (Tab tab : dishTabs.getTabs()) {
                @SuppressWarnings("unchecked")
                TableView<DishRow> tv = (TableView<DishRow>) tab.getContent();
                tv.getItems().clear();
            }
        });
    }

    private void refresh() {
        long now = System.currentTimeMillis();
    
        // Update each dish tab
        for (int i = 0; i < Dish.values().length; i++) {
            Dish d = Dish.values()[i];
            @SuppressWarnings("unchecked")
            TableView<DishRow> tv = (TableView<DishRow>) dishTabs.getTabs().get(i).getContent();
            List<DishRow> rows = new ArrayList<>();
    
            // Chef queue entries with countdown
            ChefQueue cq = sim.chefQueues()[i];
            long remainMs = cq.getFinishTimeMs() - now;
            long remainSec = remainMs > 0 ? (remainMs + 999) / 1000 : 0;  // round up, clamp at 0
            int idx = 0;
            for (Order o : cq.getQueueReadonly()) {
                String status = (idx == 0)
                    ? String.valueOf(remainSec)
                    : "waiting";
                rows.add(new DishRow(getNodeName(o.tableNumber()), status));
                idx++;
            }
    
            // Robot queue entries for this dish
            for (Order o : sim.robotQueue().getQueue()) {
                if (o.dish() == d) {
                    rows.add(new DishRow(getNodeName(o.tableNumber()), "ready"));
                }
            }
    
            tv.setItems(FXCollections.observableArrayList(rows));
        }

        robotStatus.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        robotStatus.setText(sim.isRobotBusy() ? "Robot: BUSY" : "Robot: IDLE");
    }
    
    /**
     * Map numeric tableId to its GraphModel node name (e.g., "T2-1").
     */
    private String getNodeName(int tableId) {
        return graphModel.nodes().stream()
            .filter(n -> graphModel.getNodeInfo(n.id())
                .filter(info -> info.kind == NodeKind.TABLE && info.number == tableId)
                .isPresent()
            )
            .findFirst()
            .map(GraphModel.Node::name)
            .orElse(String.valueOf(tableId));
    }

    /**
     * Clears the order events log.
     */
    public void clearOrderLog() {
        Platform.runLater(() -> {
            orderLog.getItems().clear();
        });
    }
}