package ui;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import app.Main;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import model.GraphModel;
import model.Graph;
import model.GraphModel.Edge;
import model.GraphModel.NodeInfo;
import model.GraphModel.NodeKind;
import model.TableType;
import sim.SimulationEngine;

/**
 * GridEditor with explicit 2D grid-state:
 *  • EMPTY: click to place table or extend path
 *  • TABLE: click to start/finish path on tables/junctions
 *  • PATH:  click to split & continue or cancel
 */

public class GridEditor extends HBox {
    private static final int CELLS = 8, CELL_SIZE = 60;
    private enum CellState { EMPTY, TABLE, PATH }
    private final CellState[][] gridState = new CellState[CELLS][CELLS];

    private final GraphModel graph;
    private TableType currentTable = null;
    private boolean pathMode = false;

    private GraphModel.Node pathStart = null;
    private final List<Point2D> tempCells = new ArrayList<>();

    private final Map<GraphModel.Node, Circle> nodeShapes = new HashMap<>();
    private final Map<Line, EdgeRecord> edgeShapes = new HashMap<>();
    private final Map<Point, EdgeRecord> cellEdgeMap = new HashMap<>();

    private Line previewLine = null;
    private final List<Circle> previewDots = new ArrayList<>();
    private Label statusTarget = null;
    private int nextTableNumber    = 1;
    private int nextJunctionNumber = 1;

    private final Pane gridPane;
    private final TableView<EdgeRow> edgeTable;

    // Tracks which src↔dst a JavaFX Line represents
    private static class EdgeRecord {
        final String src, dst;
        EdgeRecord(String s, String d) {
            this.src = s;
            this.dst = d;
        }
    }

    // Row in the edge table
    public record EdgeRow(String connection, double weight) {}

    private final SimulationEngine sim;

