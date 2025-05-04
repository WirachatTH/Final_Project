package model;

import sim.Kitchen;

/**
 * Creates and submits FoodItem orders to the Kitchen.
 */
public class OrderManager {
    /**
     * Build a new FoodItem and send it to the kitchen for cooking.
     * param name     The menu name.
     * param tableId  The numeric table identifier.
     * return the created FoodItem
     */
    public static FoodItem takeOrder(String name, int tableId) {
        FoodItem item = new FoodItem(name, tableId);
        Kitchen.addOrder(item);
        return item;
    }
}
