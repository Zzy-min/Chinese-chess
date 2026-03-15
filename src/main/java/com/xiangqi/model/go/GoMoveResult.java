package com.xiangqi.model.go;

public final class GoMoveResult {
    private final boolean success;
    private final String reason;
    private final int capturedStones;
    private final boolean pass;

    private GoMoveResult(boolean success, String reason, int capturedStones, boolean pass) {
        this.success = success;
        this.reason = reason == null ? "" : reason;
        this.capturedStones = Math.max(0, capturedStones);
        this.pass = pass;
    }

    public static GoMoveResult success(int capturedStones, boolean pass) {
        return new GoMoveResult(true, "", capturedStones, pass);
    }

    public static GoMoveResult illegal(String reason) {
        return new GoMoveResult(false, reason, 0, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }

    public int getCapturedStones() {
        return capturedStones;
    }

    public boolean isPass() {
        return pass;
    }
}
