package model;
import java.util.*;

/**
 * ChefQueue manages a FIFO queue of orders for one dish.
 * It tracks when the chef will be available and computes finish times dynamically.
 */
public class ChefQueue {
    private final Queue<Order> q = new ArrayDeque<>();
    // Timestamp (ms) when this chef will be free to start the next order
    private long availableAtMs = 0;

    /** Enqueue a new order for this chef. */
    public void enqueue(Order o) {
        q.add(o);
    }

    /**
     * Process all orders that have finished cooking by nowMs.
     * @param nowMs current timestamp in ms
     * @return list of orders completed by nowMs
     */
    public List<Order> update(long nowMs) {
        List<Order> done = new ArrayList<>();
        // Continuously check head order
        while (!q.isEmpty()) {
            Order head = q.peek();
            // Start time is when chef is free or order placed, whichever is later
            long startMs = Math.max(availableAtMs, head.placedAtMs());
            long finishMs = startMs + head.dish().cookSec() * 1000L;
            if (finishMs <= nowMs) {
                // Order finished
                availableAtMs = finishMs;
                done.add(q.poll());
            } else {
                // Head still cooking, stop
                break;
            }
        }
        return done;
    }

    /**
     * Read-only view of the queue for UI.
     */
    public Collection<Order> getQueueReadonly() {
        return Collections.unmodifiableCollection(q);
    }

    /**
     * Returns the finish timestamp (ms) of the current head order,
     * or the availability timestamp if no pending orders.
     */
    public long getFinishTimeMs() {
        Order head = q.peek();
        if (head == null) {
            return availableAtMs;
        }
        long startMs = Math.max(availableAtMs, head.placedAtMs());
        return startMs + head.dish().cookSec() * 1000L;
    }
}
