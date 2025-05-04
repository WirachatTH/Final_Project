package model;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FoodItem { //class ของข้อมูลเสย ๆ จ้า
    private String name; //ชื่ออาหาร
    private int tableId;
    private int cookingTime; //เวลาที่ใช้ในการทำอาหารแต่ละเมนู
    private long orderTime; //เวลาตอนที่อาหารได้ถูกสั่ง
    private long startTime; //เวลาตอนได้เริ่มทำอาหาร
    private long endTime; //เวลาตอนที่อาหารทำเสร็จ
    private long finishedTime; // เวลาที่เสิร์ฟถึงมือลูกค้าแล้ว
    private boolean isCooked = false; // สถานะว่าอาหารทำเสร็จแล้วหรือยัง

    //constructure
    public FoodItem (String name, int tableId){
        this.name = name;
        this.tableId = tableId;
        this.cookingTime = getCookingTimefromMenu(name); //ให้การ assign ค่า cookingTime ขึ้นกับเมนูอาหาร Hence จะใช้ switch case
        this.orderTime = System.currentTimeMillis(); //เป็น method ที่จะบันทึกเวลา
    }

    //getter
    public String getName(){
        return name;
    }
    public int getTableId(){
        return tableId;
    }
    private int getCookingTimefromMenu(String name){ 
        switch (name) {
            case "Iced Chrysanthemum Tea" :
                return 3;
            case "Water" :
                return 2;
            case "Boiled Chinese Herbal Drink" : 
                return 5;
            case "Spicy Stir-Fried Chicken with Cashew Nuts" :
                return 8;
            case "Yangzhou Fried Rice" :
                return 6;
            case "Szechuan Tom Yum with Abalone" :
                return 7;
            case "Wonton Soup" :
                return 6;
            case "Mango Pudding" :
                return 5;
            case "Jingzhou Sesame Balls" :
                return 4;
            case "Egg Tart" :
                return 6;
        }
        return -1;
    }
    public int getCookingTime(){ //cookingTime ตามเมนู
        return cookingTime;
    }
    public long getOrderTime(){
        return orderTime;
    }
    public long getEndTime(){
        return endTime;
    }
    public long getFinishedTime(){
        return finishedTime;
    }
    // Getter สำหรับตรวจสอบสถานะว่าอาหารทำเสร็จแล้วหรือยัง เพิ่มโดยพอช
    public synchronized boolean isCooked() {
        return this.isCooked;
    }
    
    
    //setter - เซ็ตเวลาในขั้นตอนต่าง ๆ ของการรันอาหาร ตั้งแต่รับออเดอร์ถึงเสิร์ฟให้ลูกค้า
    public void setStartTime(long time){
        this.startTime = time;
    }
    public void setEndTime(long time){
        this.endTime = time;
    }
    public void setFinishedTime(long time){
        this.finishedTime = time;
    }
    public long totalTime(){ //เวลาทั้งหมดที่ใช้ตั้งแต่ได้รับออเดอร์จนกว่าจะเสิร์ฟถึงลูกค้า
        return finishedTime - orderTime;
    }
    public long serveTime(){ //เวลาที่ใช้ในการเสิร์ฟ
        return finishedTime - endTime;
    }
    // Setter สำหรับกำหนดสถานะว่าอาหารทำเสร็จแล้ว เพิ่มโดยพอช
    public synchronized void setCooked(boolean cooked) {
        this.isCooked = cooked;
    }


    //set format เวลา ให้อ่านง่าย ๆ
     public String formatTime(long time) {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(time));
    }


    //เอาไว้เทสระบบว่าใช้ได้มั้ย
    @Override
    public String toString() {
        return name + " (Table " + tableId + ") - Order Time " + formatTime(orderTime);
    }
}