package com.telearn.ftc.vision;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * 場地區域偵測器
 * 識別 FTC 場地上的關鍵區域
 */
@Slf4j
public class ZoneDetector {

    /**
     * 區域定義
     */
    @Data
    public static class Zone {
        private String name;
        private String nameEn;
        private Rect bounds;
        private Scalar displayColor;

        public Zone(String name, String nameEn, Rect bounds, Scalar displayColor) {
            this.name = name;
            this.nameEn = nameEn;
            this.bounds = bounds;
            this.displayColor = displayColor;
        }

        public boolean contains(Point point) {
            return point.x >= bounds.x && point.x <= bounds.x + bounds.width
                    && point.y >= bounds.y && point.y <= bounds.y + bounds.height;
        }
    }

    // 場地尺寸 (像素，假設 640x480 解析度)
    private int fieldWidth = 640;
    private int fieldHeight = 480;

    // 預定義區域
    private List<Zone> zones = new ArrayList<>();

    // 是否已完成校準
    private boolean calibrated = false;

    // 透視變換矩陣
    private Mat perspectiveMatrix;

    public ZoneDetector() {
        initializeDefaultZones();
        log.info("區域偵測器初始化完成");
    }

    /**
     * 初始化預設區域 (基於比例)
     */
    private void initializeDefaultZones() {
        // 以下為基於 6x6 地墊的比例估算
        // 實際使用時需要透過校準來調整

        // 裝載區 (Loading Zone) - 兩個角落
        zones.add(new Zone("紅方裝載區", "Red Loading Zone",
                new Rect(0, fieldHeight - 100, 100, 100),
                new Scalar(0, 0, 200))); // 深紅

        zones.add(new Zone("藍方裝載區", "Blue Loading Zone",
                new Rect(fieldWidth - 100, fieldHeight - 100, 100, 100),
                new Scalar(200, 0, 0))); // 深藍

        // 發射區 (Launch Zone) - 場地中央偏上
        zones.add(new Zone("發射區", "Launch Zone",
                new Rect(fieldWidth / 4, 50, fieldWidth / 2, 150),
                new Scalar(0, 200, 200))); // 黃色

        // 坡道區 (Ramp Area) - 場地頂端中央
        zones.add(new Zone("坡道區", "Ramp Area",
                new Rect(fieldWidth / 3, 0, fieldWidth / 3, 80),
                new Scalar(0, 150, 0))); // 綠色

        // 聯盟區域 (Alliance Area) - 兩側
        zones.add(new Zone("紅方聯盟區", "Red Alliance Area",
                new Rect(0, 100, fieldWidth / 3, fieldHeight - 200),
                new Scalar(100, 100, 255))); // 淺紅

        zones.add(new Zone("藍方聯盟區", "Blue Alliance Area",
                new Rect(fieldWidth * 2 / 3, 100, fieldWidth / 3, fieldHeight - 200),
                new Scalar(255, 100, 100))); // 淺藍

        // 中立區 (Neutral Zone) - 場中央
        zones.add(new Zone("中立區", "Neutral Zone",
                new Rect(fieldWidth / 3, fieldHeight / 3, fieldWidth / 3, fieldHeight / 3),
                new Scalar(200, 200, 200))); // 灰色

        // 基地區 (Base Area) - 停車區域
        zones.add(new Zone("紅方基地", "Red Base",
                new Rect(0, fieldHeight - 80, 120, 80),
                new Scalar(50, 50, 180)));

        zones.add(new Zone("藍方基地", "Blue Base",
                new Rect(fieldWidth - 120, fieldHeight - 80, 120, 80),
                new Scalar(180, 50, 50)));
    }

    /**
     * 更新場地尺寸 (根據影像解析度)
     */
    public void updateFieldSize(int width, int height) {
        this.fieldWidth = width;
        this.fieldHeight = height;
        zones.clear();
        initializeDefaultZones();
        log.info("場地尺寸更新為 {}x{}", width, height);
    }

    /**
     * 取得點所在的區域
     */
    public Zone getZoneAt(Point point) {
        for (Zone zone : zones) {
            if (zone.contains(point)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * 取得所有區域
     */
    public List<Zone> getAllZones() {
        return new ArrayList<>(zones);
    }

    /**
     * 在影像上繪製區域
     */
    public void drawZones(Mat frame, boolean showLabels) {
        for (Zone zone : zones) {
            // 繪製半透明區域
            Mat overlay = frame.clone();
            Imgproc.rectangle(overlay, zone.getBounds(), zone.getDisplayColor(), -1);
            Core.addWeighted(overlay, 0.2, frame, 0.8, 0, frame);
            overlay.release();

            // 繪製邊框
            Imgproc.rectangle(frame, zone.getBounds(), zone.getDisplayColor(), 1);

            if (showLabels) {
                // 繪製標籤
                Point labelPos = new Point(
                        zone.getBounds().x + 5,
                        zone.getBounds().y + 15);
                Imgproc.putText(frame, zone.getName(), labelPos,
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, zone.getDisplayColor(), 1);
            }
        }
    }

    /**
     * 校準場地區域
     * 
     * @param cornerPoints 場地四角點 (順時針，從左上開始)
     */
    public void calibrate(Point[] cornerPoints) {
        if (cornerPoints.length != 4) {
            log.error("校準需要 4 個角點");
            return;
        }

        // 計算透視變換矩陣
        MatOfPoint2f srcPoints = new MatOfPoint2f(cornerPoints);
        MatOfPoint2f dstPoints = new MatOfPoint2f(
                new Point(0, 0),
                new Point(fieldWidth, 0),
                new Point(fieldWidth, fieldHeight),
                new Point(0, fieldHeight));

        perspectiveMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints);
        calibrated = true;

        log.info("場地校準完成");
        srcPoints.release();
        dstPoints.release();
    }

    /**
     * 應用透視變換
     */
    public Mat applyPerspective(Mat frame) {
        if (!calibrated || perspectiveMatrix == null) {
            return frame;
        }

        Mat warped = new Mat();
        Imgproc.warpPerspective(frame, warped, perspectiveMatrix,
                new Size(fieldWidth, fieldHeight));
        return warped;
    }

    /**
     * 是否已校準
     */
    public boolean isCalibrated() {
        return calibrated;
    }

    /**
     * 手動設定區域
     */
    public void setZone(String name, Rect bounds) {
        for (Zone zone : zones) {
            if (zone.getName().equals(name) || zone.getNameEn().equals(name)) {
                zone.setBounds(bounds);
                log.info("更新區域 {} 範圍: {}", name, bounds);
                return;
            }
        }
        log.warn("找不到區域: {}", name);
    }

    /**
     * 釋放資源
     */
    public void release() {
        if (perspectiveMatrix != null) {
            perspectiveMatrix.release();
        }
        log.info("區域偵測器資源已釋放");
    }
}
