package com.telearn.ftc.vision;

import com.telearn.ftc.model.Motif;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * OCR 讀取轉播畫面上的分數
 * 從畫面底部偵測:
 * - 紅方分數 (Alliance 1) - 紅色背景上的白色數字
 * - 藍方分數 (Alliance 3) - 藍色背景上的白色數字
 * - 計時器 - 中間
 * - 主題指示器 - 底部彩色圓點
 */
@Slf4j
public class ScoreOCR {

    // 分數條區域 (相對於畫面的比例)
    // 基於 frame_111.jpg 分析:
    // - 分數條在畫面底部約 85%-95% 的位置
    // - 紅方分數約在寬度 30%-38%
    // - 藍方分數約在寬度 62%-70%
    // - 計時器約在寬度 45%-55%

    private static final double SCORE_BAR_TOP = 0.82;
    private static final double SCORE_BAR_HEIGHT = 0.08;

    // 紅方分數 "0" 的位置 (Alliance 1 的數字)
    private static final double RED_SCORE_LEFT = 0.335;
    private static final double RED_SCORE_WIDTH = 0.05;

    // 藍方分數 "0" 的位置 (Alliance 3 的數字)
    private static final double BLUE_SCORE_LEFT = 0.615;
    private static final double BLUE_SCORE_WIDTH = 0.05;

    // 計時器位置
    private static final double TIMER_LEFT = 0.455;
    private static final double TIMER_WIDTH = 0.09;

    // 主題指示器位置 (底部彩色圓點)
    private static final double MOTIF_TOP = 0.92;
    private static final double MOTIF_HEIGHT = 0.06;
    private static final double MOTIF_LEFT = 0.30;
    private static final double MOTIF_WIDTH = 0.10;

    // 偵測結果
    @Getter
    private int redScore = -1;
    @Getter
    private int blueScore = -1;
    @Getter
    private String timer = "";
    @Getter
    private Motif detectedMotif = Motif.UNKNOWN;

    // 是否顯示偵測區域 (除錯用)
    private boolean showDebugRegions = true;

    public ScoreOCR() {
        log.info("ScoreOCR 初始化完成");
    }

    /**
     * 從畫面偵測分數和主題
     */
    public void detect(Mat frame) {
        int height = frame.rows();
        int width = frame.cols();

        // 1. 定義偵測區域
        Rect redRegion = createRegion(width, height, RED_SCORE_LEFT, SCORE_BAR_TOP, RED_SCORE_WIDTH, SCORE_BAR_HEIGHT);
        Rect blueRegion = createRegion(width, height, BLUE_SCORE_LEFT, SCORE_BAR_TOP, BLUE_SCORE_WIDTH,
                SCORE_BAR_HEIGHT);
        Rect timerRegion = createRegion(width, height, TIMER_LEFT, SCORE_BAR_TOP, TIMER_WIDTH, SCORE_BAR_HEIGHT);
        Rect motifRegion = createRegion(width, height, MOTIF_LEFT, MOTIF_TOP, MOTIF_WIDTH, MOTIF_HEIGHT);

        // 2. 偵測分數
        redScore = detectScore(frame, redRegion);
        blueScore = detectScore(frame, blueRegion);

        // 3. 偵測計時器
        timer = detectTimer(frame, timerRegion);

        // 4. 偵測主題指示器
        detectedMotif = detectMotifIndicator(frame, motifRegion);

        // 5. 繪製偵測區域 (除錯用)
        if (showDebugRegions) {
            drawDebugInfo(frame, redRegion, blueRegion, timerRegion, motifRegion);
        }
    }

    /**
     * 建立偵測區域
     */
    private Rect createRegion(int width, int height, double left, double top, double w, double h) {
        int x = (int) (width * left);
        int y = (int) (height * top);
        int rw = (int) (width * w);
        int rh = (int) (height * h);

        // 確保在範圍內
        x = Math.max(0, Math.min(x, width - 1));
        y = Math.max(0, Math.min(y, height - 1));
        rw = Math.min(rw, width - x);
        rh = Math.min(rh, height - y);

        return new Rect(x, y, Math.max(1, rw), Math.max(1, rh));
    }

