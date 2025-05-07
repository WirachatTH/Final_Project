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
 * แสดงสถานะการประมวลผลคำสั่งอาหารในครัวของการจำลอง
 * โดยแสดงคิวแยกตามประเภทจานในแท็บต่างๆ และเก็บบันทึกเหตุการณ์การสั่งอาหาร
 */
public class KitchenQueuePane extends VBox implements SimulationEngine.ResetListener {
    private final SimulationEngine sim;
    private final GraphModel graphModel;
    private final ListView<String> orderLog = new ListView<>();
    private final TabPane dishTabs = new TabPane();
    private final Label robotStatus = new Label();

    /**
     * คลาสข้อมูลสำหรับแต่ละแถวในตารางคิวของแต่ละจาน
     * เก็บชื่อโต๊ะและสถานะปัจจุบันของคำสั่ง
     */
    public record DishRow(String table, String status) {}

    /**
     * สร้าง KitchenQueuePane ใหม่ที่เชื่อมต่อกับ SimulationEngine
     */
    public KitchenQueuePane(SimulationEngine sim) {
        this.sim = sim;

        if (sim != null) {
            sim.addResetListener(this);
        }

        this.graphModel = sim.getGraphModel();
        setSpacing(8);

        // เมื่อมีคำสั่งใหม่ ให้เพิ่มบันทึกเหตุการณ์ใน orderLog
        sim.addOrderListener((tableId, dish) ->
            Platform.runLater(() ->
                orderLog.getItems().add("Table " + getNodeName(tableId) + " ordered " + dish.name())
            )
        );

        // สร้างแท็บแยกตามประเภทจานในเมนู
        for (Dish d : Dish.values()) {
            TableView<DishRow> tv = new TableView<>();
            tv.getStyleClass().add("kitchen-queue-table");

            // คอลัมน์แสดงชื่อโต๊ะ
            TableColumn<DishRow, String> colTable = new TableColumn<>("Table");
            colTable.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().table()));

            // คอลัมน์แสดงสถานะ (กำลังปรุง, พร้อมส่ง ฯลฯ)
            TableColumn<DishRow, String> colStatus = new TableColumn<>("Status");
            colStatus.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().status()));

            tv.getColumns().addAll(colTable, colStatus);
            tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            VBox.setVgrow(tv, Priority.ALWAYS);

            // สร้างแท็บสำหรับจานแล้วเพิ่มลงใน TabPane
            Tab tab = new Tab(d.name(), tv);
            tab.setClosable(false);
            tv.getStyleClass().add("kitchen-queue-table");
            dishTabs.getTabs().add(tab);
            dishTabs.getStyleClass().add("stroked-tabs");
        }

        // กำหนดให้ orderLog และ dishTabs ขยายตามพื้นที่แนวตั้ง
        VBox.setVgrow(orderLog, Priority.ALWAYS);
        VBox.setVgrow(dishTabs, Priority.ALWAYS);
        
        // สร้างหัวข้อส่วน Order Events และ Kitchen Queues พร้อมจัดสไตล์
        Label orderEvents = new Label("Order Events");
        Label kitchenQueues = new Label("Kitchen Queues");
        orderEvents.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        kitchenQueues.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        orderLog.getStyleClass().add("table-view");

        // รวมคอมโพเนนต์ทั้งหมดตามลำดับในแนวตั้ง
        getChildren().addAll(
            orderEvents, orderLog,
            new Separator(), kitchenQueues, dishTabs,
            robotStatus
        );

        // ตั้ง Timeline รีเฟรช UI ทุก 1 วินาที
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh()));
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();
    }

    /**
     * รีเซ็ต UI เมื่อ Simulation รีเซ็ต
     */
    @Override
    public void onReset() {
        Platform.runLater(() -> {
            // เคลียร์ orderlog
            orderLog.getItems().clear();
            
            // เคลียร์ตารางคิวในแต่ละแท็บ
            for (Tab tab : dishTabs.getTabs()) {
                @SuppressWarnings("unchecked")
                TableView<DishRow> tv = (TableView<DishRow>) tab.getContent();
                tv.getItems().clear();
            }
        });
    }


    /**
     * อัปเดตการแสดงผลตามสถานะการจำลองปัจจุบัน
     * เรียกทุก 1 วินาทีจาก Timer
     */
    private void refresh() {
        long now = System.currentTimeMillis();
    
        // อัปเดตแต่ละแท็บตามคิวของเชฟและหุ่นยนต์
        for (int i = 0; i < Dish.values().length; i++) {
            Dish d = Dish.values()[i];
            @SuppressWarnings("unchecked")
            TableView<DishRow> tv = (TableView<DishRow>) dishTabs.getTabs().get(i).getContent();
            List<DishRow> rows = new ArrayList<>();
    
            // คิวของเชฟ พร้อมตัวนับเวลาถอยหลัง
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
    
            // คิวของหุ่นยนต์ที่พร้อมส่ง
            for (Order o : sim.robotQueue().getQueue()) {
                if (o.dish() == d) {
                    rows.add(new DishRow(getNodeName(o.tableNumber()), "ready"));
                }
            }
    
            tv.setItems(FXCollections.observableArrayList(rows));
        }

        // อัปเดตสถานะของหุ่นยนต์
        robotStatus.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF");
        robotStatus.setText(sim.isRobotBusy() ? "Robot: BUSY" : "Robot: IDLE");
    }
    
    /**
     * แปลงรหัสโต๊ะ (ตัวเลข) ให้เป็นชื่อโหนดที่ใช้งาน เช่น "T2-1"
     * @param tableId รหัสโต๊ะแบบตัวเลข
     * @return ชื่อโหนดที่ใช้งานได้
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
     * เคลียร์ orderlog
     */
    public void clearOrderLog() {
        Platform.runLater(() -> {
            orderLog.getItems().clear();
        });
    }
}