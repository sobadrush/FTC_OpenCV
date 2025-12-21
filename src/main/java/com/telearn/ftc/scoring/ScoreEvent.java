package com.telearn.ftc.scoring;

import com.telearn.ftc.model.Alliance;
import lombok.Data;

/**
 * 得分事件記錄
 */
@Data
public class ScoreEvent {

    /**
     * 事件類型
     */
    public enum EventType {
        MATCH_START("比賽開始"),
        MATCH_END("比賽結束"),
        LEAVE("離開"),
        CLASSIFY("分類"),
        OVERFLOW("溢出"),
        PATTERN("圖案加分"),
        PARK("停車"),
        BONUS("獎勵"),
        CLEAR_RAMP("清空坡道");

        private final String displayName;

        EventType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** 事件類型 */
    private EventType type;

    /** 相關聯盟 (可為 null) */
    private Alliance alliance;

    /** 得分數值 */
    private int points;

    /** 描述 */
    private String description;

    /** 時間戳 */
    private long timestamp;

    public ScoreEvent(EventType type, Alliance alliance, int points, String description) {
        this.type = type;
        this.alliance = alliance;
        this.points = points;
        this.description = description;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 取得格式化的時間字串
     */
    public String getTimeString() {
        long seconds = (System.currentTimeMillis() - timestamp) / 1000;
        return String.format("%ds ago", seconds);
    }

    @Override
    public String toString() {
        if (alliance != null) {
            return String.format("[%s] %s: %s (+%d)",
                    type.getDisplayName(),
                    alliance.name(),
                    description,
                    points);
        }
        return String.format("[%s] %s", type.getDisplayName(), description);
    }
}
