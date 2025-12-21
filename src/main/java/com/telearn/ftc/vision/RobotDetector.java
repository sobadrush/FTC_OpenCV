package com.telearn.ftc.vision;

import com.telearn.ftc.model.Alliance;
import com.telearn.ftc.model.DetectedRobot;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * 機器人偵測器
 * 根據聯盟顏色 (紅/藍) 偵測比賽場上的機器人
 */
@Slf4j
public class RobotDetector {

    // ===== 紅色聯盟 HSV 範圍 =====
    // 紅色跨越 0 度，需要兩個範圍
    private static final Scalar RED_LOWER_1 = new Scalar(0, 100, 100);
    private static final Scalar RED_UPPER_1 = new Scalar(10, 255, 255);
    private static final Scalar RED_LOWER_2 = new Scalar(160, 100, 100);
    private static final Scalar RED_UPPER_2 = new Scalar(180, 255, 255);

    // ===== 藍色聯盟 HSV 範圍 =====
    private static final Scalar BLUE_LOWER = new Scalar(100, 100, 100);
    private static final Scalar BLUE_UPPER = new Scalar(130, 255, 255);

    // ===== 偵測參數 =====
    /** 最小機器人面積 (像素平方) */
    private static final double MIN_ROBOT_AREA = 5000;

    /** 最大機器人面積 */
    private static final double MAX_ROBOT_AREA = 100000;

    /** 信心度閾值 */
    private static final double CONFIDENCE_THRESHOLD = 0.5;

    // 可重用的 Mat 物件
    private Mat hsvMat = new Mat();
    private Mat redMask1 = new Mat();
    private Mat redMask2 = new Mat();
    private Mat redMask = new Mat();
    private Mat blueMask = new Mat();
    private Mat morphKernel;

    private int nextRobotId = 1;

    public RobotDetector() {
        morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7));
        log.info("機器人偵測器初始化完成");
    }

    /**
     * 偵測影像中的所有機器人
     * 
     * @param frame BGR 格式的影像
     * @return 偵測到的機器人列表
     */
    public List<DetectedRobot> detect(Mat frame) {
        List<DetectedRobot> robots = new ArrayList<>();

        if (frame == null || frame.empty()) {
            return robots;
        }

        // 轉換到 HSV 顏色空間
        Imgproc.cvtColor(frame, hsvMat, Imgproc.COLOR_BGR2HSV);

        // 偵測紅色聯盟機器人
        Core.inRange(hsvMat, RED_LOWER_1, RED_UPPER_1, redMask1);
        Core.inRange(hsvMat, RED_LOWER_2, RED_UPPER_2, redMask2);
        Core.bitwise_or(redMask1, redMask2, redMask);
        List<DetectedRobot> redRobots = detectFromMask(redMask, Alliance.RED);
        robots.addAll(redRobots);

        // 偵測藍色聯盟機器人
        Core.inRange(hsvMat, BLUE_LOWER, BLUE_UPPER, blueMask);
        List<DetectedRobot> blueRobots = detectFromMask(blueMask, Alliance.BLUE);
        robots.addAll(blueRobots);

        return robots;
    }

    /**
     * 從遮罩中偵測機器人
     */
    private List<DetectedRobot> detectFromMask(Mat mask, Alliance alliance) {
        List<DetectedRobot> robots = new ArrayList<>();

        // 型態學運算 - 去除雜訊並填充空隙
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, morphKernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, morphKernel);
        Imgproc.dilate(mask, mask, morphKernel);

        // 尋找輪廓
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // 只保留最大的兩個輪廓 (每個聯盟最多 2 台機器人)
        List<ContourInfo> contourInfos = new ArrayList<>();

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);

            if (area >= MIN_ROBOT_AREA && area <= MAX_ROBOT_AREA) {
                contourInfos.add(new ContourInfo(contour, area));
            }
        }

        // 按面積排序，取最大的兩個
        contourInfos.sort((a, b) -> Double.compare(b.area, a.area));
        int maxRobots = Math.min(2, contourInfos.size());

        for (int i = 0; i < maxRobots; i++) {
            ContourInfo info = contourInfos.get(i);
            Rect boundingBox = Imgproc.boundingRect(info.contour);

            // 計算信心度
            double confidence = calculateConfidence(info.area, boundingBox);

            if (confidence >= CONFIDENCE_THRESHOLD) {
                DetectedRobot robot = new DetectedRobot(alliance, boundingBox, confidence);
                robot.setId(nextRobotId++);
                robots.add(robot);
            }
        }

        hierarchy.release();

        return robots;
    }

    /**
     * 計算偵測信心度
     */
    private double calculateConfidence(double area, Rect boundingBox) {
        // 面積權重
        double idealArea = 20000;
        double areaScore = 1.0 - Math.min(1.0, Math.abs(area - idealArea) / idealArea * 0.5);

        // 形狀權重 (機器人通常接近正方形)
        double aspectRatio = (double) boundingBox.width / boundingBox.height;
        double aspectScore = 1.0 - Math.min(1.0, Math.abs(aspectRatio - 1.0) * 0.5);

        return areaScore * 0.6 + aspectScore * 0.4;
    }

    /**
     * 在影像上繪製偵測結果
     */
    public void drawDetections(Mat frame, List<DetectedRobot> robots) {
        for (DetectedRobot robot : robots) {
            Rect box = robot.getBoundingBox();
            Scalar color = robot.getAlliance() == Alliance.RED
                    ? new Scalar(0, 0, 255) // 紅色 (BGR)
                    : new Scalar(255, 0, 0); // 藍色 (BGR)

            // 繪製矩形框
            Imgproc.rectangle(frame, box, color, 3);

            // 繪製標籤
            String label = String.format("%s #%d %.0f%%",
                    robot.getAlliance().name(),
                    robot.getId(),
                    robot.getConfidence() * 100);
            Imgproc.putText(frame, label,
                    new Point(box.x, box.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2);

            // 繪製中心點
            if (robot.getCenter() != null) {
                Imgproc.circle(frame, robot.getCenter(), 5, color, -1);
            }
        }
    }

    /**
     * 釋放資源
     */
    public void release() {
        hsvMat.release();
        redMask1.release();
        redMask2.release();
        redMask.release();
        blueMask.release();
        morphKernel.release();
        log.info("機器人偵測器資源已釋放");
    }

    /**
     * 輔助類別：儲存輪廓資訊
     */
    private static class ContourInfo {
        MatOfPoint contour;
        double area;

        ContourInfo(MatOfPoint contour, double area) {
            this.contour = contour;
            this.area = area;
        }
    }
}
