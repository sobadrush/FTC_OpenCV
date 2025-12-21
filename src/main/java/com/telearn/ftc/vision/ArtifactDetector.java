package com.telearn.ftc.vision;

import com.telearn.ftc.model.ArtifactType;
import com.telearn.ftc.model.DetectedArtifact;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * 文物偵測器
 * 使用 HSV 顏色空間辨識紫色與綠色文物
 */
@Slf4j
public class ArtifactDetector {

    // ===== 紫色文物 HSV 範圍 =====
    // 紫色在 HSV 中的色相 (Hue) 約為 130-160
    private static final Scalar PURPLE_LOWER = new Scalar(130, 50, 50);
    private static final Scalar PURPLE_UPPER = new Scalar(165, 255, 255);

    // ===== 綠色文物 HSV 範圍 =====
    // 綠色在 HSV 中的色相 (Hue) 約為 40-80
    private static final Scalar GREEN_LOWER = new Scalar(35, 50, 50);
    private static final Scalar GREEN_UPPER = new Scalar(85, 255, 255);

    // ===== 偵測參數 =====
    /** 最小輪廓面積 (像素平方) */
    private static final double MIN_CONTOUR_AREA = 500;

    /** 最大輪廓面積 */
    private static final double MAX_CONTOUR_AREA = 50000;

    /** 最小圓形度 (0-1, 1=完美圓形) */
    private static final double MIN_CIRCULARITY = 0.3;

    /** 信心度閾值 */
    private static final double CONFIDENCE_THRESHOLD = 0.6;

    // 可重用的 Mat 物件 (避免頻繁記憶體分配)
    private Mat hsvMat = new Mat();
    private Mat purpleMask = new Mat();
    private Mat greenMask = new Mat();
    private Mat morphKernel;

    private int nextArtifactId = 1;

    public ArtifactDetector() {
        // 建立型態學運算的核心
        morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        log.info("文物偵測器初始化完成");
    }

    /**
     * 偵測影像中的所有文物
     * 
     * @param frame BGR 格式的影像
     * @return 偵測到的文物列表
     */
    public List<DetectedArtifact> detect(Mat frame) {
        List<DetectedArtifact> artifacts = new ArrayList<>();

        if (frame == null || frame.empty()) {
            return artifacts;
        }

        // 轉換到 HSV 顏色空間
        Imgproc.cvtColor(frame, hsvMat, Imgproc.COLOR_BGR2HSV);

        // 偵測紫色文物
        Core.inRange(hsvMat, PURPLE_LOWER, PURPLE_UPPER, purpleMask);
        List<DetectedArtifact> purpleArtifacts = detectFromMask(purpleMask, ArtifactType.PURPLE);
        artifacts.addAll(purpleArtifacts);

        // 偵測綠色文物
        Core.inRange(hsvMat, GREEN_LOWER, GREEN_UPPER, greenMask);
        List<DetectedArtifact> greenArtifacts = detectFromMask(greenMask, ArtifactType.GREEN);
        artifacts.addAll(greenArtifacts);

        return artifacts;
    }

    /**
     * 從遮罩中偵測文物
     */
    private List<DetectedArtifact> detectFromMask(Mat mask, ArtifactType type) {
        List<DetectedArtifact> artifacts = new ArrayList<>();

        // 型態學運算 - 去除雜訊
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, morphKernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, morphKernel);

        // 尋找輪廓
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);

            // 過濾太小或太大的輪廓
            if (area < MIN_CONTOUR_AREA || area > MAX_CONTOUR_AREA) {
                continue;
            }

            // 計算圓形度
            double perimeter = Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true);
            double circularity = 4 * Math.PI * area / (perimeter * perimeter);

            if (circularity < MIN_CIRCULARITY) {
                continue;
            }

            // 取得邊界矩形
            Rect boundingBox = Imgproc.boundingRect(contour);

            // 計算信心度 (基於圓形度和面積)
            double confidence = calculateConfidence(circularity, area);

            if (confidence >= CONFIDENCE_THRESHOLD) {
                DetectedArtifact artifact = new DetectedArtifact(type, boundingBox, confidence);
                artifact.setId(nextArtifactId++);
                artifacts.add(artifact);
            }
        }

        hierarchy.release();

        return artifacts;
    }

    /**
     * 計算偵測信心度
     */
    private double calculateConfidence(double circularity, double area) {
        // 圓形度權重 (越圓越好)
        double circularityScore = Math.min(1.0, circularity / 0.8);

        // 面積權重 (中等大小最佳)
        double idealArea = 3000;
        double areaScore = 1.0 - Math.min(1.0, Math.abs(area - idealArea) / idealArea);

        return circularityScore * 0.7 + areaScore * 0.3;
    }

    /**
     * 在影像上繪製偵測結果
     */
    public void drawDetections(Mat frame, List<DetectedArtifact> artifacts) {
        for (DetectedArtifact artifact : artifacts) {
            Rect box = artifact.getBoundingBox();
            Scalar color = artifact.getType() == ArtifactType.PURPLE
                    ? new Scalar(255, 0, 255) // 紫色
                    : new Scalar(0, 255, 0); // 綠色

            // 繪製矩形框
            Imgproc.rectangle(frame, box, color, 2);

            // 繪製標籤
            String label = String.format("%s %.0f%%",
                    artifact.getType().getCode(),
                    artifact.getConfidence() * 100);
            Imgproc.putText(frame, label,
                    new Point(box.x, box.y - 5),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 1);
        }
    }

    /**
     * 更新 HSV 顏色範圍 (可用於校正)
     */
    public void updateColorRange(ArtifactType type, Scalar lower, Scalar upper) {
        log.info("更新 {} 顏色範圍: {} - {}", type, lower, upper);
        // 這裡可以擴展為動態更新
    }

    /**
     * 取得當前的遮罩 (用於除錯)
     */
    public Mat getPurpleMask() {
        return purpleMask.clone();
    }

    public Mat getGreenMask() {
        return greenMask.clone();
    }

    /**
     * 釋放資源
     */
    public void release() {
        hsvMat.release();
        purpleMask.release();
        greenMask.release();
        morphKernel.release();
        log.info("文物偵測器資源已釋放");
    }
}
