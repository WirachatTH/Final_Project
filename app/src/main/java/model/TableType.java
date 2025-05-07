package model;

//ชนิดของ Node และสีของ Node
public enum TableType {
    T2 (2,  "#FFD700"),
    T4 (4,  "#EEC900"),
    T6 (6,  "#DAA520"),
    T8 (8,  "#CD950C"),
    T10(10, "#B8860B"),
    K  (0, "#ee6146"),
    J  (0, "#ff2400");

    public final int seats;
    public final String colorHex;   // สีจาก CSS

    TableType(int seats, String colorHex) {
        this.seats = seats;
        this.colorHex = colorHex;
    }
}