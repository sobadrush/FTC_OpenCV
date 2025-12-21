package com.telearn.ftc.model;

/**
 * 比賽階段枚舉
 */
public enum MatchPhase {
    /** 比賽前 */
    PRE_MATCH("比賽前", 0),

    /** 自主階段 (前 30 秒) */
    AUTONOMOUS("自主階段", 30),

    /** 遙控階段 (2 分鐘) */
    TELEOP("遙控階段", 120),

    /** 終局階段 (最後 30 秒，屬於遙控階段) */
    ENDGAME("終局階段", 30),

    /** 比賽結束 */
    POST_MATCH("比賽結束", 0);

    private final String displayName;
    private final int durationSeconds;

    MatchPhase(String displayName, int durationSeconds) {
        this.displayName = displayName;
        this.durationSeconds = durationSeconds;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }
}
