package com.telearn.ftc.model;

/**
 * 文物類型枚舉
 * 代表 FTC 2025 賽季中的兩種文物顏色
 */
public enum ArtifactType {
    /** 紫色文物 */
    PURPLE("紫色", "P"),

    /** 綠色文物 */
    GREEN("綠色", "G"),

    /** 未知/無法辨識 */
    UNKNOWN("未知", "?");

    private final String displayName;
    private final String code;

    ArtifactType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }
}
