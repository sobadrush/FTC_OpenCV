package com.telearn.ftc.vision;

import com.telearn.ftc.model.Motif;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;
import org.opencv.objdetect.DetectorParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AprilTag / ArUco 標記偵測器
 * 用於識別方尖碑上的標記以判斷比賽主題 (Motif)
 * 
 * FTC 2025 使用 AprilTag ID:
 * - ID 21: GPP (Green-Purple-Purple)
 * - ID 22: PGP (Purple-Green-Purple)
 * - ID 23: PPG (Purple-Purple-Green)
 * 
 * 注意: 由於 OpenCV Java 綁定對 AprilTag 的支援有限，
 * 此實作使用 ArUco 字典作為替代方案。
 * 如需完整 AprilTag 支援，可整合 WPILib apriltag-java 套件。
 */
@Slf4j
public class AprilTagDetector {

    /**
     * 偵測到的標記資訊
     */
    @Data
    public static class TagDetection {
        private int id;
        private Point[] corners;
        private Point center;
        private double size;
        private long timestamp;

        public TagDetection(int id, Point[] corners) {
            this.id = id;
            this.corners = corners;
            this.timestamp = System.currentTimeMillis();
            calculateCenter();
            calculateSize();
        }

        private void calculateCenter() {
            if (corners != null && corners.length == 4) {
                double sumX = 0, sumY = 0;
                for (Point p : corners) {
                    sumX += p.x;
                    sumY += p.y;
                }
                this.center = new Point(sumX / 4, sumY / 4);
            }
        }

        private void calculateSize() {
            if (corners != null && corners.length == 4) {
                // 計算對角線長度作為大小估計
                double d1 = Math.sqrt(
                        Math.pow(corners[2].x - corners[0].x, 2) +
                                Math.pow(corners[2].y - corners[0].y, 2));
                double d2 = Math.sqrt(
                        Math.pow(corners[3].x - corners[1].x, 2) +
                                Math.pow(corners[3].y - corners[1].y, 2));
                this.size = (d1 + d2) / 2;
            }
        }
    }

    // ArUco 偵測器
    private ArucoDetector arucoDetector;
    private Dictionary dictionary;
    private DetectorParameters detectorParams;

    // 是否成功初始化
    private boolean initialized = false;

    // 最後偵測到的主題
    private Motif lastDetectedMotif = Motif.UNKNOWN;
    private long lastDetectionTime = 0;

    // 偵測穩定性計數器 (避免閃爍)
    private int[] motifCounts = new int[4]; // UNKNOWN, GPP, PGP, PPG
    private static final int STABILITY_THRESHOLD = 5;

    public AprilTagDetector() {
        initialize();
    }

