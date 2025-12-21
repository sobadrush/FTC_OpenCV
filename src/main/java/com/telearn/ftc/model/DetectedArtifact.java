package com.telearn.ftc.model;

import lombok.Data;
import org.opencv.core.Point;
import org.opencv.core.Rect;

/**
 * 偵測到的文物資訊
 */
@Data
public class DetectedArtifact {
    /** 唯一識別碼 (用於追蹤) */
    private int id;

    /** 文物類型 */
    private ArtifactType type;

    /** 在影像中的邊界框 */
    private Rect boundingBox;

    /** 中心座標 */
    private Point center;

    /** 偵測信心度 (0.0 - 1.0) */
    private double confidence;

    /** 偵測時間戳 (毫秒) */
    private long timestamp;

    /** 是否已被計分 */
    private boolean scored;

    /** 所在區域 */
    private String zone;

    public DetectedArtifact() {
        this.timestamp = System.currentTimeMillis();
    }

    public DetectedArtifact(ArtifactType type, Rect boundingBox, double confidence) {
        this();
        this.type = type;
        this.boundingBox = boundingBox;
        this.confidence = confidence;
        if (boundingBox != null) {
            this.center = new Point(
                    boundingBox.x + boundingBox.width / 2.0,
                    boundingBox.y + boundingBox.height / 2.0);
        }
    }

    /**
     * 計算與另一個文物的距離
     */
    public double distanceTo(DetectedArtifact other) {
        if (this.center == null || other.center == null) {
            return Double.MAX_VALUE;
        }
        double dx = this.center.x - other.center.x;
        double dy = this.center.y - other.center.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 計算與指定點的距離
     */
    public double distanceTo(Point point) {
        if (this.center == null || point == null) {
            return Double.MAX_VALUE;
        }
        double dx = this.center.x - point.x;
        double dy = this.center.y - point.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
