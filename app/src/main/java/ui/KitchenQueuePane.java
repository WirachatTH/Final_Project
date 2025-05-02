package ui;

import javafx.animation.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.*;
import sim.SimulationEngine;

import java.util.ArrayList;
import java.util.List;

public class KitchenQueuePane extends VBox {

    private final SimulationEngine sim;
    private final TableView<Row> table = new TableView<>();
    private final Label robotStatus = new Label();

    /* ------- Row DTO ------- */
    public record Row(String table, String dish, String status){}

    public KitchenQueuePane(SimulationEngine sim){
        this.sim = sim;
        setSpacing(8);

        /* ---------- สร้างตาราง (เหมือนเดิม) ---------- */
        TableColumn<Row,String> c1 = new TableColumn<>("Table");
        c1.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().table()));
        TableColumn<Row,String> c2 = new TableColumn<>("Dish");
        c2.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().dish()));
        TableColumn<Row,String> c3 = new TableColumn<>("Remaining(s)");
        c3.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().status()));
        table.getColumns().addAll(c1,c2,c3);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        /* ---------- ให้ table ขยายเต็มพื้นที่ที่เหลือ ---------- */
        VBox.setVgrow(table, Priority.ALWAYS);          // ★ สำคัญ

        /* ---------- จัดวาง ---------- */
        getChildren().addAll(
            new Label("Kitchen Queues"),
            table,
            robotStatus                                   // Robot label เลยอยู่ชิดล่าง
        );

        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh()));
        tl.setCycleCount(Animation.INDEFINITE); tl.play();
    }


    private void refresh(){
        long now=System.currentTimeMillis();
        List<Row> rows=new ArrayList<>();

        /* 1. เชฟคิว */
        var chefs=sim.chefQueues();
        for(Dish d: Dish.values()){
            ChefQueue cq=chefs[d.ordinal()];
            int idx=0;
            long remain=cq.getFinishTimeMs()-now;
            for(Order o: cq.getQueueReadonly()){
                String status = idx==0? String.valueOf(Math.max(remain/1000,0)) : "waiting";
                rows.add(new Row(String.valueOf(o.tableNumber()), d.name, status));
                idx++;
            }
        }
        /* 2. คิวรอหุ่นยนต์ */
        for(Order o: sim.robotQueue().getQueue()){
            rows.add(new Row(String.valueOf(o.tableNumber()), o.dish().name,"ready"));
        }
        robotStatus.setText(sim.isRobotBusy()? "Robot: BUSY":"Robot: IDLE");
        table.setItems(FXCollections.observableArrayList(rows));
    }
}
