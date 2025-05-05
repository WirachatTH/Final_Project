// model/Dish.java
package model;

public enum Dish {
    Iced_Chrysanthemum_Tea("Chrysanthemum Tea", 20),
    Water("Water", 10),
    Chinese_Herbal_Drink("Chinese Herbal Drink", 15),
    Spicy_Stir_Fried_Chicken("Spicy Stir-Fried Chicken", 60),
    Yangzhou_Fried_Rice("Yangzhou Fried Rice", 60),
    Szechuan_Tom_Yum("Szechuan Tom Yum", 70),
    Wonton_Soup("Wonton Soup", 55),
    Mango_Pudding("Mango Pudding", 45),
    Sesame_Balls("Sesame Balls", 40),
    Egg_Tart("Egg Tart", 35);

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
