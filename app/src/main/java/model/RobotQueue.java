// model/RobotQueue.java
package model;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
public class RobotQueue {
    private final Queue<Order> ready = new ArrayDeque<>();
    public void add(Order o){ ready.add(o); }
    public List<Order> dispatch(int n){
        List<Order> trip = new ArrayList<>();
        while(trip.size()<n && !ready.isEmpty()) trip.add(ready.poll());
        return trip;
    }
    public Queue<Order> getQueue(){return ready;}

    // In RobotQueue.java
    public void clear() {
        ready.clear();
    }
}