    public GridEditor(GraphModel model, SimulationEngine sim) {
        this.graph = model;
        this.sim = sim;
        // init grid state
        for (int r = 0; r < CELLS; r++)
            for (int c = 0; c < CELLS; c++)
                gridState[r][c] = CellState.EMPTY;

        setSpacing(10);

        // grid pane
        gridPane = new Pane();
        gridPane.setPrefSize(CELLS * CELL_SIZE, CELLS * CELL_SIZE);
        drawGridLines();
        gridPane.setOnMouseClicked(this::onClick);
        gridPane.getStyleClass().add("grid-cell");

        // edge table
        edgeTable = new TableView<>();
        edgeTable.setPrefWidth(200);
        TableColumn<EdgeRow,String> colC = new TableColumn<>("Connection");
        colC.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().connection()));
        TableColumn<EdgeRow,String> colW = new TableColumn<>("Weight");
        colW.setCellValueFactory(r -> new SimpleStringProperty(String.valueOf((int)r.getValue().weight())));
        edgeTable.getColumns().addAll(colC, colW);
        edgeTable.setItems(FXCollections.observableArrayList());

        // reset button
        Button resetBtn = new Button("Reset");
        resetBtn.setOnAction(e -> resetAll());

        // layout: left side with reset + grid, right side edge table
        VBox leftBox = new VBox(5, resetBtn, gridPane);
        leftBox.setAlignment(Pos.TOP_CENTER);

        getChildren().addAll(leftBox, edgeTable);
    }

    /** Completely clears all nodes, edges, and UI elements */
    private void resetAll() {
        // clear model data
        graph.nodes().clear();
        graph.edges().clear();
        graph.tableIds().clear();
        graph.junctionIds().clear();
        graph.setKitchenId(null); 

        if (sim != null) {
            sim.resetState();
        }

        // reset counters
        nextTableNumber = 1;
        nextJunctionNumber = 1;

        // clear UI state
        nodeShapes.clear();
        edgeShapes.clear();
        cellEdgeMap.clear();
        clearPathState();

        // reset gridState to EMPTY
        for (int r = 0; r < CELLS; r++) {
            for (int c = 0; c < CELLS; c++) {
                gridState[r][c] = CellState.EMPTY;
            }
        }

        Main mainApp = Main.getInstance();
        if (mainApp != null) {
            mainApp.resetSimulationHistory();
        }

        // rebuild grid and table view
        drawGridLines();
        gridPane.getChildren().clear();
        drawGridLines();
        drawGridLines();
        for (javafx.scene.Node h : gridPane.getChildren()) { /* grid lines remain */ }
        edgeTable.getItems().clear();
        showStatus("Reset complete");
    }

    public void setCurrentTable(TableType t) {
        this.currentTable = t;
    }

    public void setPathMode(boolean flag) {
        this.pathMode = flag;
        clearPathState();
    }

    public void setStatusTarget(Label lbl) {
        this.statusTarget = lbl;
    }

    private void showStatus(String msg) {
        if (statusTarget != null) {
            statusTarget.setText(msg);
        }
    }

    private void drawGridLines() {
        for (int i = 0; i <= CELLS; i++) {
            Line h = new Line(0, i*CELL_SIZE, CELLS*CELL_SIZE, i*CELL_SIZE);
            Line v = new Line(i*CELL_SIZE, 0, i*CELL_SIZE, CELLS*CELL_SIZE);
            gridPane.getChildren().addAll(h, v);
        }
    }

    private void onClick(MouseEvent e) {
        int c = (int)(e.getX() / CELL_SIZE), r = (int)(e.getY() / CELL_SIZE);
        if (c<0||c>=CELLS||r<0||r>=CELLS) return;
        CellState st = gridState[r][c];

        if (!pathMode) {
            if (st == CellState.EMPTY) placeTable(c, r);
        } else {
            switch (st) {
                case EMPTY -> {
                    if (pathStart!=null) {
                        extendPath(c,r);
                    }
                }
                case TABLE -> clickTable(c,r);
                case PATH  -> {
                    if (pathStart==null) {
                        splitAndContinue(c,r);
                }
                    else {
                        showStatus("ERROR: no double-split — cancelled");
                        clearPathState();
                    }
                }
            }
        }
    }

    public void cancelDraw() {
        if (pathStart!=null) {
            clearPathState();
            showStatus("Drawing cancelled");
        }

        else {
            showStatus("ERROR: Not currently drawing");
        }
    }

    private void placeTable(int c, int r) {
        if (currentTable == TableType.K && graph.kitchenId().isPresent()) {
            showStatus("ERROR: Only one Kitchen (K) allowed!");
            return;
        }
        if (currentTable == null) {
            showStatus("ERROR: Have not selected table type yet.");
            return;
        }
        double x = c*CELL_SIZE + CELL_SIZE/2.0, y = r*CELL_SIZE + CELL_SIZE/2.0;
        GraphModel.Node n = graph.addNode(x, y, currentTable);
        Circle circ = new Circle(x, y, CELL_SIZE*0.35, Color.web(currentTable.colorHex));
        circ.setStroke(Color.BLACK);
        nodeShapes.put(n, circ);
        gridPane.getChildren().add(circ);

        Text lbl = new Text(n.name());
        lbl.setMouseTransparent(true);
        lbl.setTextOrigin(javafx.geometry.VPos.CENTER);
        // center immediately
        javafx.application.Platform.runLater(() -> {
            lbl.setX(x - lbl.getLayoutBounds().getWidth()/2);
            lbl.setY(y);
        });
        gridPane.getChildren().add(lbl);

        gridState[r][c] = CellState.TABLE;
        showStatus("Placed " + n.name());
    }

    private void clickTable(int c, int r) {
        GraphModel.Node node = findNode(c, r);
        if (node==null) return;

        if (pathStart==null) {
            // begin drawing
            pathStart = node;
            tempCells.clear();
            clearPreview();
            previewLine = new Line(node.x(), node.y(), node.x(), node.y());
            previewLine.getStrokeDashArray().addAll(4.0,4.0);
            previewLine.setStroke(Color.GRAY);
            previewLine.setMouseTransparent(true);
            previewLine.getStyleClass().add("path-line");
            style(previewLine);
            gridPane.getChildren().add(previewLine);

            NodeInfo info = graph.getNodeInfo(node.id()).orElseThrow();
            String startLabel = (info.kind==NodeKind.JUNCTION?"J":node.type()+"-") + info.number;
            showStatus("Path start @ " + startLabel);
        } else {
            // finish drawing
            if (graph.findEdge(pathStart.id(),node.id()).isPresent()) {
                showStatus("ERROR: attempted loop — cancelled");
                clearPathState();
                return;
            }
            Point2D last = tempCells.isEmpty()
                ? new Point2D(pathStart.x(),pathStart.y())
                : tempCells.get(tempCells.size()-1);
            int pc=(int)(last.getX()/CELL_SIZE), pr=(int)(last.getY()/CELL_SIZE);
            if (Math.abs(pc-c)+Math.abs(pr-r)!=1) {
                showStatus("ERROR: must land adjacent");
                return;
            }

            clearPreview(); double px=pathStart.x(), py=pathStart.y();
            for (Point2D pd : tempCells) {
                Line seg = new Line(px,py,pd.getX(),pd.getY());
                seg.getStyleClass().add("path-line");
                style(seg);
                gridPane.getChildren().add(seg);
                edgeShapes.put(seg, new EdgeRecord(pathStart.id(), node.id()));
                int cc=(int)(pd.getX()/CELL_SIZE), rr=(int)(pd.getY()/CELL_SIZE);
                gridState[rr][cc]=CellState.PATH;
                cellEdgeMap.put(new Point(cc,rr), new EdgeRecord(pathStart.id(),node.id()));
                px=pd.getX(); py=pd.getY();
            }
            Line lastSeg = new Line(px,py,node.x(),node.y());
            lastSeg.getStyleClass().add("path-line");
            style(lastSeg);
            style(lastSeg);
            gridPane.getChildren().add(lastSeg);
            edgeShapes.put(lastSeg,new EdgeRecord(pathStart.id(),node.id()));

            List<Point> cellPath = tempCells.stream()
                .map(p2->new Point((int)(p2.getX()/CELL_SIZE),(int)(p2.getY()/CELL_SIZE)))
                .collect(Collectors.toList());
            graph.addEdge(pathStart.id(), node.id(), cellPath);

            NodeInfo info2 = graph.getNodeInfo(node.id()).orElseThrow();
            String endLabel = (info2.kind==NodeKind.JUNCTION?"J":node.type()+"-") + info2.number;
            refreshEdges(); showStatus("Connected to " + endLabel);
            clearPathState();
        }
    }

    private void extendPath(int c, int r) {
        int pc, pr;
        if (tempCells.isEmpty()) {
            pc=(int)(pathStart.x()/CELL_SIZE);
            pr=(int)(pathStart.y()/CELL_SIZE);
        }
        else {
            Point2D last=tempCells.get(tempCells.size()-1);
            pc=(int)((last.getX()-CELL_SIZE/2)/CELL_SIZE);
            pr=(int)((last.getY()-CELL_SIZE/2)/CELL_SIZE);
        }

        if (Math.abs(c-pc)+Math.abs(r-pr)!=1) {
            showStatus("ERROR: one-cell only");
            return;
        }
        Point2D p=new Point2D(c*CELL_SIZE+CELL_SIZE/2,r*CELL_SIZE+CELL_SIZE/2);
        if (tempCells.contains(p)) return;
        clearPreview();
        Circle dot=new Circle(p.getX(),p.getY(),CELL_SIZE*0.1,Color.web(currentTable.colorHex));
        dot.setMouseTransparent(true);
        previewDots.add(dot);
        gridPane.getChildren().add(dot);
        tempCells.add(p);
        previewLine=new Line(p.getX(),p.getY(),p.getX(),p.getY());
        previewLine.getStrokeDashArray().addAll(4.0,4.0);
        previewLine.setStroke(Color.GRAY);
        previewLine.setMouseTransparent(true);
        previewLine.getStyleClass().add("path-line");
        style(previewLine);
        gridPane.getChildren().add(previewLine);
        showStatus("Segments: " + tempCells.size());
    }

    private void splitAndContinue(int c, int r) {
        EdgeRecord rec=cellEdgeMap.get(new Point(c,r));
        if(rec==null) return;
        Optional<GraphModel.Edge> oe=graph.findEdge(rec.src,rec.dst);
        if(oe.isEmpty()) return;
        GraphModel.Edge oldEdge=oe.get();
        List<Point> cells=oldEdge.cells;
        int idx=findIndex(cells,c,r);
        if(idx<0) return;
        graph.removeEdge(rec.src,rec.dst);
        edgeShapes.entrySet().removeIf(e->e.getValue().src.equals(rec.src)&&e.getValue().dst.equals(rec.dst));
        cellEdgeMap.entrySet().removeIf(e->e.getValue().src.equals(rec.src)&&e.getValue().dst.equals(rec.dst));
        List<Point> firstPath=new ArrayList<>(cells.subList(0,idx));
        List<Point> secondPath=new ArrayList<>(cells.subList(idx+1,cells.size()));
        double jx=c*CELL_SIZE+CELL_SIZE/2,jy=r*CELL_SIZE+CELL_SIZE/2;
        GraphModel.Node existing=findNode(c,r);
        GraphModel.Node mid=existing!=null?existing:graph.addNode(jx,jy,currentTable);
        if(existing==null){
            graph.junctionIds().put(mid.id(),nextJunctionNumber);
            Circle jc = new Circle(jx,jy,CELL_SIZE*0.25,Color.web(currentTable.colorHex));
            jc.setStroke(Color.BLACK);
            nodeShapes.put(mid,jc);

            StackPane nodeGroup = new StackPane();
            nodeGroup.setTranslateX(jx - (CELL_SIZE*0.25));
            nodeGroup.setTranslateY(jy - (CELL_SIZE*0.25));

            Text lbl=new Text("J"+nextJunctionNumber);
            lbl.setMouseTransparent(true);
            lbl.getStyleClass().add("junction-text");

            nodeGroup.getChildren().addAll(jc, lbl);
            nodeShapes.put(mid, jc); 
            gridPane.getChildren().add(nodeGroup);

            gridState[r][c]=CellState.TABLE;
            nextJunctionNumber++;
        }
        graph.addEdge(rec.src,mid.id(),new ArrayList<>(firstPath));
        graph.addEdge(mid.id(),rec.dst,new ArrayList<>(secondPath));
        for(Point p:firstPath){
            gridState[p.y][p.x]=CellState.PATH;
            cellEdgeMap.put(new Point(p.x,p.y),new EdgeRecord(rec.src,mid.id()));
        }
        for(Point p:secondPath){
            gridState[p.y][p.x]=CellState.PATH;
            cellEdgeMap.put(new Point(p.x,p.y),new EdgeRecord(mid.id(),rec.dst));
        }
        refreshEdges();
    }

    private int findIndex(List<Point> list,int c,int r){
        for(int i=0;i<list.size();i++){
            Point p=list.get(i);
            if(p.x==c&&p.y==r)return i;
        }
        return -1;
    }

    private void refreshEdges() {
        var rows = graph.edges().stream().map(e -> {
            String sa = graph.nodes().stream().filter(n->n.id().equals(e.from)).findFirst().get().name();
            String sb = graph.nodes().stream().filter(n->n.id().equals(e.to)).findFirst().get().name();
            return new EdgeRow(sa+" <-> "+sb, e.weight+1);
        }).toList();
        edgeTable.setItems(FXCollections.observableArrayList(rows));
    }

    private GraphModel.Node findNode(int c, int r) {
        return nodeShapes.keySet().stream()
            .filter(n->(int)(n.x()/CELL_SIZE)==c && (int)(n.y()/CELL_SIZE)==r)
            .findFirst().orElse(null);
    }

    private void clearPreview() {
        if (previewLine!=null) gridPane.getChildren().remove(previewLine);
        previewLine = null;
        previewDots.forEach(d->gridPane.getChildren().remove(d));
        previewDots.clear();
    }

    private void clearPathState() {
        clearPreview();
        pathStart = null;
        tempCells.clear();
    }

    private void style(Line ln) {
        ln.setStrokeWidth(3);
        ln.setStroke(Color.RED);
    }

    private boolean traversePath(Graph simGraph) {
        boolean isCompleted = true;

        // 0) ตรวจสอบว่ามี Kitchen (“K”) ในกราฟหรือไม่
        boolean hasKitchen = graph.nodes().stream()
        .anyMatch(n -> n.name().equals("K"));
        if (!hasKitchen) {
            System.out.println("ERROR: Kitchen node 'K' not found in the graph!");
            showStatus("ERROR: Kitchen node not found!");
            isCompleted = false;
            return isCompleted;
        }
    
        // 1) Check each table, not each edge
        for (String tableId : graph.tableIds().keySet()) {
            // look up the Node so you can get its display name
            GraphModel.Node tableNode = graph.nodes().stream()
                .filter(n -> n.id().equals(tableId))
                .findFirst()
                .orElseThrow();
        
            String displayName = tableNode.name();  // “T4-1”, “T2-2”, etc.
        
            List<String> path = simGraph.dijkstra("K", displayName);
            boolean reachable = path.size() >= 2
                             && path.get(0).equals("K")
                             && path.get(path.size()-1).equals(displayName);
            if (!reachable) {
                System.out.println("Table " + displayName + " is not reachable from the kitchen!");
                isCompleted = false;
            }
        }
        
    
        if (isCompleted) {
            System.out.println("Graph created successfully! Begin simulation..");
        }
        return isCompleted;
    }
    



    public void startSim() {
        // iterate through every edge in the graph and dump it to the console
        Graph simGraph = new Graph();
        for (GraphModel.Edge e: graph.edges()) {
            String srcNode = graph.nodes().stream().filter(n->n.id().equals(e.from)).findFirst().get().name();
            String destNode = graph.nodes().stream().filter(n->n.id().equals(e.to)).findFirst().get().name();
            simGraph.addEdge(srcNode, destNode, (int)e.weight);
        }
        if (traversePath(simGraph) == false) {
            showStatus("ERROR: Graph not complete!");
            return;
        } else {
            showStatus("Graph created successfully! Begin simulation..");
            //BEGIN SIMULATION HERE
            sim.startSimulation();
        }
        for (GraphModel.Edge e : graph.edges()) {
            System.out.println(
                graph.nodes().stream().filter(n->n.id().equals(e.from)).findFirst().get().name()
              + " <-> " + graph.nodes().stream().filter(n->n.id().equals(e.to)).findFirst().get().name()
              + ", weight: " + (e.weight + 1)
            );
        }
    }
}
