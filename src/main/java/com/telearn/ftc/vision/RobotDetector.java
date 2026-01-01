package com.telearn.ftc.vision;

import com.telearn.ftc.model.Alliance;
import com.telearn.ftc.model.DetectedRobot;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 機器人偵測器 - 使用自適應模板匹配進行追蹤
 */
@Slf4j
public class RobotDetector {

    // ===== 追蹤參數 =====
    private static final int SEARCH_RADIUS = 300; // 搜尋半徑
    private static final int TRAIL_LENGTH = 50;
    private static final int NUM_ROBOTS = 4;
    private static final double MATCH_THRESHOLD = 0.4; // 匹配閾值
    private static final double TEMPLATE_UPDATE_RATE = 0.3; // 模板更新率

    private int nextRobotId = 1;

    // 可重用的 Mat 物件
    private Mat resultMat = new Mat();

    // 是否已初始化追蹤
    private boolean isInitialized = false;

    // 追蹤中的機器人
    private List<TrackedRobot> trackedRobots = new ArrayList<>();

    // 追蹤歷史
    private Map<Integer, List<Point>> trackingHistory = new HashMap<>();

    // 追蹤顏色
    private static final Scalar[] ROBOT_COLORS = {
            new Scalar(0, 255, 255), // 黃
            new Scalar(255, 0, 255), // 紫
            new Scalar(0, 255, 0), // 綠
            new Scalar(255, 165, 0) // 橙
    };

    // 用於保存初始幀
    private Mat templateFrame = null;

    public RobotDetector() {
        log.info("機器人偵測器初始化完成");
    }

    /**
     * 追蹤中的機器人資料 (包含自適應模板和聯盟)
     */
    private static class TrackedRobot {
        int id;
        Alliance alliance; // 所屬聯盟
        Rect boundingBox;
        Point center;
        Mat template;
        double lastMatchScore = 1.0;
        int lostFrameCount = 0;

        TrackedRobot(int id, Alliance alliance, Rect box, Mat template) {
            this.id = id;
            this.alliance = alliance;
            this.boundingBox = box;
            this.center = new Point(box.x + box.width / 2.0, box.y + box.height / 2.0);
            this.template = template;
        }

        void updatePosition(Rect newBox) {
            this.boundingBox = newBox;
            this.center = new Point(newBox.x + newBox.width / 2.0, newBox.y + newBox.height / 2.0);
        }

        void updateTemplate(Mat newTemplate, double updateRate) {
            if (template != null && newTemplate != null &&
                    template.size().equals(newTemplate.size()) && template.type() == newTemplate.type()) {
                Core.addWeighted(template, 1.0 - updateRate, newTemplate, updateRate, 0, template);
            }
        }

        void release() {
            if (template != null) {
                template.release();
            }
        }
    }

    /**
     * 設定模板幀 - 在標記機器人之前必須先設定
     */
    public void setTemplateFrame(Mat frame) {
        if (templateFrame != null) {
            templateFrame.release();
        }
        templateFrame = frame.clone();
        log.info("已設定模板幀");
    }

    /**
     * 重置追蹤
     */
    public void resetTracking() {
        isInitialized = false;
        for (TrackedRobot robot : trackedRobots) {
            robot.release();
        }
        trackedRobots.clear();
        trackingHistory.clear();
        nextRobotId = 1;
        if (templateFrame != null) {
            templateFrame.release();
            templateFrame = null;
        }
        log.info("追蹤已重置");
    }

    /**
     * 手動加入機器人 (點擊位置) - 從模板幀提取模板
     * 
     * @param clickX    點擊 X 座標
     * @param clickY    點擊 Y 座標
     * @param robotSize 機器人框大小
     * @param alliance  所屬聯盟 (RED 或 BLUE)
     */
    public boolean addManualRobot(int clickX, int clickY, int robotSize, Alliance alliance) {
        if (trackedRobots.size() >= NUM_ROBOTS) {
            log.warn("已達最大機器人數量 ({})", NUM_ROBOTS);
            return false;
        }

        if (templateFrame == null || templateFrame.empty()) {
            log.error("尚未設定模板幀");
            return false;
        }

        // 建立以點擊位置為中心的邊界框
        int halfSize = robotSize / 2;
        int x = Math.max(0, clickX - halfSize);
        int y = Math.max(0, clickY - halfSize);
        int w = Math.min(robotSize, templateFrame.cols() - x);
        int h = Math.min(robotSize, templateFrame.rows() - y);

        Rect box = new Rect(x, y, w, h);

        // 提取模板
        Mat template = new Mat(templateFrame, box).clone();

        TrackedRobot robot = new TrackedRobot(nextRobotId++, alliance, box, template);
        trackedRobots.add(robot);
        trackingHistory.put(robot.id, new ArrayList<>());
        trackingHistory.get(robot.id).add(robot.center);

        if (trackedRobots.size() >= NUM_ROBOTS) {
            isInitialized = true;
        }

        log.info("手動加入 {} Robot #{} 於位置 ({}, {}), 目前共 {} 台",
                alliance.getDisplayName(), robot.id, clickX, clickY, trackedRobots.size());
        return true;
    }

