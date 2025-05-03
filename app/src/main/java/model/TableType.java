package model;

/** เก็บชนิดโต๊ะ + สี (ไว้ใช้ตอนวาด) + จำนวนที่นั่ง */
public enum TableType {
    T2 (2,  "#ffb3b3"),
    T4 (4,  "#ffd59f"),
    T6 (6,  "#c9ffb3"),
    T8 (8,  "#b3d9ff"),
    T10(10, "#dab3ff"),
    K  (0, "#ffff99"),
    J (0, "ff2400");

    public final int seats;
    public final String colorHex;   // CSS สี 6 หลัก

    TableType(int seats, String colorHex) {
        this.seats = seats;
        this.colorHex = colorHex;
    }
}
