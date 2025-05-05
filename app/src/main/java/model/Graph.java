package model;

import java.util.*;
import java.util.stream.Collectors;



public class Graph {
    private Map<String, List<Edge>> adjacencyList = new HashMap<>();

    // เพิ่ม Edge เข้าไปในกราฟ
    public void addEdge(String src, String dest, int weight) {
        adjacencyList.putIfAbsent(src, new ArrayList<>());
        adjacencyList.putIfAbsent(dest, new ArrayList<>());
        
        adjacencyList.get(src).add(new Edge(src, dest, weight));
        adjacencyList.get(dest).add(new Edge(dest, src, weight)); // ทำกราฟสองทิศทาง
    }

    // คำนวณเส้นทางที่สั้นที่สุดจาก src ไปยัง dest ด้วย Dijkstra's Algorithm
    public List<String> dijkstra(String start, String end) {
        System.err.println("[DIJKSTRA] Keys in adjacencyList: " + adjacencyList.keySet());
        System.err.println("[DIJKSTRA] Checking for start=" + start + ", end=" + end);
        if (!adjacencyList.containsKey(start) || !adjacencyList.containsKey(end)) {
            System.err.println("[DIJKSTRA] Missing node: start=" + start + ", end=" + end);
            return Collections.emptyList();
        }
        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> previousNodes = new HashMap<>();
        PriorityQueue<Edge> pq = new PriorityQueue<>(Comparator.comparingInt(Edge::getWeight));
        
        for (String node : adjacencyList.keySet()) {
            distances.put(node, Integer.MAX_VALUE);
            previousNodes.put(node, null);
        }
        distances.put(start, 0);
        
        pq.add(new Edge(start, start, 0));

        while (!pq.isEmpty()) {
            Edge currentEdge = pq.poll();
            String currentNode = currentEdge.getDest();

            if (currentNode.equals(end)) break;

            for (Edge edge : adjacencyList.getOrDefault(currentNode, Collections.emptyList())) {
                String neighbor = edge.getDest();
                int newDist = distances.get(currentNode) + edge.getWeight();

                if (newDist < distances.get(neighbor)) {
                    distances.put(neighbor, newDist);
                    previousNodes.put(neighbor, currentNode);
                    pq.add(new Edge(currentNode, neighbor, newDist));
                }
            }
        }

        List<String> path = new ArrayList<>();
        String current = end;
        while (current != null) {
            path.add(current);
            current = previousNodes.get(current);
        }
        Collections.reverse(path);
        return path;
    }

    // คลาส Edge เพื่อเก็บข้อมูลเกี่ยวกับการเดินทาง
    public static class Edge {
        private String src;
        private String dest;
        private int weight;

        public Edge(String src, String dest, int weight) {
            this.src = src;
            this.dest = dest;
            this.weight = weight;
        }

        public String getSrc() {
            return src;
        }

        public String getDest() {
            return dest;
        }

        public int getWeight() {
            return weight;
        }
    }

    public List<Edge> getAllEdges() {
    return adjacencyList.values().stream()
        .flatMap(List::stream)
        .collect(Collectors.toList());
    }

    /** 
     * Return the weight of the edge from src → dest, or throw if none exists.
     */
    public double getWeight(String src, String dest) {
        for (Edge e : adjacencyList.getOrDefault(src, Collections.emptyList())) {
            if (e.getDest().equals(dest)) {
                return e.getWeight();
            }
        }
        throw new IllegalArgumentException("No edge from " + src + " to " + dest);
    }

}