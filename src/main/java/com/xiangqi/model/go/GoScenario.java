package com.xiangqi.model.go;

public final class GoScenario {
    private final String name;
    private final String description;
    private final int size;
    private final GoStone turn;
    private final String[] rows;

    public GoScenario(String name, String description, int size, GoStone turn, String[] rows) {
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.size = size;
        this.turn = turn == null ? GoStone.BLACK : turn;
        this.rows = rows == null ? new String[0] : rows.clone();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getSize() {
        return size;
    }

    public GoStone getTurn() {
        return turn;
    }

    public String[] getRows() {
        return rows.clone();
    }
}
