package com.xiangqi.model.gomoku;

public final class GomokuPlaceResult {
    private final boolean success;
    private final boolean forbidden;
    private final String reason;

    private GomokuPlaceResult(boolean success, boolean forbidden, String reason) {
        this.success = success;
        this.forbidden = forbidden;
        this.reason = reason == null ? "" : reason;
    }

    public static GomokuPlaceResult success() {
        return new GomokuPlaceResult(true, false, "");
    }

    public static GomokuPlaceResult illegal(String reason) {
        return new GomokuPlaceResult(false, false, reason);
    }

    public static GomokuPlaceResult forbidden(String reason) {
        return new GomokuPlaceResult(false, true, reason);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isForbidden() {
        return forbidden;
    }

    public String getReason() {
        return reason;
    }
}

