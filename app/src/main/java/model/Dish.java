// model/Dish.java
package model;

public enum Dish {
    Iced_Chrysanthemum_Tea("Chrysanthemum Tea", 3),
    Water("Water", 2),
    Chinese_Herbal_Drink("Chinese Herbal Drink", 5),
    Spicy_Stir_Fried_Chicken("Spicy Stir-Fried Chicken", 8),
    Yangzhou_Fried_Rice("Yangzhou Fried Rice", 6),
    Szechuan_Tom_Yum("Szechuan Tom Yum", 7),
    Wonton_Soup("Wonton Soup", 6),
    Mango_Pudding("Mango Pudding", 5),
    Sesame_Balls("Sesame Balls", 4),
    Egg_Tart("Egg Tart", 6);

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
