package com.telearn.ftc.vision;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * OCR 讀取轉播畫面上的分數
 * 從畫面底部偵測:
 * - 紅方分數 (Alliance 1)
 * - 藍方分數 (Alliance 3)
 * - 計時器
 * - 主題指示器
 */
@Slf4j
public class ScoreOCR {

    // 分數區域 (相對於畫面的比例)
    private static final double SCORE_BAR_TOP = 0.85; // 分數列頂部位置 (佔畫面高度的百分比)
    private static final double SCORE_BAR_HEIGHT = 0.10; // 分數列高度

    private static final double RED_SCORE_LEFT = 0.20;
    private static final double RED_SCORE_WIDTH = 0.10;

    private static final double BLUE_SCORE_LEFT = 0.70;
    private static final double BLUE_SCORE_WIDTH = 0.10;

    private static final double TIMER_LEFT = 0.45;
    private static final double TIMER_WIDTH = 0.10;

    // 最後偵測到的分數
    private int lastRedScore = -1;
    private int lastBlueScore = -1;
    private String lastTimer = "";

    public ScoreOCR() {
        log.info("ScoreOCR 初始化完成");
    }

    /**
     * 從畫面偵測分數
     */
    public void detect(Mat frame) {
        int height = frame.rows();
        int width = frame.cols();

        // 取得分數列區域
        int barTop = (int) (height * SCORE_BAR_TOP);
        int barHeight = (int) (height * SCORE_BAR_HEIGHT);

        // 紅方分數區域
        Rect redRegion = new Rect(
                (int) (width * RED_SCORE_LEFT),
                barTop,
                (int) (width * RED_SCORE_WIDTH),
                barHeight);

        // 藍方分數區域
        Rect blueRegion = new Rect(
                (int) (width * BLUE_SCORE_LEFT),
                barTop,
                (int) (width * BLUE_SCORE_WIDTH),
                barHeight);

        // 計時器區域
        Rect timerRegion = new Rect(
                (int) (width * TIMER_LEFT),
                barTop,
                (int) (width * TIMER_WIDTH),
                barHeight);

        // 確保區域在範圍內
        redRegion = clampRect(redRegion, width, height);
        blueRegion = clampRect(blueRegion, width, height);
        timerRegion = clampRect(timerRegion, width, height);

        // 繪製偵測區域 (除錯用)
        Imgproc.rectangle(frame, redRegion, new Scalar(0, 0, 255), 1);
        Imgproc.rectangle(frame, blueRegion, new Scalar(255, 0, 0), 1);
        Imgproc.rectangle(frame, timerRegion, new Scalar(0, 255, 0), 1);

        // TODO: 使用 Tesseract OCR 或數字模板匹配來讀取分數
        // 目前只繪製區域，實際 OCR 功能待實作
    }

    /**
     * 確保 Rect 在有效範圍內
     */
    private Rect clampRect(Rect rect, int maxWidth, int maxHeight) {
        int x = Math.max(0, Math.min(rect.x, maxWidth - 1));
        int y = Math.max(0, Math.min(rect.y, maxHeight - 1));
        int w = Math.min(rect.width, maxWidth - x);
        int h = Math.min(rect.height, maxHeight - y);
        return new Rect(x, y, Math.max(1, w), Math.max(1, h));
    }

    /**
     * 取得紅方 OCR 分數
     */
    public int getRedScore() {
        return lastRedScore;
    }

    /**
     * 取得藍方 OCR 分數
     */
    public int getBlueScore() {
        return lastBlueScore;
    }

    /**
     * 取得計時器
     */
    public String getTimer() {
        return lastTimer;
    }

    /**
     * 是否有有效的 OCR 結果
     */
    public boolean hasValidResults() {
        return lastRedScore >= 0 && lastBlueScore >= 0;
    }
}
