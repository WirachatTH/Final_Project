package model;
public record Order( //add a record for each customer's order
    int tableNumber,
    Dish dish,
    long placedAtMs //timestamps of when the order is placed
){}
