// model/Dish.java
package model;

public enum Dish {
    Iced_Chrysanthemum_Tea("Chrysanthemum Tea", 4),
    Water("Water", 3),
    Chinese_Herbal_Drink("Chinese Herbal Drink", 5),
    Spicy_Stir_Fried_Chicken("Spicy Stir-Fried Chicken", 15),
    Yangzhou_Fried_Rice("Yangzhou Fried Rice", 15),
    Szechuan_Tom_Yum("Szechuan Tom Yum", 16),
    Wonton_Soup("Wonton Soup", 12),
    Mango_Pudding("Mango Pudding", 9),
    Sesame_Balls("Sesame Balls", 8),
    Egg_Tart("Egg Tart", 6);

    // Iced_Chrysanthemum_Tea("Chrysanthemum Tea", 1),
    // Water("Water", 1),
    // Chinese_Herbal_Drink("Chinese Herbal Drink", 1),
    // Spicy_Stir_Fried_Chicken("Spicy Stir-Fried Chicken", 1),
    // Yangzhou_Fried_Rice("Yangzhou Fried Rice", 1),
    // Szechuan_Tom_Yum("Szechuan Tom Yum", 1),
    // Wonton_Soup("Wonton Soup", 1),
    // Mango_Pudding("Mango Pudding", 1),
    // Sesame_Balls("Sesame Balls", 1),
    // Egg_Tart("Egg Tart", 1);

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
