package model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/*
   ใช้จัดการคิวของออเดอร์ที่ทำเสร็จแล้วและพร้อมส่ง
   ทำหน้าที่เป็น buffer ระหว่างครัว (Chef) และหุ่นยนต์ส่งอาหาร
   โดยเก็บออเดอร์ที่ปรุงเสร็จแล้วแต่ยังไม่ได้ส่ง
 */
public class RobotQueue {
    
    
    // คิวอาหารสำหรับเก็บออเดอร์ที่หุ่นยนต์จะเอาไปส่ง
     
    private final Queue<Order> ready = new ArrayDeque<>();
    
    
    // เพิ่มออเดอร์ที่ปรุงเสร็จแล้วลงในคิวเรียกใช้เมื่ออาหารทำเสร็จและพร้อมส่งออเดอร์ที่เสร็จแล้วและจะเพิ่มเข้าในคิว
     
    public void add(Order o){ 
        ready.add(o); 
    }
    
    // ดึงออเดอร์สูงสุด n รายการออกจากคิวเพื่อส่งด้วยหุ่นยนต์ใช้เมื่อหุ่นยนต์พร้อมจะออกไปส่งอาหาร
    public List<Order> dispatch(int n){
        List<Order> trip = new ArrayList<>();
        // ดึงออกจากคิวจนกว่าจะครบ n รายการหรือคิวว่าง
        while(trip.size() < n && !ready.isEmpty()) {
            trip.add(ready.poll());
        }
        return trip;
    }
    
    /**
     * คืนค่า reference ของคิว
     * ใช้โดย UI เพื่อแสดงสถานะคิวของหุ่นยนต์ปัจจุบัน
     *
     * @return คิวของออเดอร์ที่รอส่ง
     */
    public Queue<Order> getQueue(){
        return ready;
    }

    // ล้างคิวทั้งหมด
    public void clear() {
        ready.clear();
    }
}