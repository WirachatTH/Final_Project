package sim;
import java.util.concurrent.BlockingQueue;

import model.FoodItem;
public class ServeManager implements Runnable {
    private final BlockingQueue<FoodItem> serveQueue;

    public ServeManager(BlockingQueue<FoodItem> serveQueue) {
        this.serveQueue = serveQueue;
    }

    @Override
    public void run() {
        while (true) {
            try {
                FoodItem item = serveQueue.take(); // take อาหารออกจากคิว (การ take คือถ้ามันไม่มีอาหารเลยในคิวมันก็จะรอจนกว่าจะมี) ออกมาอยู่ในตัวแปร item
                serveQueue.add(item); // Ensure the item is added to the queue
                item.setFinishedTime(System.currentTimeMillis()); //เป็นการ set เวลาเมื่ออาหารถึงมือลูกค้า เอาไปใส่ตอนเสิร์ฟเสร็จแล้ว
                //ถ้าจะใส่เข้าไปในคิวเสิร์ฟตัวเองอีกทีก็ใช้ robotQueue.put(item); จะเป็นการเพิ่มไอเทมเข้าไปในคิวของโรบอท ปล. robotQueue คือคิวของโรบอทที่เก็บอาหารสามอย่าง แล้วเอาไปเสิร์ฟลูกค้า
                System.out.println("Serve: " + item + " -> Time used " + (item.totalTime() / 1000.0) + " s\n"); //เทสระบบเฉย ๆ
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}