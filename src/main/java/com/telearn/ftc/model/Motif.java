package com.telearn.ftc.model;

/**
 * 圖案主題枚舉
 * 對應 AprilTag ID 21, 22, 23
 */
public enum Motif {
    /** Green-Purple-Purple (AprilTag ID 21) */
    GPP(21, "GPP"),

    /** Purple-Green-Purple (AprilTag ID 22) */
    PGP(22, "PGP"),

    /** Purple-Purple-Green (AprilTag ID 23) */
    PPG(23, "PPG"),

    /** 未偵測到 */
    UNKNOWN(-1, "???");

    private final int aprilTagId;
    private final String pattern;

    Motif(int aprilTagId, String pattern) {
        this.aprilTagId = aprilTagId;
        this.pattern = pattern;
    }

    public int getAprilTagId() {
        return aprilTagId;
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * 根據 AprilTag ID 取得對應的主題
     */
    public static Motif fromAprilTagId(int id) {
        for (Motif motif : values()) {
            if (motif.aprilTagId == id) {
                return motif;
            }
        }
        return UNKNOWN;
    }

    /**
     * 檢查給定的文物序列是否符合此主題
     * 
     * @param sequence 文物序列 (長度應為 3 的倍數)
     * @return 符合的數量
     */
    public int countMatches(ArtifactType[] sequence) {
        if (sequence == null || sequence.length < 3) {
            return 0;
        }

        ArtifactType[] patternTypes = getPatternTypes();
        int matches = 0;

        for (int i = 0; i + 2 < sequence.length; i += 3) {
            boolean match = true;
            for (int j = 0; j < 3; j++) {
                if (sequence[i + j] != patternTypes[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                matches += 3;
            }
        }

        return matches;
    }

    /**
     * 取得此主題對應的文物類型陣列
     */
    public ArtifactType[] getPatternTypes() {
        return switch (this) {
            case GPP -> new ArtifactType[] { ArtifactType.GREEN, ArtifactType.PURPLE, ArtifactType.PURPLE };
            case PGP -> new ArtifactType[] { ArtifactType.PURPLE, ArtifactType.GREEN, ArtifactType.PURPLE };
            case PPG -> new ArtifactType[] { ArtifactType.PURPLE, ArtifactType.PURPLE, ArtifactType.GREEN };
            default -> new ArtifactType[] { ArtifactType.UNKNOWN, ArtifactType.UNKNOWN, ArtifactType.UNKNOWN };
        };
    }
}