    /**
     * 取得目前標記的機器人數量
     */
    public int getMarkedRobotCount() {
        return trackedRobots.size();
    }

    /**
     * 是否已初始化
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * 偵測並追蹤機器人 - 使用自適應模板匹配
     */
    public List<DetectedRobot> detect(Mat frame) {
        List<DetectedRobot> robots = new ArrayList<>();

        if (frame == null || frame.empty()) {
            return robots;
        }

        if (trackedRobots.isEmpty()) {
            return robots;
        }

        // 對每個追蹤中的機器人進行模板匹配
        for (TrackedRobot tracked : trackedRobots) {
            // 定義擴大的搜尋區域
            int searchX = (int) Math.max(0, tracked.center.x - SEARCH_RADIUS);
            int searchY = (int) Math.max(0, tracked.center.y - SEARCH_RADIUS);
            int searchW = Math.min(SEARCH_RADIUS * 2, frame.cols() - searchX);
            int searchH = Math.min(SEARCH_RADIUS * 2, frame.rows() - searchY);

            if (searchW < tracked.template.cols() || searchH < tracked.template.rows()) {
                // 搜尋區域太小
                tracked.lostFrameCount++;
                DetectedRobot robot = new DetectedRobot(tracked.alliance, tracked.boundingBox, 0.3);
                robot.setId(tracked.id);
                robots.add(robot);
                continue;
            }

            Rect searchROI = new Rect(searchX, searchY, searchW, searchH);
            Mat searchRegion = new Mat(frame, searchROI);

            // 模板匹配
            Imgproc.matchTemplate(searchRegion, tracked.template, resultMat, Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult mmr = Core.minMaxLoc(resultMat);

            tracked.lastMatchScore = mmr.maxVal;

            // 更新位置
            if (mmr.maxVal > MATCH_THRESHOLD) {
                int newX = searchX + (int) mmr.maxLoc.x;
                int newY = searchY + (int) mmr.maxLoc.y;
                Rect newBox = new Rect(newX, newY, tracked.template.cols(), tracked.template.rows());
                tracked.updatePosition(newBox);
                tracked.lostFrameCount = 0;

                // 自適應更新模板 (只在匹配分數高時更新)
                if (mmr.maxVal > 0.6) {
                    Mat newTemplate = new Mat(frame, newBox);
                    tracked.updateTemplate(newTemplate, TEMPLATE_UPDATE_RATE);
                    newTemplate.release();
                }

                // 更新軌跡
                List<Point> trail = trackingHistory.get(tracked.id);
                if (trail != null) {
                    trail.add(tracked.center);
                    while (trail.size() > TRAIL_LENGTH) {
                        trail.remove(0);
                    }
                }
            } else {
                tracked.lostFrameCount++;
            }

            searchRegion.release();

            // 建立 DetectedRobot
            DetectedRobot robot = new DetectedRobot(tracked.alliance, tracked.boundingBox, mmr.maxVal);
            robot.setId(tracked.id);
            robots.add(robot);
        }

        return robots;
    }

    /**
     * 取得機器人軌跡
     */
    public List<Point> getTrail(int robotId) {
        return trackingHistory.getOrDefault(robotId, new ArrayList<>());
    }

    /**
     * 在影像上繪製偵測結果 (含軌跡)
     */
    public void drawDetections(Mat frame, List<DetectedRobot> robots) {
        for (DetectedRobot robot : robots) {
            Rect box = robot.getBoundingBox();
            // 根據聯盟決定顏色 (BGR 格式: Red = (0,0,255), Blue = (255,0,0))
            Scalar color = robot.getAlliance() == Alliance.RED
                    ? new Scalar(0, 0, 255) // 紅色
                    : new Scalar(255, 0, 0); // 藍色

            // 繪製軌跡
            List<Point> trail = getTrail(robot.getId());
            if (trail.size() > 1) {
                for (int i = 1; i < trail.size(); i++) {
                    double alpha = (double) i / trail.size();
                    Scalar trailColor = new Scalar(
                            color.val[0] * alpha,
                            color.val[1] * alpha,
                            color.val[2] * alpha);
                    int thickness = Math.max(1, (int) (3 * alpha));
                    Imgproc.line(frame, trail.get(i - 1), trail.get(i), trailColor, thickness);
                }
            }

            // 繪製矩形框
            Imgproc.rectangle(frame, box, color, 3);

            // 繪製標籤 - 顯示聯盟和編號
            String allianceName = robot.getAlliance() == Alliance.RED ? "紅" : "藍";
            String label = String.format("%s #%d", allianceName, robot.getId());
            Imgproc.putText(frame, label,
                    new Point(box.x, box.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2);

            // 繪製中心點
            if (robot.getCenter() != null) {
                Imgproc.circle(frame, robot.getCenter(), 8, color, -1);
            }
        }
    }

    /**
     * 釋放資源
     */
    public void release() {
        resultMat.release();
        if (templateFrame != null) {
            templateFrame.release();
        }
        for (TrackedRobot robot : trackedRobots) {
            robot.release();
        }
        trackingHistory.clear();
        trackedRobots.clear();
        log.info("機器人偵測器資源已釋放");
    }
}
