package com.xiangqi.online.game;

public final class MatchEvent {
    private final boolean accepted;
    private final String message;

    public MatchEvent(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message;
    }

    public static MatchEvent accepted(String message) {
        return new MatchEvent(true, message);
    }

    public static MatchEvent rejected(String message) {
        return new MatchEvent(false, message);
    }

    public boolean accepted() {
        return accepted;
    }

    public String message() {
        return message;
    }
}
