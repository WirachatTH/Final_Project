// model/Dish.java
package model;

public enum Dish { //enum of dish with its name and cooking time
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

    public final String name;
    private final int cookSec;       

    Dish(String n, int s) {
        name = n;
        cookSec = s;
    }

    /** เวลาในการปรุง (วินาที) */
    public int cookSec() { //a getter from another function
        return cookSec;
    }
}
