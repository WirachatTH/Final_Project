package sim;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

import model.FoodItem;

public class Kitchen {
    private static final ExecutorService menuThread = Executors.newCachedThreadPool(); //สร้างกลุ่ม thread เพื่อแยกแถวเมนู 
    private static final BlockingQueue<FoodItem> serveQueue = new PriorityBlockingQueue<>(10, Comparator.comparingLong(FoodItem::getEndTime)); //เก็บอาการที่ทำเสร็จและพร้อมเสิร์ฟ coparator จากเวลาที่ทำอาหารเสร็จ
    private static final Set<String> activeCookingMenus = ConcurrentHashMap.newKeySet(); //เก็บ set ชื่อเมนูที่จะตรวจสอบว่ากำลังทำเมนูนี้อยู่มั้ย contains คือเช็คค่าว่ามีค่านี้ใน set มั้ย จะคืนค่ามาเป็น true, false
    private static final Object lock = new Object(); //สร้าง object เปล่า ๆ ขึ้นมา

    //เพิ่มออเดอร์เข้าคิวสำหรับรอทำอาหาร
    public static void addOrder(FoodItem item){
        menuThread.submit(() -> cook(item));
    }

    //method การทำอาหาร ทำเสร็จใส่ serveQueue
    private static void cook(FoodItem item) {
        try {
            synchronized (lock) {
                while (activeCookingMenus.contains(item.getName())) {
                    lock.wait();
                }
                activeCookingMenus.add(item.getName());
            }

            // เริ่มทำอาหาร โค้ดใหม่พอชเพราะจะดูว่าเสิร์ฟอะไรได้บ้างแล้ว
            item.setStartTime(System.currentTimeMillis());
            System.out.println("Start cooking: " + item);
            Thread.sleep(item.getCookingTime() * 1000L); // จำลองเวลาทำอาหาร
            item.setCooked(true); // กำหนดสถานะว่าอาหารทำเสร็จแล้ว
            System.out.println("Finished cooking: " + item);

            /*โค้ดอุ้ม
            item.setStartTime(System.currentTimeMillis());
            System.out.println("เริ่มทำ: " + item); //testt******
            Thread.sleep(item.getCookingTime()*1000L);
            item.setEndTime(System.currentTimeMillis());
            serveQueue.put(item);
            System.out.println("เสร็จแล้ว: " + item); //test******
            */

            serveQueue.put(item);

            synchronized (lock) {
                activeCookingMenus.remove(item.getName());
                lock.notifyAll();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static BlockingQueue<FoodItem> getServeQueue(){
        return serveQueue;
    }

    //ปิด thread pool เรียกใช้งานตอนโปรแกรมทำงานเสร็จ
    public static void shutdown() {
        menuThread.shutdown();
    }
}