    /**
     * 使用白色像素計數來偵測分數
     * 數字越大，白色像素越多
     */
    private int detectScore(Mat frame, Rect region) {
        if (region.width <= 0 || region.height <= 0)
            return -1;

        try {
            Mat roi = new Mat(frame, region);
            Mat gray = new Mat();
            Mat binary = new Mat();

            // 轉灰階
            Imgproc.cvtColor(roi, gray, Imgproc.COLOR_BGR2GRAY);

            // 二值化找白色區域 (分數是白色的)
            Imgproc.threshold(gray, binary, 200, 255, Imgproc.THRESH_BINARY);

            // 計算白色像素數量
            int whitePixels = Core.countNonZero(binary);
            int totalPixels = region.width * region.height;
            double ratio = (double) whitePixels / totalPixels;

            // 根據白色像素比例估算數字
            // 這是簡化的方法，實際應使用模板匹配
            int estimatedScore = estimateScoreFromRatio(ratio);

            gray.release();
            binary.release();
            roi.release();

            return estimatedScore;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 根據白色像素比例估算分數
     */
    private int estimateScoreFromRatio(double ratio) {
        // 簡化的估算：
        // - 0: 約 8-12% 白色像素
        // - 1-9: 隨數字增加
        // - 10+: 兩位數有更多像素

        if (ratio < 0.05)
            return 0;
        if (ratio < 0.10)
            return (int) (ratio * 100);
        if (ratio < 0.15)
            return (int) (ratio * 150);
        return (int) (ratio * 200);
    }

    /**
     * 偵測計時器
     */
    private String detectTimer(Mat frame, Rect region) {
        // TODO: 實作計時器 OCR
        // 目前返回空字串
        return "";
    }

    /**
     * 偵測主題指示器 (底部彩色圓點)
     */
    private Motif detectMotifIndicator(Mat frame, Rect region) {
        if (region.width <= 0 || region.height <= 0)
            return Motif.UNKNOWN;

        try {
            Mat roi = new Mat(frame, region);
            Mat hsv = new Mat();
            Imgproc.cvtColor(roi, hsv, Imgproc.COLOR_BGR2HSV);

            // 偵測紫色和綠色圓點
            // 紫色: H = 140-160, S = 50-255, V = 50-255
            // 綠色: H = 35-85, S = 50-255, V = 50-255

            Mat purpleMask = new Mat();
            Mat greenMask = new Mat();

            Core.inRange(hsv, new Scalar(140, 50, 50), new Scalar(160, 255, 255), purpleMask);
            Core.inRange(hsv, new Scalar(35, 50, 50), new Scalar(85, 255, 255), greenMask);

            // 找輪廓來判斷順序
            List<MatOfPoint> purpleContours = new ArrayList<>();
            List<MatOfPoint> greenContours = new ArrayList<>();

            Imgproc.findContours(purpleMask, purpleContours, new Mat(),
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            Imgproc.findContours(greenMask, greenContours, new Mat(),
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // 根據圓點位置判斷主題
            // TODO: 更精確的主題偵測

            hsv.release();
            purpleMask.release();
            greenMask.release();
            roi.release();

        } catch (Exception e) {
            // ignore
        }

        return Motif.UNKNOWN;
    }

    /**
     * 繪製除錯資訊
     */
    private void drawDebugInfo(Mat frame, Rect redRegion, Rect blueRegion,
            Rect timerRegion, Rect motifRegion) {
        // 紅方分數區域 (紅框)
        Imgproc.rectangle(frame, redRegion, new Scalar(0, 0, 255), 2);
        Imgproc.putText(frame, "RED:" + redScore,
                new org.opencv.core.Point(redRegion.x, redRegion.y - 5),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 255), 1);

        // 藍方分數區域 (藍框)
        Imgproc.rectangle(frame, blueRegion, new Scalar(255, 0, 0), 2);
        Imgproc.putText(frame, "BLUE:" + blueScore,
                new org.opencv.core.Point(blueRegion.x, blueRegion.y - 5),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 0, 0), 1);

        // 計時器區域 (綠框)
        Imgproc.rectangle(frame, timerRegion, new Scalar(0, 255, 0), 2);

        // 主題指示器區域 (黃框)
        Imgproc.rectangle(frame, motifRegion, new Scalar(0, 255, 255), 2);
    }

    /**
     * 設定是否顯示除錯區域
     */
    public void setShowDebugRegions(boolean show) {
        this.showDebugRegions = show;
    }

    /**
     * 是否有有效的 OCR 結果
     */
    public boolean hasValidResults() {
        return redScore >= 0 && blueScore >= 0;
    }
}
