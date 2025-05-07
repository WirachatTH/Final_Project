package model;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GraphModel {
    //node is kept as a record, containing its ID, name, xy coordinates, and a type of node
    public record Node(String id, String name, double x, double y, TableType type) {}

    //edge containing the name of src node and dest node, list of cells that the edge was drawn on, weight
    public static class Edge {
        public final String from, to;
        public final List<Point> cells;
        public final double weight;
        public Edge(String from, String to, List<Point> cells) {
            this.from   = from;
            this.to     = to;
            this.cells  = new ArrayList<>(cells);
            this.weight = cells.size();
        }
    }

    //array list of all nodes and edges
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    //tables and junctions are kept in different maps
    private final Map<String,Integer> tableIds    = new HashMap<>();
    private final Map<String,Integer> junctionIds = new HashMap<>();

    //create a kitchen node
    private String kitchenId = null;
    public Optional<String> kitchenId() { return Optional.ofNullable(kitchenId); }
    public void setKitchenId(String id) { this.kitchenId = id; }

    public Map<String,Integer> tableIds()    { return tableIds;    }
    public Map<String,Integer> junctionIds() { return junctionIds; }

    //what kind of node is this, and its user-facing number
    public enum NodeKind { TABLE, JUNCTION, KITCHEN }
    public static class NodeInfo {
        public final NodeKind kind;
        public final int     number;
        public NodeInfo(NodeKind kind, int number) {
            this.kind   = kind;
            this.number = number;
        }
    }

    //retrieve the information of node by its ID
    public Optional<NodeInfo> getNodeInfo(String id) {
        if (kitchenId != null && kitchenId.equals(id)) {
            return Optional.of(new NodeInfo(NodeKind.KITCHEN, 1));
        }
        Integer t = tableIds.get(id);
        if (t != null) return Optional.of(new NodeInfo(NodeKind.TABLE, t));
        Integer j = junctionIds.get(id);
        if (j != null) return Optional.of(new NodeInfo(NodeKind.JUNCTION, j));
        return Optional.empty();
    }

    public List<Node> nodes() { return nodes; } //a getter to get all nodes in the graph
    public List<Edge> edges() { return edges; } //a getter to get all edges in the graph

    //add a new node with XY coordinates and its type
    public Node addNode(double x, double y, TableType type) {
        //prevent multiple kitchens
        if (type == TableType.K && this.kitchenId != null) {
            throw new IllegalStateException("Only one Kitchen (K) is allowed.");
        }
        String newId = String.valueOf(nodes.size() + 1);
        String newName;
        if (type == TableType.K) {
            //kitchen name
            this.kitchenId = newId;
            newName = "K";
        } else if (type == TableType.J) {
            //junction name
            int junNum = junctionIds.size() + 1;
            junctionIds.put(newId, junNum);
            newName = "J" + junNum;
        } else {
            //table name
            int tableNum = tableIds.size() + 1;
            tableIds.put(newId, tableNum);
            newName = type.toString() + "-" + tableNum;
        }
        Node n = new Node(newId, newName, x, y, type);
        nodes.add(n);
        return n;
      }
      
    

    //add a new edge with src node, dest node, a list of paths
    public Edge addEdge(String fromId, String toId, List<Point> cellPath) {
        Edge e = new Edge(fromId, toId, cellPath);
        edges.add(e);
        return e;
    }

    //remove edge with src node, dest node
    public void removeEdge(String idA, String idB) {
        edges.removeIf(e ->
            (e.from.equals(idA) && e.to.equals(idB)) ||
            (e.from.equals(idB) && e.to.equals(idA))
        );
    }

    //find an edge in the graph with src node, dest node
    public Optional<Edge> findEdge(String idA, String idB) {
        return edges.stream().filter(e ->
            (e.from.equals(idA) && e.to.equals(idB)) ||
            (e.from.equals(idB) && e.to.equals(idA))
        ).findFirst();
    }
}
