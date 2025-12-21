package com.telearn.ftc.model;

/**
 * 聯盟枚舉
 */
public enum Alliance {
    RED("紅色聯盟", 0),
    BLUE("藍色聯盟", 1);

    private final String displayName;
    private final int index;

    Alliance(String displayName, int index) {
        this.displayName = displayName;
        this.index = index;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getIndex() {
        return index;
    }
}
