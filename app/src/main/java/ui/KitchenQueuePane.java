package ui;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.ChefQueue;
import model.Dish;
import model.Order;
import sim.SimulationEngine;

public class KitchenQueuePane extends VBox {
    private final SimulationEngine sim;
    private final TableView<Row> table = new TableView<>();
    private final Label robotStatus = new Label();
    private final ListView<String> orderLog = new ListView<>();

    /**
     * DTO for table rows.
     */
    public record Row(String table, String dish, String status) {}

    public KitchenQueuePane(SimulationEngine sim) {
        this.sim = sim;
        setSpacing(8);

        // Register listener for new orders
        sim.addOrderListener((tableId, dish) ->
            Platform.runLater(() ->
                orderLog.getItems().add("Table " + tableId + " ordered " + dish.name())
            )
        );

        // Define table columns
        TableColumn<Row,String> c1 = new TableColumn<>("Table");
        c1.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().table()));
        TableColumn<Row,String> c2 = new TableColumn<>("Dish");
        c2.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().dish()));
        TableColumn<Row,String> c3 = new TableColumn<>("Remaining(s)");
        c3.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().status()));
        table.getColumns().addAll(c1, c2, c3);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Allow table and log to grow vertically
        VBox.setVgrow(orderLog, Priority.ALWAYS);
        VBox.setVgrow(table, Priority.ALWAYS);

        // Layout
        getChildren().addAll(
            new Label("Order Events"),
            orderLog,
            new Separator(),
            new Label("Kitchen Queues"),
            table,
            robotStatus
        );

        // Refresh loop
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh()));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        List<Row> rows = new ArrayList<>();

        // 1. Chef queues
        for (Dish d : Dish.values()) {
            ChefQueue cq = sim.chefQueues()[d.ordinal()];
            int idx = 0;
            long remain = cq.getFinishTimeMs() - now;
            for (Order o : cq.getQueueReadonly()) {
                String status = (idx == 0)
                    ? String.valueOf(Math.max(remain/1000, 0))
                    : "waiting";
                rows.add(new Row(
                    String.valueOf(o.tableNumber()),
                    d.name(),
                    status
                ));
                idx++;
            }
        }
        // 2. Robot queue
        for (Order o : sim.robotQueue().getQueue()) {
            rows.add(new Row(
                String.valueOf(o.tableNumber()),
                o.dish().name(),
                "ready"
            ));
        }
        robotStatus.setText(
            sim.isRobotBusy() ? "Robot: BUSY" : "Robot: IDLE"
        );
        table.setItems(FXCollections.observableArrayList(rows));
    }
}
