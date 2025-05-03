// model/GraphModel.java
package model;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Underlying graph of tables (and junctions) + paths between them.
 */
public class GraphModel {
    /* --- Node & Edge DTOs --- */
    public record Node(String id, String name, double x, double y, TableType type) {}


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

    /* --- Storage --- */
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    // Separate maps for tables vs. junctions
    private final Map<String,Integer> tableIds    = new HashMap<>();
    private final Map<String,Integer> junctionIds = new HashMap<>();

    private String kitchenId = null;
    public Optional<String> kitchenId() { return Optional.ofNullable(kitchenId); }
    public void setKitchenId(String id) { this.kitchenId = id; }

    public Map<String,Integer> tableIds()    { return tableIds;    }
    public Map<String,Integer> junctionIds() { return junctionIds; }

    /** What kind of node is this, and what its user-facing number? */
    public enum NodeKind { TABLE, JUNCTION, KITCHEN }
    public static class NodeInfo {
        public final NodeKind kind;
        public final int     number;
        public NodeInfo(NodeKind kind, int number) {
            this.kind   = kind;
            this.number = number;
        }
    }
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

    /* --- Public API --- */
    public List<Node> nodes() { return nodes; }
    public List<Edge> edges() { return edges; }

    public Node addNode(double x, double y, TableType type) {
        String newId = String.valueOf(nodes.size() + 1);
        String newName;
        if (type == TableType.K) {
          // Kitchen case
          this.kitchenId = newId;
          newName = "K";
        } else if (type == TableType.J) {
          // Junction
          int junNum = junctionIds.size() + 1;
          junctionIds.put(newId, junNum);
          newName = "J" + junNum;
        } else {
          // Normal table
          int tableNum = tableIds.size() + 1;
          tableIds.put(newId, tableNum);
          newName = type.toString() + "-" + tableNum;
        }
        Node n = new Node(newId, newName, x, y, type);
        nodes.add(n);
        return n;
      }
      
    

    /** Add a new undirected edge. */
    public Edge addEdge(String fromId, String toId, List<Point> cellPath) {
        Edge e = new Edge(fromId, toId, cellPath);
        edges.add(e);
        return e;
    }

    /** Remove any edge between the two IDs. */
    public void removeEdge(String idA, String idB) {
        edges.removeIf(e ->
            (e.from.equals(idA) && e.to.equals(idB)) ||
            (e.from.equals(idB) && e.to.equals(idA))
        );
    }

    /** Find (one) edge between the two IDs, if present. */
    public Optional<Edge> findEdge(String idA, String idB) {
        return edges.stream().filter(e ->
            (e.from.equals(idA) && e.to.equals(idB)) ||
            (e.from.equals(idB) && e.to.equals(idA))
        ).findFirst();
    }
}