    /**
     * 初始化偵測器
     */
    private void initialize() {
        try {
            // 使用 DICT_APRILTAG_36h11 字典 (最接近 FTC 使用的 AprilTag)
            dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_APRILTAG_36h11);
            detectorParams = new DetectorParameters();

            // 調整參數以提高偵測率
            // 注意: 某些參數在不同 OpenCV 版本可能有不同的存取方式

            arucoDetector = new ArucoDetector(dictionary, detectorParams);

            initialized = true;
            log.info("AprilTag 偵測器初始化完成 (使用 DICT_APRILTAG_36h11)");

        } catch (Exception e) {
            log.error("AprilTag 偵測器初始化失敗: {}", e.getMessage());
            log.info("將使用備用的顏色標記偵測方法");
            initialized = false;
        }
    }

    /**
     * 偵測影像中的所有 AprilTag 標記
     */
    public List<TagDetection> detect(Mat frame) {
        List<TagDetection> detections = new ArrayList<>();

        if (!initialized || frame == null || frame.empty()) {
            return detections;
        }

        try {
            // 轉換為灰階
            Mat gray = new Mat();
            if (frame.channels() > 1) {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                gray = frame.clone();
            }

            // 偵測標記
            List<Mat> corners = new ArrayList<>();
            Mat ids = new Mat();

            arucoDetector.detectMarkers(gray, corners, ids);

            // 處理偵測結果
            if (!ids.empty()) {
                for (int i = 0; i < ids.rows(); i++) {
                    int id = (int) ids.get(i, 0)[0];
                    Mat cornerMat = corners.get(i);

                    Point[] cornerPoints = new Point[4];
                    for (int j = 0; j < 4; j++) {
                        cornerPoints[j] = new Point(
                                cornerMat.get(0, j)[0],
                                cornerMat.get(0, j)[1]);
                    }

                    detections.add(new TagDetection(id, cornerPoints));
                }
            }

            gray.release();
            ids.release();

        } catch (Exception e) {
            log.debug("AprilTag 偵測發生錯誤: {}", e.getMessage());
        }

        return detections;
    }

    /**
     * 偵測並取得比賽主題
     */
    public Optional<Motif> detectMotif(Mat frame) {
        List<TagDetection> detections = detect(frame);

        Motif detected = Motif.UNKNOWN;

        for (TagDetection tag : detections) {
            // 檢查是否為 FTC 主題標記
            if (tag.getId() >= 21 && tag.getId() <= 23) {
                detected = Motif.fromAprilTagId(tag.getId());
                break;
            }
        }

        // 更新穩定性計數
        updateStabilityCount(detected);

        // 取得穩定的主題
        Motif stableMotif = getStableMotif();

        if (stableMotif != Motif.UNKNOWN) {
            lastDetectedMotif = stableMotif;
            lastDetectionTime = System.currentTimeMillis();
            return Optional.of(stableMotif);
        }

        return Optional.empty();
    }

    /**
     * 更新穩定性計數器
     */
    private void updateStabilityCount(Motif detected) {
        int index = switch (detected) {
            case GPP -> 1;
            case PGP -> 2;
            case PPG -> 3;
            default -> 0;
        };

        // 增加偵測到的主題計數，減少其他
        for (int i = 0; i < motifCounts.length; i++) {
            if (i == index) {
                motifCounts[i] = Math.min(motifCounts[i] + 1, STABILITY_THRESHOLD * 2);
            } else {
                motifCounts[i] = Math.max(motifCounts[i] - 1, 0);
            }
        }
    }

    /**
     * 取得穩定的主題 (超過閾值)
     */
    private Motif getStableMotif() {
        if (motifCounts[1] >= STABILITY_THRESHOLD)
            return Motif.GPP;
        if (motifCounts[2] >= STABILITY_THRESHOLD)
            return Motif.PGP;
        if (motifCounts[3] >= STABILITY_THRESHOLD)
            return Motif.PPG;
        return Motif.UNKNOWN;
    }

    /**
     * 在影像上繪製偵測結果
     */
    public void drawDetections(Mat frame, List<TagDetection> detections) {
        for (TagDetection tag : detections) {
            Point[] corners = tag.getCorners();

            // 繪製標記邊框
            Scalar color = getColorForId(tag.getId());
            for (int i = 0; i < 4; i++) {
                Imgproc.line(frame, corners[i], corners[(i + 1) % 4], color, 2);
            }

            // 繪製中心點
            Imgproc.circle(frame, tag.getCenter(), 5, color, -1);

            // 繪製 ID 標籤
            String label = "ID:" + tag.getId();
            if (tag.getId() >= 21 && tag.getId() <= 23) {
                Motif motif = Motif.fromAprilTagId(tag.getId());
                label += " (" + motif.getPattern() + ")";
            }

            Imgproc.putText(frame, label,
                    new Point(tag.getCenter().x - 30, tag.getCenter().y - 20),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2);
        }
    }

    /**
     * 根據 ID 取得顯示顏色
     */
    private Scalar getColorForId(int id) {
        return switch (id) {
            case 21 -> new Scalar(0, 255, 0); // GPP: 綠色
            case 22 -> new Scalar(255, 0, 255); // PGP: 紫色
            case 23 -> new Scalar(255, 255, 0); // PPG: 青色
            default -> new Scalar(255, 255, 255); // 白色
        };
    }

    /**
     * 取得最後偵測到的主題
     */
    public Motif getLastDetectedMotif() {
        return lastDetectedMotif;
    }

    /**
     * 取得最後偵測時間
     */
    public long getLastDetectionTime() {
        return lastDetectionTime;
    }

    /**
     * 是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 重置偵測狀態
     */
    public void reset() {
        lastDetectedMotif = Motif.UNKNOWN;
        lastDetectionTime = 0;
        motifCounts = new int[4];
        log.info("AprilTag 偵測器已重置");
    }

    /**
     * 釋放資源
     */
    public void release() {
        // ArucoDetector 不需要手動釋放
        log.info("AprilTag 偵測器資源已釋放");
    }
}
