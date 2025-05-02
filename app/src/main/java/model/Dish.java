// model/Dish.java
package model;

public enum Dish {
    FRIED_RICE("ข้าวผัดหมู", 15),
    GAPAO("กะเพราหมูสับ", 12),
    OMELET_RICE("ข้าวไข่เจียว", 10),
    FRIED_CHICKEN("ไก่ทอด", 20),
    WATER("น้ำเปล่า", 5),
    SODA("น้ำอัดลม", 8);

    public final String name;
    private final int cookSec;           // เปลี่ยนเป็น private ให้เข้าถึงผ่าน getter

    Dish(String n, int s) {
        name = n;
        cookSec = s;
    }

    /** เวลาในการปรุง (วินาที) */
    public int cookSec() {               // ★ getter ที่เพิ่มเข้ามา
        return cookSec;
    }
}
