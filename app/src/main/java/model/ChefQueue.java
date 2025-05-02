package model;
import java.util.*;

public class ChefQueue {
    private final Queue<Order> q = new ArrayDeque<>();
    private long finishTimeMs = 0;     // เวลาเชฟว่าง

    public void enqueue(Order o){ q.add(o); }

    /** คืนรายการออเดอร์ที่เสร็จ ≤ nowMs */
    public List<Order> update(long nowMs){
        List<Order> done = new ArrayList<>();
        while (!q.isEmpty()){
            Order peek = q.peek();
            if (finishTimeMs == 0 || finishTimeMs <= nowMs){
                finishTimeMs = Math.max(finishTimeMs, nowMs)
                              + peek.dish().cookSec()*1000L;
                done.add(q.poll());
            } else break;
        }
        return done;
    }

    /** read-only view สำหรับ UI */
    public Collection<Order> getQueueReadonly(){              // ★ เปลี่ยนชนิดเป็น Collection
        return Collections.unmodifiableCollection(q);         // ★ ใช้ unmodifiableCollection
    }

    public long getFinishTimeMs(){
        return finishTimeMs;      // เวลา ณ ซึ่งเชฟคนนี้จะว่าง (= อาหารใบแรกเสร็จ)
    }
    
}
