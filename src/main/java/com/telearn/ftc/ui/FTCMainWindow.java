package com.telearn.ftc.ui;

import com.telearn.ftc.model.*;
import com.telearn.ftc.scoring.ScoringEngine;
import com.telearn.ftc.vision.*;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * FTC 比賽分析器主視窗 (重構版)
 * 專注於：
 * 1. 文物偵測與計分
 * 2. OCR 讀取轉播畫面分數驗證
 * 3. 斜坡狀態追蹤
 */
@Slf4j
public class FTCMainWindow extends JFrame {

    // OpenCV 載入
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    // ===== UI 元件 =====
    private JLabel videoLabel;
    private JTextArea eventLogArea;
    private JLabel timeLabel;

    // 分數面板
    private JLabel redScoreLabel; // 紅方計算分數
    private JLabel redOcrLabel; // 紅方 OCR 分數
    private JLabel blueScoreLabel; // 藍方計算分數
    private JLabel blueOcrLabel; // 藍方 OCR 分數
    private JLabel validationLabel; // 驗證狀態

    // 斜坡狀態面板
    private JPanel redRampPanel; // 紅方斜坡 (9格)
    private JPanel blueRampPanel; // 藍方斜坡 (9格)
    private JLabel redPatternLabel; // 紅方圖案匹配
    private JLabel bluePatternLabel; // 藍方圖案匹配
    private JLabel motifLabel; // 主題顯示

    // ===== 視訊相關 =====
    private VideoCapture camera;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile boolean isSeeking = false;
    private Thread captureThread;
    private String currentVideoPath = null;
    private int totalFrames = 0;
    private int currentFrame = 0;
    private final Object videoLock = new Object();

    // ===== 影片控制元件 =====
    private JSlider videoSlider;
    private JButton playPauseBtn;
    private JLabel frameInfoLabel;

    // ===== 偵測器 =====
    private ArtifactDetector artifactDetector;
    private AprilTagDetector aprilTagDetector;
    private ScoreOCR scoreOCR; // 新增：OCR 讀取器

    // ===== 分析引擎 =====
    private ScoringEngine scoringEngine;

    // ===== 斜坡狀態 =====
    private RampState redRamp;
    private RampState blueRamp;
    private Motif currentMotif = Motif.UNKNOWN;

    // ===== 球門區域 =====
    private GoalArea redGoal;
    private GoalArea blueGoal;

    // 視窗尺寸
    private static final int VIDEO_WIDTH = 960;
    private static final int VIDEO_HEIGHT = 540;

    public FTCMainWindow() {
        initializeComponents();
        initializeUI();
        log.info("FTC 比賽分析器啟動");
    }

    /**
     * 初始化元件
     */
    private void initializeComponents() {
        // 偵測器
        artifactDetector = new ArtifactDetector();
        aprilTagDetector = new AprilTagDetector();
        scoreOCR = new ScoreOCR();

        // 計分引擎
        scoringEngine = new ScoringEngine();

        // 斜坡狀態
        redRamp = new RampState(Alliance.RED);
        blueRamp = new RampState(Alliance.BLUE);

        // 球門區域 (待 AprilTag 偵測後設定)
        redGoal = null;
        blueGoal = null;
    }

    /**
     * 初始化 UI
     */
    private void initializeUI() {
        setTitle("FTC 2025-2026 DECODE 自動計分系統");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 上方：影片控制
        mainPanel.add(createControlPanel(), BorderLayout.NORTH);

        // 中間：影片 + 資訊面板
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setLeftComponent(createVideoPanel());
        centerSplit.setRightComponent(createInfoPanel());
        centerSplit.setResizeWeight(0.75);
        mainPanel.add(centerSplit, BorderLayout.CENTER);

        // 下方：事件記錄
        mainPanel.add(createEventLogPanel(), BorderLayout.SOUTH);

        add(mainPanel);

        pack();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    /**
     * 建立控制面板
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        // 載入影片
        JButton loadBtn = new JButton("📂 載入影片");
        loadBtn.addActionListener(e -> loadFile());
        panel.add(loadBtn);

        // 播放/暫停
        playPauseBtn = new JButton("▶ 播放");
        playPauseBtn.setEnabled(false);
        playPauseBtn.addActionListener(e -> togglePlayPause());
        panel.add(playPauseBtn);

        // 進度條
        videoSlider = new JSlider(0, 100, 0);
        videoSlider.setPreferredSize(new Dimension(400, 30));
        videoSlider.setEnabled(false);
        videoSlider.addChangeListener(e -> {
            if (!videoSlider.getValueIsAdjusting())
                return;
            int targetFrame = videoSlider.getValue();
            seekToFrame(targetFrame);
        });
        panel.add(videoSlider);

        // 影格資訊
        frameInfoLabel = new JLabel("0 / 0");
        panel.add(frameInfoLabel);

        panel.add(new JSeparator(SwingConstants.VERTICAL));

        // 擷取畫面
        JButton captureBtn = new JButton("📷 擷取畫面");
        captureBtn.addActionListener(e -> captureImage());
        panel.add(captureBtn);

        return panel;
    }

    /**
     * 建立影片面板
     */
    private JPanel createVideoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("影片畫面"));

