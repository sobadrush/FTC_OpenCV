package com.telearn.ftc.model;

import lombok.Data;

/**
 * 聯盟得分
 * 追蹤單一聯盟的所有得分項目
 */
@Data
public class AllianceScore {
    /** 所屬聯盟 */
    private final Alliance alliance;

    // ===== 自主階段分數 =====
    /** 機器人離開得分 (每台 3 分，最多 6 分) */
    private int autoLeaveScore = 0;

    /** 自主階段分類得分 */
    private int autoClassifiedScore = 0;

    /** 自主階段圖案加分 */
    private int autoPatternScore = 0;

    // ===== 遙控階段分數 =====
    /** 分類得分 (每顆 3 分) */
    private int classifiedScore = 0;

    /** 溢出得分 (每顆 1 分) */
    private int overflowScore = 0;

    // ===== 終局階段分數 =====
    /** 停車得分 (每台 10 分) */
    private int parkingScore = 0;

    /** 雙機完全返回獎勵 (10 分) */
    private int parkingBonusScore = 0;

    /** 終局圖案加分 */
    private int endgamePatternScore = 0;

    // ===== 統計資料 =====
    /** 已分類文物總數 */
    private int totalClassified = 0;

    /** 溢出文物總數 */
    private int totalOverflow = 0;

    /** 完成的完整循環次數 */
    private int completedCycles = 0;

    public AllianceScore(Alliance alliance) {
        this.alliance = alliance;
    }

    /**
     * 重置所有分數
     */
    public void reset() {
        this.autoLeaveScore = 0;
        this.autoClassifiedScore = 0;
        this.autoPatternScore = 0;
        this.classifiedScore = 0;
        this.overflowScore = 0;
        this.parkingScore = 0;
        this.parkingBonusScore = 0;
        this.endgamePatternScore = 0;
        this.totalClassified = 0;
        this.totalOverflow = 0;
        this.completedCycles = 0;
    }

    /**
     * 計算總分
     */
    public int getTotalScore() {
        return autoLeaveScore
                + autoClassifiedScore
                + autoPatternScore
                + classifiedScore
                + overflowScore
                + parkingScore
                + parkingBonusScore
                + endgamePatternScore;
    }

    /**
     * 記錄文物分類得分
     */
    public void addClassified(int count, boolean isAuto) {
        int points = count * 3;
        if (isAuto) {
            this.autoClassifiedScore += points;
        } else {
            this.classifiedScore += points;
        }
        this.totalClassified += count;
    }

    /**
     * 記錄溢出得分
     */
    public void addOverflow(int count) {
        this.overflowScore += count;
        this.totalOverflow += count;
    }

    /**
     * 記錄機器人離開得分
     */
    public void addLeave(int robotCount) {
        this.autoLeaveScore += robotCount * 3;
    }

    /**
     * 記錄停車得分
     * 
     * @param robotsParked      停車的機器人數量
     * @param bothFullyReturned 是否兩台都完全返回
     */
    public void addParking(int robotsParked, boolean bothFullyReturned) {
        this.parkingScore += robotsParked * 10;
        if (bothFullyReturned) {
            this.parkingBonusScore = 10;
        }
    }

    /**
     * 記錄圖案加分
     */
    public void addPatternScore(int matchCount, boolean isAuto) {
        int points = matchCount * 2;
        if (isAuto) {
            this.autoPatternScore = points;
        } else {
            this.endgamePatternScore = points;
        }
    }

    /**
     * 取得分數摘要字串
     */
    public String getSummary() {
        return String.format(
                "%s: 總分 %d (Auto:%d TeleOp:%d Endgame:%d)",
                alliance.getDisplayName(),
                getTotalScore(),
                getAutoSubtotal(),
                getTeleOpSubtotal(),
                getEndgameSubtotal());
    }

    /** 自主階段小計 */
    public int getAutoSubtotal() {
        return autoLeaveScore + autoClassifiedScore + autoPatternScore;
    }

    /** 遙控階段小計 */
    public int getTeleOpSubtotal() {
        return classifiedScore + overflowScore;
    }

    /** 終局階段小計 */
    public int getEndgameSubtotal() {
        return parkingScore + parkingBonusScore + endgamePatternScore;
    }
}
