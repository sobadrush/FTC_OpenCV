package com.telearn.ftc.model;

import lombok.Data;
import org.opencv.core.Point;
import org.opencv.core.Rect;

/**
 * 得分區域
 * 用於標記球門 (Goal) 位置
 */
@Data
public class ScoringZone {
    /** 唯一識別碼 */
    private int id;

    /** 區域名稱 */
    private String name;

    /** 所屬聯盟 */
    private Alliance alliance;

    /** 區域邊界 */
    private Rect bounds;

    /** 中心點 */
    private Point center;

    /** 已在此區域計分的球數 */
    private int scoredCount = 0;

    public ScoringZone(int id, String name, Alliance alliance, Rect bounds) {
        this.id = id;
        this.name = name;
        this.alliance = alliance;
        this.bounds = bounds;
        if (bounds != null) {
            this.center = new Point(
                    bounds.x + bounds.width / 2.0,
                    bounds.y + bounds.height / 2.0);
        }
    }

    /**
     * 檢查指定點是否在此區域內
     */
    public boolean contains(Point point) {
        if (bounds == null || point == null)
            return false;
        return point.x >= bounds.x && point.x <= bounds.x + bounds.width &&
                point.y >= bounds.y && point.y <= bounds.y + bounds.height;
    }

    /**
     * 檢查指定文物是否在此區域內
     */
    public boolean contains(DetectedArtifact artifact) {
        return contains(artifact.getCenter());
    }

    /**
     * 記錄一次得分
     */
    public void recordScore() {
        scoredCount++;
    }

    /**
     * 重置計分
     */
    public void reset() {
        scoredCount = 0;
    }
}