        videoLabel = new JLabel();
        videoLabel.setPreferredSize(new Dimension(VIDEO_WIDTH, VIDEO_HEIGHT));
        videoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        videoLabel.setBackground(Color.BLACK);
        videoLabel.setOpaque(true);
        videoLabel.setText("請載入影片檔案");

        panel.add(new JScrollPane(videoLabel), BorderLayout.CENTER);

        return panel;
    }

    /**
     * 建立資訊面板
     */
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));
        panel.setPreferredSize(new Dimension(300, 0));

        // 主題顯示
        JPanel motifPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        motifPanel.setBorder(BorderFactory.createTitledBorder("主題 (MOTIF)"));
        motifLabel = new JLabel("--");
        motifLabel.setFont(new Font("Monospaced", Font.BOLD, 24));
        motifPanel.add(motifLabel);
        panel.add(motifPanel);

        // 紅方分數
        panel.add(createAllianceScorePanel(Alliance.RED));
        panel.add(Box.createVerticalStrut(10));

        // 藍方分數
        panel.add(createAllianceScorePanel(Alliance.BLUE));
        panel.add(Box.createVerticalStrut(10));

        // 驗證狀態
        JPanel validPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        validPanel.setBorder(BorderFactory.createTitledBorder("驗證狀態"));
        validationLabel = new JLabel("等待中...");
        validationLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        validPanel.add(validationLabel);
        panel.add(validPanel);

        return panel;
    }

    /**
     * 建立聯盟分數面板
     */
    private JPanel createAllianceScorePanel(Alliance alliance) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        String title = alliance == Alliance.RED ? "紅方 (Alliance 1)" : "藍方 (Alliance 3)";
        Color bgColor = alliance == Alliance.RED ? new Color(255, 230, 230) : new Color(230, 230, 255);

        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setBackground(bgColor);

        // 分數行
        JPanel scoreRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        scoreRow.setOpaque(false);

        JLabel calcLabel = new JLabel("計算: ");
        JLabel scoreLabel = new JLabel("0");
        scoreLabel.setFont(new Font("SansSerif", Font.BOLD, 28));

        JLabel ocrLabelText = new JLabel("OCR: ");
        JLabel ocrLabel = new JLabel("--");
        ocrLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        ocrLabel.setForeground(Color.GRAY);

        scoreRow.add(calcLabel);
        scoreRow.add(scoreLabel);
        scoreRow.add(ocrLabelText);
        scoreRow.add(ocrLabel);
        panel.add(scoreRow);

        // 斜坡狀態
        JPanel rampRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
        rampRow.setOpaque(false);
        rampRow.setBorder(BorderFactory.createTitledBorder("斜坡 (0/9)"));

        // 9 個格子
        for (int i = 0; i < 9; i++) {
            JLabel slot = new JLabel();
            slot.setPreferredSize(new Dimension(25, 25));
            slot.setOpaque(true);
            slot.setBackground(Color.LIGHT_GRAY);
            slot.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            rampRow.add(slot);
        }
        panel.add(rampRow);

        // 圖案匹配
        JPanel patternRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        patternRow.setOpaque(false);
        JLabel patternLabel = new JLabel("圖案匹配: 0/3");
        patternRow.add(patternLabel);
        panel.add(patternRow);

        // 儲存參考
        if (alliance == Alliance.RED) {
            redScoreLabel = scoreLabel;
            redOcrLabel = ocrLabel;
            redRampPanel = rampRow;
            redPatternLabel = patternLabel;
        } else {
            blueScoreLabel = scoreLabel;
            blueOcrLabel = ocrLabel;
            blueRampPanel = rampRow;
            bluePatternLabel = patternLabel;
        }

        return panel;
    }

    /**
     * 建立事件記錄面板
     */
    private JPanel createEventLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("事件記錄"));
        panel.setPreferredSize(new Dimension(0, 120));

        eventLogArea = new JTextArea();
        eventLogArea.setEditable(false);
        eventLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(eventLogArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 載入檔案
     */
    private void loadFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "影片檔 (*.mp4, *.avi, *.mov)", "mp4", "avi", "mov"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            startVideo(file.getAbsolutePath());
        }
    }

    /**
     * 啟動影片
     */
    private void startVideo(String videoPath) {
        stopVideo();

        synchronized (videoLock) {
            camera = new VideoCapture(videoPath);
            if (!camera.isOpened()) {
                JOptionPane.showMessageDialog(this, "無法開啟影片: " + videoPath);
                return;
            }

            currentVideoPath = videoPath;
            totalFrames = (int) camera.get(Videoio.CAP_PROP_FRAME_COUNT);
            currentFrame = 0;

            videoSlider.setMaximum(totalFrames > 0 ? totalFrames - 1 : 0);
            videoSlider.setValue(0);
            videoSlider.setEnabled(true);
            playPauseBtn.setEnabled(true);

            isRunning = true;
            isPaused = true;
            playPauseBtn.setText("▶ 播放");

            captureThread = new Thread(this::captureLoop, "VideoCapture");
            captureThread.setDaemon(true);
            captureThread.start();

            addEventLog("已載入影片: " + new File(videoPath).getName());
            log.info("影片已載入: {}", videoPath);
        }
    }

    /**
     * 影像擷取迴圈
     */
    private void captureLoop() {
        Mat frame = new Mat();
        double fps = 30;

        synchronized (videoLock) {
            if (camera != null && camera.isOpened()) {
                fps = camera.get(Videoio.CAP_PROP_FPS);
                if (fps <= 0)
                    fps = 30;
            }
        }

        long frameDelay = (long) (1000.0 / fps);

        while (isRunning) {
            if (isPaused || isSeeking) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            boolean frameRead;
            synchronized (videoLock) {
                if (camera == null || !camera.isOpened())
                    break;
                frameRead = camera.read(frame);
                if (frameRead) {
                    currentFrame = (int) camera.get(Videoio.CAP_PROP_POS_FRAMES);
                }
            }

            if (frameRead && !frame.empty()) {
                processFrame(frame);

                final int frameNum = currentFrame;
                SwingUtilities.invokeLater(() -> {
                    if (!videoSlider.getValueIsAdjusting()) {
                        videoSlider.setValue(frameNum);
                    }
                    updateFrameInfo();
                });
            } else {
                // 影片結束
                isPaused = true;
                SwingUtilities.invokeLater(() -> {
                    playPauseBtn.setText("▶ 播放");
                    addEventLog("影片播放完畢");
                });
            }

            try {
                Thread.sleep(frameDelay);
            } catch (InterruptedException e) {
                break;
            }
        }

        frame.release();
    }

    /**
     * 處理單一影格
     */
    private void processFrame(Mat frame) {
        int frameWidth = frame.cols();
        int frameHeight = frame.rows();

        // 1. AprilTag 偵測 - 找到球門位置
        List<AprilTagDetector.TagDetection> tags = aprilTagDetector.detect(frame);
        aprilTagDetector.drawDetections(frame, tags);

        // 更新球門位置
        for (AprilTagDetector.TagDetection tag : tags) {
            updateGoalFromAprilTag(tag, frameWidth, frameHeight);

            // 偵測主題 (ID 21-23)
            if (tag.getId() >= 21 && tag.getId() <= 23) {
                Motif detected = Motif.fromAprilTagId(tag.getId());
                if (detected != currentMotif) {
                    currentMotif = detected;
                    final Motif m = detected;
                    SwingUtilities.invokeLater(() -> {
                        motifLabel.setText(m.getPattern());
                        addEventLog("偵測到主題: " + m.getPattern());
                    });
                }
            }
        }

        // 2. 文物偵測
        List<DetectedArtifact> artifacts = artifactDetector.detect(frame);
        artifactDetector.drawDetections(frame, artifacts);

        // 3. 繪製球門區域
        drawGoalAreas(frame);

        // 4. 偵測文物進入球門
        checkArtifactScoring(artifacts);

        // 5. OCR 讀取轉播分數
        scoreOCR.detect(frame);

        // 更新 OCR 分數顯示
        SwingUtilities.invokeLater(() -> {
            if (scoreOCR.hasValidResults()) {
                redOcrLabel.setText(String.valueOf(scoreOCR.getRedScore()));
                blueOcrLabel.setText(String.valueOf(scoreOCR.getBlueScore()));
                redOcrLabel.setForeground(Color.BLACK);
                blueOcrLabel.setForeground(Color.BLACK);

                // 驗證計算分數與 OCR 分數是否一致
                int calcRed = scoringEngine.getMatchState().getRedScore().getTotalScore();
                int calcBlue = scoringEngine.getMatchState().getBlueScore().getTotalScore();
                int ocrRed = scoreOCR.getRedScore();
                int ocrBlue = scoreOCR.getBlueScore();

                if (calcRed == ocrRed && calcBlue == ocrBlue) {
                    validationLabel.setText("✓ 分數一致");
                    validationLabel.setForeground(new Color(0, 128, 0));
                } else {
                    validationLabel.setText("✗ 分數不一致");
                    validationLabel.setForeground(Color.RED);
                }
            }
        });

        // 更新顯示
        updateVideoLabel(frame);
    }

    /**
     * 根據 AprilTag 更新球門位置
     */
    private void updateGoalFromAprilTag(AprilTagDetector.TagDetection tag, int frameWidth, int frameHeight) {
        // ID 20 = 藍方球門, ID 24 = 紅方球門
        if (tag.getId() == 20 || tag.getId() == 24) {
            org.opencv.core.Point center = tag.getCenter();
            double tagSize = tag.getSize();

            // 球門開口區域 - 文物從這裡進入
            // 球門在 AprilTag 的外側（左或右），並且在 AprilTag 下方
            int goalWidth = (int) (tagSize * 1.5); // 球門寬度
            int goalHeight = (int) (tagSize * 2); // 球門高度（開口區域）

            Rect goalBounds;
            if (tag.getId() == 20) {
                // 藍方球門在 AprilTag ID 20 的左側
                // 球門開口位於 AprilTag 外側下方
                int goalX = Math.max(0, (int) (center.x - tagSize * 2.5));
                int goalY = (int) (center.y - tagSize * 0.5); // 稍微在 AprilTag 上方一點
                goalBounds = new Rect(goalX, goalY, goalWidth, goalHeight);
                blueGoal = new GoalArea(Alliance.BLUE, goalBounds, center);
                log.debug("藍方球門區域: x={}, y={}, w={}, h={}", goalX, goalY, goalWidth, goalHeight);
            } else {
                // 紅方球門在 AprilTag ID 24 的右側
                int goalX = (int) (center.x + tagSize * 1.0);
                int goalY = (int) (center.y - tagSize * 0.5);
                goalX = Math.min(goalX, frameWidth - goalWidth);
                goalBounds = new Rect(goalX, goalY, goalWidth, goalHeight);
                redGoal = new GoalArea(Alliance.RED, goalBounds, center);
                log.debug("紅方球門區域: x={}, y={}, w={}, h={}", goalX, goalY, goalWidth, goalHeight);
            }
        }
    }

    /**
     * 繪製球門區域
     */
    private void drawGoalAreas(Mat frame) {
        if (redGoal != null) {
            // 紅方球門 - 紅色半透明框
            Imgproc.rectangle(frame, redGoal.bounds, new Scalar(0, 0, 255), 2);
            Imgproc.putText(frame, "RED GOAL",
                    new org.opencv.core.Point(redGoal.bounds.x, redGoal.bounds.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 255), 2);
        }
        if (blueGoal != null) {
            // 藍方球門 - 藍色半透明框
            Imgproc.rectangle(frame, blueGoal.bounds, new Scalar(255, 0, 0), 2);
            Imgproc.putText(frame, "BLUE GOAL",
                    new org.opencv.core.Point(blueGoal.bounds.x, blueGoal.bounds.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 0, 0), 2);
        }
    }

    /**
     * 檢查文物是否進入球門計分
     */
    private void checkArtifactScoring(List<DetectedArtifact> artifacts) {
        for (DetectedArtifact artifact : artifacts) {
            org.opencv.core.Point center = artifact.getCenter();
            if (center == null)
                continue;

            // 檢查紅方球門
            if (redGoal != null && redGoal.contains(center)) {
                if (redRamp.addArtifact(artifact.getType(), center)) {
                    addEventLog("紅方 CLASSIFIED +3分 (" + artifact.getType().getDisplayName() + ")");
                    scoringEngine.scoreClassified(Alliance.RED, artifact.getType());
                    updateScoreDisplay();
                }
            }

            // 檢查藍方球門
            if (blueGoal != null && blueGoal.contains(center)) {
                if (blueRamp.addArtifact(artifact.getType(), center)) {
                    addEventLog("藍方 CLASSIFIED +3分 (" + artifact.getType().getDisplayName() + ")");
                    scoringEngine.scoreClassified(Alliance.BLUE, artifact.getType());
                    updateScoreDisplay();
                }
            }
        }
    }

    /**
     * 更新分數顯示
     */
    private void updateScoreDisplay() {
        SwingUtilities.invokeLater(() -> {
            MatchState state = scoringEngine.getMatchState();
            redScoreLabel.setText(String.valueOf(state.getRedScore().getTotalScore()));
            blueScoreLabel.setText(String.valueOf(state.getBlueScore().getTotalScore()));

            // 更新斜坡顯示
            updateRampDisplay(redRampPanel, redRamp, redPatternLabel);
            updateRampDisplay(blueRampPanel, blueRamp, bluePatternLabel);
        });
    }

    /**
     * 更新斜坡顯示
     */
    private void updateRampDisplay(JPanel rampPanel, RampState ramp, JLabel patternLabel) {
        Component[] slots = rampPanel.getComponents();
        ArtifactType[] artifacts = ramp.getArtifacts();

        for (int i = 0; i < 9 && i < slots.length; i++) {
            if (slots[i] instanceof JLabel) {
                JLabel slot = (JLabel) slots[i];
                if (artifacts[i] != null) {
                    if (artifacts[i] == ArtifactType.PURPLE) {
                        slot.setBackground(new Color(128, 0, 128));
                    } else if (artifacts[i] == ArtifactType.GREEN) {
                        slot.setBackground(new Color(0, 128, 0));
                    }
                } else {
                    slot.setBackground(Color.LIGHT_GRAY);
                }
            }
        }

        // 更新斜坡標題
        rampPanel.setBorder(BorderFactory.createTitledBorder(
                "斜坡 (" + ramp.getCount() + "/9)"));

        // 更新圖案匹配
        int matches = ramp.countPatternMatches(currentMotif);
        patternLabel.setText("圖案匹配: " + matches + "/3");
    }

    /**
     * 更新影像顯示
     */
    private void updateVideoLabel(Mat frame) {
        BufferedImage image = matToBufferedImage(frame);
        if (image != null) {
            Image scaled = image.getScaledInstance(
                    videoLabel.getWidth(), videoLabel.getHeight(), Image.SCALE_FAST);
            videoLabel.setIcon(new ImageIcon(scaled));
            videoLabel.setText(null);
        }
    }

    /**
     * Mat 轉 BufferedImage
     */
    private BufferedImage matToBufferedImage(Mat mat) {
        int type = (mat.channels() > 1) ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        mat.get(0, 0, ((DataBufferByte) image.getRaster().getDataBuffer()).getData());
        return image;
    }

    /**
     * 切換播放/暫停
     */
    private void togglePlayPause() {
        isPaused = !isPaused;
        playPauseBtn.setText(isPaused ? "▶ 播放" : "⏸ 暫停");
    }

    /**
     * 跳轉到指定影格
     */
    private void seekToFrame(int frameNumber) {
        isSeeking = true;
        synchronized (videoLock) {
            if (camera != null && camera.isOpened()) {
                camera.set(Videoio.CAP_PROP_POS_FRAMES, frameNumber);
                currentFrame = frameNumber;

                Mat frame = new Mat();
                if (camera.read(frame) && !frame.empty()) {
                    processFrame(frame);
                }
                frame.release();
            }
        }
        isSeeking = false;
        updateFrameInfo();
    }

    /**
     * 更新影格資訊
     */
    private void updateFrameInfo() {
        frameInfoLabel.setText(currentFrame + " / " + totalFrames);
    }

    /**
     * 擷取畫面
     */
    private void captureImage() {
        synchronized (videoLock) {
            if (camera == null || !camera.isOpened()) {
                JOptionPane.showMessageDialog(this, "請先載入影片");
                return;
            }

            Mat frame = new Mat();
            camera.set(Videoio.CAP_PROP_POS_FRAMES, currentFrame);
            if (camera.read(frame) && !frame.empty()) {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String filename = "capture_" + timestamp + ".jpg";

                File captureDir = new File("captures");
                if (!captureDir.exists())
                    captureDir.mkdirs();

                String path = captureDir.getPath() + File.separator + filename;
                Imgcodecs.imwrite(path, frame);
                addEventLog("畫面已擷取: " + filename);
            }
            frame.release();
        }
    }

    /**
     * 停止影片
     */
    private void stopVideo() {
        isRunning = false;

        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (videoLock) {
            if (camera != null) {
                camera.release();
                camera = null;
            }
        }

        log.info("影片已停止");
    }

    /**
     * 新增事件記錄
     */
    private void addEventLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            eventLogArea.append(String.format("[%s] %s\n", timestamp, message));
            eventLogArea.setCaretPosition(eventLogArea.getDocument().getLength());
        });
    }

    /**
     * 清理資源
     */
    private void cleanup() {
        stopVideo();
        artifactDetector.release();
        aprilTagDetector.release();
        log.info("資源已清理");
    }

    /**
     * 程式進入點
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                log.warn("無法設定系統外觀");
            }
            new FTCMainWindow().setVisible(true);
        });
    }

    // ===== 內部類別 =====

    /**
     * 球門區域
     */
    private static class GoalArea {
        Alliance alliance;
        Rect bounds;
        org.opencv.core.Point aprilTagCenter;

        GoalArea(Alliance alliance, Rect bounds, org.opencv.core.Point aprilTagCenter) {
            this.alliance = alliance;
            this.bounds = bounds;
            this.aprilTagCenter = aprilTagCenter;
        }

        boolean contains(org.opencv.core.Point p) {
            return p.x >= bounds.x && p.x <= bounds.x + bounds.width &&
                    p.y >= bounds.y && p.y <= bounds.y + bounds.height;
        }
    }

    /**
     * 斜坡狀態 - 追蹤進入球門的文物
     */
    private static class RampState {
        Alliance alliance;
        ArtifactType[] artifacts = new ArtifactType[9];
        int count = 0;

        // 位置追蹤：記錄已計分文物的位置，防止同一個球重複計分
        java.util.List<org.opencv.core.Point> scoredPositions = new java.util.ArrayList<>();
        private static final double POSITION_THRESHOLD = 30.0; // 像素距離閾值

        // 時間閘門：同一個球在短時間內不重複計分
        private long lastScoringTime = 0;
        private static final long SCORING_COOLDOWN_MS = 1000; // 1秒冷卻時間

        RampState(Alliance alliance) {
            this.alliance = alliance;
        }

        /**
         * 嘗試新增文物到斜坡
         * 
         * @param type     文物類型
         * @param position 文物位置 (可為 null)
         * @return 是否成功新增 (避免重複)
         */
        boolean addArtifact(ArtifactType type, org.opencv.core.Point position) {
            // 檢查斜坡是否已滿
            if (count >= 9)
                return false;

            // 檢查冷卻時間
            long now = System.currentTimeMillis();
            if (now - lastScoringTime < SCORING_COOLDOWN_MS) {
                return false;
            }

            // 如果有位置資訊，檢查是否已經在附近計分過
            if (position != null) {
                for (org.opencv.core.Point scored : scoredPositions) {
                    double dist = Math.sqrt(
                            Math.pow(position.x - scored.x, 2) +
                                    Math.pow(position.y - scored.y, 2));
                    if (dist < POSITION_THRESHOLD) {
                        return false; // 太近，可能是同一個球
                    }
                }
                // 記錄新位置
                scoredPositions.add(new org.opencv.core.Point(position.x, position.y));

                // 清理舊位置 (保留最近 20 個)
                while (scoredPositions.size() > 20) {
                    scoredPositions.remove(0);
                }
            }

            // 新增到斜坡
            artifacts[count++] = type;
            lastScoringTime = now;
            return true;
        }

        /**
         * 簡化版新增 (無位置追蹤)
         */
        boolean addArtifact(ArtifactType type) {
            return addArtifact(type, null);
        }

        int getCount() {
            return count;
        }

        ArtifactType[] getArtifacts() {
            return artifacts;
        }

        /**
         * 計算與主題匹配的數量
         */
        int countPatternMatches(Motif motif) {
            if (motif == Motif.UNKNOWN || count < 1)
                return 0;

            char[] pattern = motif.getPattern().toCharArray();
            int matches = 0;

            for (int i = 0; i < Math.min(3, count); i++) {
                if (artifacts[i] != null && i < pattern.length) {
                    char expected = pattern[i];
                    char actual = artifacts[i] == ArtifactType.PURPLE ? 'P' : 'G';
                    if (expected == actual)
                        matches++;
                }
            }
            return matches;
        }

        void reset() {
            artifacts = new ArtifactType[9];
            count = 0;
            scoredPositions.clear();
            lastScoringTime = 0;
        }
    }
}
