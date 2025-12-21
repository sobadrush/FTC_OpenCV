package com.telearn.ftc.model;

import lombok.Data;
import org.opencv.core.Point;
import org.opencv.core.Rect;

/**
 * 偵測到的機器人資訊
 */
@Data
public class DetectedRobot {
    /** 唯一識別碼 */
    private int id;

    /** 所屬聯盟 */
    private Alliance alliance;

    /** 在影像中的邊界框 */
    private Rect boundingBox;

    /** 中心座標 */
    private Point center;

    /** 偵測信心度 */
    private double confidence;

    /** 偵測時間戳 */
    private long timestamp;

    /** 當前所在區域 */
    private String currentZone;

    /** 預估速度 (像素/秒) */
    private double estimatedSpeed;

    /** 是否已停車 (Parked) */
    private boolean parked;

    /** 是否完全返回基地 */
    private boolean fullyReturned;

    /** 攜帶的文物數量 (預估) */
    private int carryingArtifacts;

    public DetectedRobot() {
        this.timestamp = System.currentTimeMillis();
    }

    public DetectedRobot(Alliance alliance, Rect boundingBox, double confidence) {
        this();
        this.alliance = alliance;
        this.boundingBox = boundingBox;
        this.confidence = confidence;
        if (boundingBox != null) {
            this.center = new Point(
                    boundingBox.x + boundingBox.width / 2.0,
                    boundingBox.y + boundingBox.height / 2.0);
        }
    }

    /**
     * 更新位置並計算速度
     */
    public void updatePosition(Point newCenter, long newTimestamp) {
        if (this.center != null && newTimestamp > this.timestamp) {
            double dx = newCenter.x - this.center.x;
            double dy = newCenter.y - this.center.y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            double timeDelta = (newTimestamp - this.timestamp) / 1000.0;
            this.estimatedSpeed = distance / timeDelta;
        }
        this.center = newCenter;
        this.timestamp = newTimestamp;
    }
}
