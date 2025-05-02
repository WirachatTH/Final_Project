// sim/SimulationEngine.java
package sim;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import model.ChefQueue;
import model.Dish;
import model.Order;
import model.RobotQueue;

public class SimulationEngine {
    private final ChefQueue[] chefs = new ChefQueue[Dish.values().length];
    private final RobotQueue robotQ = new RobotQueue();
    private boolean robotBusy = false;
    public SimulationEngine(){
        for(int i=0;i<chefs.length;i++) chefs[i]=new ChefQueue();
        Timeline loop = new Timeline(new KeyFrame(Duration.seconds(1), e->tick()));
        loop.setCycleCount(Timeline.INDEFINITE); loop.play();
    }
    public void placeOrder(int tableId,Dish d){
        chefs[d.ordinal()].enqueue(new Order(tableId,d,System.currentTimeMillis()));
    }
    private void tick(){
        long now = System.currentTimeMillis();
        // 1. update ทั้ง 6 คิว
        for(ChefQueue c:chefs){
            for(Order done: c.update(now)) robotQ.add(done);
        }
        // 2. ถ้าหุ่นว่าง → รับ 3 คิว
        if(!robotBusy && robotQ.getQueue().size()>0){
            var trip = robotQ.dispatch(3);
            robotBusy=true;
            // TODO: คำนวณเวลาเดินตาม GraphModel แล้ว set robotBusy=false เมื่อถึงเวลา
        }
        // TODO: แจ้ง KitchenPane (ผ่าน listener) ให้ refresh ตาราง
    }
    // getters ให้ UI ดึงข้อมูล
    public ChefQueue[] chefQueues(){return chefs;}
    public RobotQueue robotQueue(){return robotQ;}
    public boolean isRobotBusy(){return robotBusy;}
}
