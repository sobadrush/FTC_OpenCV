package com.telearn.ftc.ui;

import com.telearn.ftc.model.*;
import com.telearn.ftc.scoring.ScoringEngine;
import com.telearn.ftc.strategy.CycleAnalyzer;
import com.telearn.ftc.util.MatchDataExporter;
import com.telearn.ftc.vision.*;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * FTC 比賽分析器主視窗
 * 整合影像辨識、計分與策略分析功能
 */
@Slf4j
public class FTCMainWindow extends JFrame {

    // OpenCV 載入
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    // ===== UI 元件 =====
    private JLabel videoLabel;
    private ScoreboardPanel redScorePanel;
    private ScoreboardPanel blueScorePanel;
    private JTextArea eventLogArea;
    private JLabel timeLabel;
    private JLabel phaseLabel;
    private JLabel motifLabel;
    private JLabel rampLabel;
    private JProgressBar rampProgressBar;

    // ===== 視訊相關 =====
    private VideoCapture camera;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile boolean isSeeking = false; // 新增: 防止 seek 期間讀取
    private Thread captureThread;
    private String currentVideoPath = null;
    private int totalFrames = 0;
    private int currentFrame = 0;
    private final Object videoLock = new Object(); // 新增: 執行緒安全鎖
    private Mat lastProcessedFrame = null; // 保存最後處理的幀供標記用

    // ===== 影片控制元件 =====
    private JSlider videoSlider;
    private JButton playPauseBtn;
    private JLabel frameInfoLabel;

    // ===== 偵測器 =====
    private ArtifactDetector artifactDetector;
    private RobotDetector robotDetector;
    private ZoneDetector zoneDetector;
    private AprilTagDetector aprilTagDetector;

    // ===== 分析引擎 =====
    private ScoringEngine scoringEngine;
    private CycleAnalyzer cycleAnalyzer;

    // ===== 設定 =====
    private boolean showArtifacts = true;
    private boolean showRobots = true;
    private boolean showZones = false;
    private boolean showAprilTags = true;
    private boolean autoDetectMotif = true;
    private boolean autoScoring = false;
    private boolean isMarkingMode = false; // 是否正在標記機器人
    private boolean isMarkingZoneMode = false; // 是否正在標記得分區
    private com.telearn.ftc.model.Alliance currentMarkingAlliance = com.telearn.ftc.model.Alliance.RED; // 當前要標記的聯盟

    // 得分區列表
    private java.util.List<com.telearn.ftc.model.ScoringZone> scoringZones = new java.util.ArrayList<>();
    // 已計分的文物位置 (使用位置追蹤避免重複計分)
    private java.util.List<org.opencv.core.Point> scoredArtifactPositions = new java.util.ArrayList<>();
    private static final double SCORED_POSITION_THRESHOLD = 50.0; // 距離閾值

    // 視窗尺寸
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final int DEFAULT_ROBOT_SIZE = 80; // 預設機器人標記大小
    private static final int DEFAULT_ZONE_SIZE = 120; // 預設得分區大小

    public FTCMainWindow() {
        initializeComponents();
        initializeUI();
        log.info("FTC 比賽分析器啟動");
    }

    /**
     * 初始化元件
     */
    private void initializeComponents() {
        artifactDetector = new ArtifactDetector();
        robotDetector = new RobotDetector();
        zoneDetector = new ZoneDetector();
        aprilTagDetector = new AprilTagDetector();
        scoringEngine = new ScoringEngine(zoneDetector);
        cycleAnalyzer = new CycleAnalyzer();
    }

    /**
     * 初始化 UI
     */
    private void initializeUI() {
        setTitle("FTC 比賽分析器 v1.0 (INTO THE DEEP 2025)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 左側：影像顯示區
        JPanel leftPanel = createVideoPanel();
        mainPanel.add(leftPanel, BorderLayout.CENTER);

        // 右側：計分板與資訊
        JPanel rightPanel = createInfoPanel();
        mainPanel.add(rightPanel, BorderLayout.EAST);

        // 底部：控制面板
        JPanel bottomPanel = createControlPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // 設定視窗
        pack();
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);

        // 視窗關閉時釋放資源
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    /**
     * 建立影像顯示面板
     */
    private JPanel createVideoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("即時影像"));

        videoLabel = new JLabel();
        videoLabel.setPreferredSize(new Dimension(VIDEO_WIDTH, VIDEO_HEIGHT));
        videoLabel.setHorizontalAlignment(JLabel.CENTER);
        videoLabel.setBackground(Color.BLACK);
        videoLabel.setOpaque(true);
        videoLabel.setText("攝影機未啟動");
        panel.add(videoLabel, BorderLayout.CENTER);

        // 影像控制項
        JPanel overlayControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JCheckBox showArtifactsChk = new JCheckBox("顯示文物", showArtifacts);
        showArtifactsChk.addActionListener(e -> showArtifacts = showArtifactsChk.isSelected());

        JCheckBox showRobotsChk = new JCheckBox("顯示機器人", showRobots);
        showRobotsChk.addActionListener(e -> showRobots = showRobotsChk.isSelected());

        JCheckBox showZonesChk = new JCheckBox("顯示區域", showZones);
        showZonesChk.addActionListener(e -> showZones = showZonesChk.isSelected());

        JCheckBox showAprilTagsChk = new JCheckBox("AprilTag", showAprilTags);
        showAprilTagsChk.addActionListener(e -> showAprilTags = showAprilTagsChk.isSelected());

        JCheckBox autoDetectMotifChk = new JCheckBox("自動偵測主題", autoDetectMotif);
        autoDetectMotifChk.addActionListener(e -> autoDetectMotif = autoDetectMotifChk.isSelected());

        // 聯盟選擇按鈕 (紅方/藍方)
        JPanel alliancePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JLabel allianceLabel = new JLabel("標記聯盟:");
        JToggleButton redBtn = new JToggleButton("🔴 紅方", true);
        JToggleButton blueBtn = new JToggleButton("🔵 藍方", false);
        redBtn.setBackground(new Color(255, 100, 100));
        blueBtn.setBackground(Color.LIGHT_GRAY);

        ButtonGroup allianceGroup = new ButtonGroup();
        allianceGroup.add(redBtn);
        allianceGroup.add(blueBtn);

        redBtn.addActionListener(e -> {
            currentMarkingAlliance = com.telearn.ftc.model.Alliance.RED;
            redBtn.setBackground(new Color(255, 100, 100));
            blueBtn.setBackground(Color.LIGHT_GRAY);
        });
        blueBtn.addActionListener(e -> {
            currentMarkingAlliance = com.telearn.ftc.model.Alliance.BLUE;
            blueBtn.setBackground(new Color(100, 100, 255));
            redBtn.setBackground(Color.LIGHT_GRAY);
        });

        alliancePanel.add(allianceLabel);
        alliancePanel.add(redBtn);
        alliancePanel.add(blueBtn);

        // 標記機器人按鈕
        JButton markRobotsBtn = new JButton("🎯 標記機器人 (0/4)");
        markRobotsBtn.setBackground(Color.LIGHT_GRAY);
        markRobotsBtn.addActionListener(e -> {
            if (isMarkingMode) {
                // 結束標記模式
                isMarkingMode = false;
                markRobotsBtn.setBackground(Color.LIGHT_GRAY);
                markRobotsBtn.setText("🎯 標記機器人 (" + robotDetector.getMarkedRobotCount() + "/4)");
                addEventLog("標記模式已關閉");
            } else {
                // 開始標記模式
                robotDetector.resetTracking();
                // 設定模板幀供機器人模板提取使用
                if (lastProcessedFrame != null && !lastProcessedFrame.empty()) {
                    robotDetector.setTemplateFrame(lastProcessedFrame);
                }
                isMarkingMode = true;
                isPaused = true; // 自動暫停
                markRobotsBtn.setBackground(Color.YELLOW);
                markRobotsBtn.setText("🎯 標記中... 點擊影片標記機器人 (0/4)");
                addEventLog("標記模式已開啟 - 選擇聯盟後點擊機器人位置");
            }
        });

        // 影片點擊處理 (標記機器人)
        videoLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isMarkingMode || camera == null)
                    return;

                // Label 尺寸
                int labelWidth = videoLabel.getWidth();
                int labelHeight = videoLabel.getHeight();
                int clickX = e.getX();
                int clickY = e.getY();

                // 影格實際尺寸
                int frameWidth = (int) camera.get(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH);
                int frameHeight = (int) camera.get(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT);

                // 簡單比例轉換 (因為影像填滿整個 Label，不需要偏移計算)
                int actualX = (int) ((double) clickX / labelWidth * frameWidth);
                int actualY = (int) ((double) clickY / labelHeight * frameHeight);

                boolean added = robotDetector.addManualRobot(actualX, actualY, DEFAULT_ROBOT_SIZE,
                        currentMarkingAlliance);
                if (added) {
                    int count = robotDetector.getMarkedRobotCount();
                    markRobotsBtn.setText("🎯 標記中... (" + count + "/4)");
                    if (count >= 4) {
                        isMarkingMode = false;
                        markRobotsBtn.setBackground(Color.GREEN);
                        markRobotsBtn.setText("✓ 標記完成 (4/4)");
                        isPaused = false;
                        addEventLog("4 台機器人已標記完成！開始追蹤");
                    }
                }
            }
        });

        // 標記得分區按鈕
        JButton markZonesBtn = new JButton("⭕ 標記得分區 (0/2)");
        markZonesBtn.setBackground(Color.LIGHT_GRAY);
        markZonesBtn.addActionListener(e -> {
            if (isMarkingZoneMode) {
                isMarkingZoneMode = false;
                markZonesBtn.setBackground(Color.LIGHT_GRAY);
                markZonesBtn.setText("⭕ 標記得分區 (" + scoringZones.size() + "/2)");
                addEventLog("得分區標記模式已關閉");
            } else {
                scoringZones.clear();
                scoredArtifactPositions.clear();
                isMarkingZoneMode = true;
                isPaused = true;
                markZonesBtn.setBackground(Color.ORANGE);
                markZonesBtn.setText("⭕ 標記中... 選擇聯盟後點擊得分區 (0/2)");
                addEventLog("得分區標記模式 - 選擇紅/藍方後點擊球門位置");
            }
        });

        // 得分區點擊處理
        videoLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isMarkingZoneMode || camera == null)
                    return;

                int labelWidth = videoLabel.getWidth();
                int labelHeight = videoLabel.getHeight();
                int clickX = e.getX();
                int clickY = e.getY();

                int frameWidth = (int) camera.get(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH);
                int frameHeight = (int) camera.get(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT);

                int actualX = (int) ((double) clickX / labelWidth * frameWidth);
                int actualY = (int) ((double) clickY / labelHeight * frameHeight);

                // 建立得分區
                int halfSize = DEFAULT_ZONE_SIZE / 2;
                int x = Math.max(0, actualX - halfSize);
                int y = Math.max(0, actualY - halfSize);

                org.opencv.core.Rect zoneBounds = new org.opencv.core.Rect(x, y, DEFAULT_ZONE_SIZE, DEFAULT_ZONE_SIZE);
                String zoneName = currentMarkingAlliance == com.telearn.ftc.model.Alliance.RED ? "紅方球門" : "藍方球門";
                com.telearn.ftc.model.ScoringZone zone = new com.telearn.ftc.model.ScoringZone(
                        scoringZones.size() + 1, zoneName, currentMarkingAlliance, zoneBounds);
                scoringZones.add(zone);

                addEventLog(zoneName + " 已標記於 (" + actualX + ", " + actualY + ")");
                markZonesBtn.setText("⭕ 標記中... (" + scoringZones.size() + "/2)");

                if (scoringZones.size() >= 2) {
                    isMarkingZoneMode = false;
                    markZonesBtn.setBackground(Color.GREEN);
                    markZonesBtn.setText("✓ 得分區標記完成 (2/2)");
                    isPaused = false;
                    autoScoring = true;
                    addEventLog("得分區標記完成！自動計分已啟用");
                }
            }
        });

        overlayControlPanel.add(showArtifactsChk);
        overlayControlPanel.add(showRobotsChk);
        overlayControlPanel.add(showZonesChk);
        overlayControlPanel.add(showAprilTagsChk);
        overlayControlPanel.add(autoDetectMotifChk);
        overlayControlPanel.add(alliancePanel);
        overlayControlPanel.add(markRobotsBtn);
        overlayControlPanel.add(markZonesBtn);

        panel.add(overlayControlPanel, BorderLayout.NORTH);

        // 影片控制列 (底部)
        JPanel videoControlPanel = createVideoControlPanel();
        panel.add(videoControlPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * 建立影片控制面板
     */
    private JPanel createVideoControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 播放/暫停按鈕
        playPauseBtn = new JButton("⏸ 暫停");
        playPauseBtn.setPreferredSize(new Dimension(80, 30));
        playPauseBtn.addActionListener(e -> togglePlayPause());
        playPauseBtn.setEnabled(false);

        // 時間軸 Slider
        videoSlider = new JSlider(0, 100, 0);
        videoSlider.setEnabled(false);
        videoSlider.addChangeListener(e -> {
            if (videoSlider.getValueIsAdjusting() && camera != null && camera.isOpened()) {
                int targetFrame = videoSlider.getValue();
                seekToFrame(targetFrame);
            }
        });

        // 影格資訊
        frameInfoLabel = new JLabel("0 / 0");
        frameInfoLabel.setPreferredSize(new Dimension(120, 20));
        frameInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        frameInfoLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));

        panel.add(playPauseBtn, BorderLayout.WEST);
        panel.add(videoSlider, BorderLayout.CENTER);
        panel.add(frameInfoLabel, BorderLayout.EAST);

        return panel;
    }

    /**
     * 切換播放/暫停
     */
    private void togglePlayPause() {
        isPaused = !isPaused;
        playPauseBtn.setText(isPaused ? "▶ 播放" : "⏸ 暫停");
    }

    /**
     * 跳轉到指定影格 (執行緒安全)
     */
    private void seekToFrame(int frameNumber) {
        if (camera == null || !camera.isOpened() || totalFrames <= 0) {
            return;
        }

        // 設定 seeking flag 以停止 captureLoop 讀取
        isSeeking = true;

        // 在背景執行緒遛行 seek 以避免阻塞 UI
        new Thread(() -> {
            synchronized (videoLock) {
                try {
                    camera.set(Videoio.CAP_PROP_POS_FRAMES, frameNumber);
                    currentFrame = frameNumber;

                    // 讀取並顯示該影格
                    Mat frame = new Mat();
                    if (camera.read(frame)) {
                        processFrame(frame);
                        updateVideoLabel(frame);
                    }
                    frame.release();

                    updateFrameInfo();
                } finally {
                    isSeeking = false;
                }
            }
        }).start();
    }

    /**
     * 更新影格資訊顯示
     */
    private void updateFrameInfo() {
        SwingUtilities.invokeLater(() -> {
            frameInfoLabel.setText(String.format("%d / %d", currentFrame, totalFrames));
            if (!videoSlider.getValueIsAdjusting()) {
                videoSlider.setValue(currentFrame);
            }
        });
    }

    /**
     * 建立資訊面板
     */
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(350, 0));

        // 比賽資訊
        JPanel matchInfoPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        matchInfoPanel.setBorder(new TitledBorder("比賽資訊"));

        timeLabel = new JLabel("02:30");
        timeLabel.setFont(new Font("Monospaced", Font.BOLD, 24));
        phaseLabel = new JLabel("比賽前");
        motifLabel = new JLabel("???");
        rampLabel = new JLabel("0/9");

        matchInfoPanel.add(new JLabel("剩餘時間:"));
        matchInfoPanel.add(timeLabel);
        matchInfoPanel.add(new JLabel("階段:"));
        matchInfoPanel.add(phaseLabel);
        matchInfoPanel.add(new JLabel("主題:"));
        matchInfoPanel.add(motifLabel);
        matchInfoPanel.add(new JLabel("坡道:"));
        matchInfoPanel.add(rampLabel);

        panel.add(matchInfoPanel);

        // 坡道進度條
        rampProgressBar = new JProgressBar(0, 9);
        rampProgressBar.setStringPainted(true);
        rampProgressBar.setString("0/9");
        panel.add(rampProgressBar);

        panel.add(Box.createVerticalStrut(10));

        // 紅色聯盟計分板
        redScorePanel = new ScoreboardPanel(Alliance.RED);
        panel.add(redScorePanel);

        panel.add(Box.createVerticalStrut(10));

        // 藍色聯盟計分板
        blueScorePanel = new ScoreboardPanel(Alliance.BLUE);
        panel.add(blueScorePanel);

        panel.add(Box.createVerticalStrut(10));

        // 事件記錄
        JPanel eventPanel = new JPanel(new BorderLayout());
        eventPanel.setBorder(new TitledBorder("事件記錄"));

        eventLogArea = new JTextArea(6, 30);
        eventLogArea.setEditable(false);
        eventLogArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(eventLogArea);
        eventPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(eventPanel);

        return panel;
    }

    /**
     * 建立控制面板
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        panel.setBorder(new TitledBorder("控制"));

        // 影片控制
        JButton loadFileBtn = new JButton("📂 載入影片");
        loadFileBtn.addActionListener(e -> loadFile());

        JButton stopBtn = new JButton("⏹ 停止");
        stopBtn.addActionListener(e -> stopCamera());

        JButton captureBtn = new JButton("📷 擷取畫面");
        captureBtn.addActionListener(e -> captureImage());

        panel.add(loadFileBtn);
        panel.add(stopBtn);
        panel.add(captureBtn);

        panel.add(new JSeparator(SwingConstants.VERTICAL));

        // 手動計分
        JButton scoreRedBtn = new JButton("🔴 紅+3");
        scoreRedBtn.setBackground(new Color(255, 200, 200));
        scoreRedBtn.addActionListener(e -> manualScore(Alliance.RED));

        JButton scoreBlueBtn = new JButton("🔵 藍+3");
        scoreBlueBtn.setBackground(new Color(200, 200, 255));
        scoreBlueBtn.addActionListener(e -> manualScore(Alliance.BLUE));

        panel.add(scoreRedBtn);
        panel.add(scoreBlueBtn);

        panel.add(new JSeparator(SwingConstants.VERTICAL));

        // 主題選擇
        JComboBox<Motif> motifCombo = new JComboBox<>(new Motif[] { Motif.UNKNOWN, Motif.GPP, Motif.PGP, Motif.PPG });
        motifCombo.addActionListener(e -> {
            Motif selected = (Motif) motifCombo.getSelectedItem();
            scoringEngine.setDetectedMotif(selected);
            motifLabel.setText(selected.getPattern());
        });
        panel.add(new JLabel("主題:"));
        panel.add(motifCombo);

        panel.add(new JSeparator(SwingConstants.VERTICAL));

        // 資料匯出
        JButton exportBtn = new JButton("💾 匯出資料");
        exportBtn.addActionListener(e -> exportMatchData());
        panel.add(exportBtn);

        return panel;
    }

    /**
     * 匯出比賽資料
     */
    private void exportMatchData() {
        String dir = "match_data";
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }

        // 匯出 JSON
        MatchDataExporter.exportToJsonFile(scoringEngine, cycleAnalyzer, dir);

        // 匯出 CSV
        MatchDataExporter.exportToCsvFile(scoringEngine, cycleAnalyzer, dir);

        JOptionPane.showMessageDialog(this,
                "比賽資料已匯出至 " + dir + " 目錄",
                "匯出成功", JOptionPane.INFORMATION_MESSAGE);

        addEventLog("資料已匯出至 " + dir);
    }

    /**
     * 載入檔案
     */
    private void loadFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("選擇影片或圖片檔案");
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            stopCamera(); // Stop current stream if any
            startCamera(selectedFile.getAbsolutePath());
        }
    }

    /**
     * 啟動攝影機或載入檔案
     * 
     * @param videoPath 檔案路徑，null 表示使用預設攝影機
     */
    private void startCamera(String videoPath) {
        if (isRunning) {
            log.info("停止目前串流以切換來源");
            stopCamera();
        }

        if (videoPath == null) {
            camera = new VideoCapture(0);
            this.currentVideoPath = "Camera 0";
        } else {
            camera = new VideoCapture(videoPath);
            this.currentVideoPath = videoPath;
        }

        if (!camera.isOpened()) {
            JOptionPane.showMessageDialog(this,
                    "無法開啟來源：" + (videoPath == null ? "Default Camera" : videoPath),
                    "錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }

        camera.set(Videoio.CAP_PROP_FRAME_WIDTH, VIDEO_WIDTH);
        camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, VIDEO_HEIGHT);

        isRunning = true;
        isPaused = false;

        // 初始化影片控制 (僅限檔案)
        boolean isVideoFile = videoPath != null && !currentVideoPath.equals("Camera 0");
        if (isVideoFile) {
            totalFrames = (int) camera.get(Videoio.CAP_PROP_FRAME_COUNT);
            currentFrame = 0;
            videoSlider.setMaximum(Math.max(1, totalFrames));
            videoSlider.setValue(0);
            videoSlider.setEnabled(true);
            playPauseBtn.setEnabled(true);
            playPauseBtn.setText("⏸ 暫停");
            updateFrameInfo();
        } else {
            totalFrames = 0;
            currentFrame = 0;
            videoSlider.setEnabled(false);
            playPauseBtn.setEnabled(false);
            frameInfoLabel.setText("即時攝影機");
        }

        captureThread = new Thread(this::captureLoop);
        captureThread.start();

        log.info("攝影機已啟動");
    }

    /**
     * 影像擷取迴圈 (執行緒安全)
     */
    private void captureLoop() {
        Mat frame = new Mat();
        boolean isVideoFile = currentVideoPath != null && !currentVideoPath.equals("Camera 0");

        while (isRunning) {
            // 如果暫停或正在 seeking，等待
            if (isPaused || isSeeking) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            // 使用鎖保護 VideoCapture 存取
            synchronized (videoLock) {
                if (!isRunning || isSeeking)
                    continue;

                if (camera.read(frame)) {
                    processFrame(frame);
                    updateVideoLabel(frame);

                    // 更新影格計數 (僅影片檔案)
                    if (isVideoFile) {
                        currentFrame = (int) camera.get(Videoio.CAP_PROP_POS_FRAMES);
                        updateFrameInfo();

                        // 影片結束時自動暫停
                        if (currentFrame >= totalFrames) {
                            isPaused = true;
                            SwingUtilities.invokeLater(() -> playPauseBtn.setText("▶ 播放"));
                        }
                    }
                } else if (isVideoFile) {
                    // 影片結束
                    isPaused = true;
                    SwingUtilities.invokeLater(() -> playPauseBtn.setText("▶ 播放"));
                }
            }

            try {
                if (isVideoFile) {
                    double fps = camera.get(Videoio.CAP_PROP_FPS);
                    int delay = (fps > 0) ? (int) (1000 / fps) : 33;
                    Thread.sleep(delay);
                } else {
                    Thread.sleep(33);
                }
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
        // 保存當前幀供標記用
        if (lastProcessedFrame == null) {
            lastProcessedFrame = frame.clone();
        } else {
            frame.copyTo(lastProcessedFrame);
        }

        // 定義場地 ROI (只分析中央比賽區域)
        // 根據影片比例：場地約佔水平 10%-95%，垂直 3%-63% (計分板上方)
        int frameWidth = frame.cols();
        int frameHeight = frame.rows();

        int roiX = (int) (frameWidth * 0.10);
        int roiY = (int) (frameHeight * 0.03);
        int roiW = (int) (frameWidth * 0.85);
        int roiH = (int) (frameHeight * 0.60);

        // 確保 ROI 在有效範圍內
        roiW = Math.min(roiW, frameWidth - roiX);
        roiH = Math.min(roiH, frameHeight - roiY);

        Rect fieldROI = new Rect(roiX, roiY, roiW, roiH);
        Mat fieldFrame = new Mat(frame, fieldROI);

        // 偵測文物 (只在場地區域內)
        if (showArtifacts) {
            List<DetectedArtifact> artifacts = artifactDetector.detect(fieldFrame);
            // 調整座標回原始影像
            for (DetectedArtifact artifact : artifacts) {
                Rect box = artifact.getBoundingBox();
                artifact.setBoundingBox(new Rect(box.x + roiX, box.y + roiY, box.width, box.height));
            }
            artifactDetector.drawDetections(frame, artifacts);

            // 自動計分：檢查文物是否在得分區內
            if (autoScoring && !scoringZones.isEmpty()) {
                for (DetectedArtifact artifact : artifacts) {
                    Point artifactCenter = artifact.getCenter();
                    if (artifactCenter == null)
                        continue;

                    // 檢查是否已在此位置計過分 (使用距離閾值)
                    boolean alreadyScored = scoredArtifactPositions.stream()
                            .anyMatch(p -> {
                                double dx = p.x - artifactCenter.x;
                                double dy = p.y - artifactCenter.y;
                                return Math.sqrt(dx * dx + dy * dy) < SCORED_POSITION_THRESHOLD;
                            });

                    if (alreadyScored)
                        continue;

                    // 檢查是否在任何得分區內
                    for (com.telearn.ftc.model.ScoringZone zone : scoringZones) {
                        if (zone.contains(artifact)) {
                            // 計分！
                            zone.recordScore();
                            scoredArtifactPositions.add(new Point(artifactCenter.x, artifactCenter.y));

                            // 更新計分引擎
                            com.telearn.ftc.model.ArtifactType type = artifact.getType();
                            scoringEngine.scoreClassified(zone.getAlliance(), type);

                            String msg = String.format("%s 得分! %s球進入%s (+3分)",
                                    zone.getAlliance().getDisplayName(),
                                    type.getDisplayName(),
                                    zone.getName());
                            addEventLog(msg);

                            // 更新分數顯示
                            SwingUtilities.invokeLater(this::updateMatchInfo);
                            break;
                        }
                    }
                }
            }
        }

        // 繪製得分區
        for (com.telearn.ftc.model.ScoringZone zone : scoringZones) {
            Rect bounds = zone.getBounds();
            Scalar color = zone.getAlliance() == com.telearn.ftc.model.Alliance.RED
                    ? new Scalar(0, 0, 255) // 紅色
                    : new Scalar(255, 0, 0); // 藍色

            // 繪製虛線矩形框
            Imgproc.rectangle(frame, bounds, color, 2);

            // 繪製標籤
            String label = zone.getName() + " (" + zone.getScoredCount() + "球)";
            Imgproc.putText(frame, label,
                    new Point(bounds.x, bounds.y - 5),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 2);
        }

        // 偵測機器人 (使用完整畫面，因為手動標記使用完整座標)
        if (showRobots) {
            List<DetectedRobot> robots = robotDetector.detect(frame);
            robotDetector.drawDetections(frame, robots);
        }

        // 繪製 ROI 邊界 (除錯用)
        if (showZones) {
            Imgproc.rectangle(frame, fieldROI, new Scalar(255, 255, 0), 2);
            Imgproc.putText(frame, "Field ROI", new Point(roiX + 5, roiY + 20),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(255, 255, 0), 2);
        }

        // AprilTag 偵測與主題自動識別
        if (showAprilTags || autoDetectMotif) {
            List<AprilTagDetector.TagDetection> tags = aprilTagDetector.detect(frame);

            if (showAprilTags) {
                aprilTagDetector.drawDetections(frame, tags);
            }

            // 使用 AprilTag ID 20 和 ID 24 來建立坡道得分區
            // ID 20 = 藍方坡道 (在左側/外側), ID 24 = 紅方坡道 (在右側/外側)
            if (scoringZones.isEmpty()) {
                for (AprilTagDetector.TagDetection tag : tags) {
                    if (tag.getId() == 20 || tag.getId() == 24) {
                        Point center = tag.getCenter();
                        double tagSize = tag.getSize();

                        // 坡道區大小 - 只有球滑下來的那個小區域
                        int zoneWidth = (int) (tagSize * 1.2); // 較窄
                        int zoneHeight = (int) (tagSize * 3); // 較短
                        int zoneY = (int) (center.y - tagSize / 2); // AprilTag 附近
                        int zoneX;

                        // ID 20 (藍方) 坡道在 AprilTag 左側邊緣
                        // ID 24 (紅方) 坡道在 AprilTag 右側邊緣
                        if (tag.getId() == 20) {
                            // 藍方 - 坡道緊鄰 AprilTag 左邊
                            zoneX = (int) (center.x - tagSize * 1.5 - zoneWidth);
                        } else {
                            // 紅方 - 坡道緊鄰 AprilTag 右邊
                            zoneX = (int) (center.x + tagSize * 1.5);
                        }

                        // 確保在影像範圍內
                        zoneX = Math.max(0, zoneX);
                        zoneY = Math.max(0, zoneY);
                        zoneWidth = Math.min(zoneWidth, frameWidth - zoneX);
                        zoneHeight = Math.min(zoneHeight, frameHeight - zoneY);

                        if (zoneWidth > 20 && zoneHeight > 20) {
                            // ID 20 = 藍方, ID 24 = 紅方
                            com.telearn.ftc.model.Alliance alliance = tag.getId() == 20
                                    ? com.telearn.ftc.model.Alliance.BLUE
                                    : com.telearn.ftc.model.Alliance.RED;
                            String zoneName = alliance == com.telearn.ftc.model.Alliance.BLUE ? "藍方坡道" : "紅方坡道";

                            Rect zoneBounds = new Rect(zoneX, zoneY, zoneWidth, zoneHeight);
                            com.telearn.ftc.model.ScoringZone zone = new com.telearn.ftc.model.ScoringZone(
                                    scoringZones.size() + 1, zoneName, alliance, zoneBounds);
                            scoringZones.add(zone);

                            autoScoring = true;
                            addEventLog("偵測到 " + zoneName + " (AprilTag ID:" + tag.getId() + ")");
                        }
                    }
                }
            }

            // 自動設定主題
            if (autoDetectMotif) {
                aprilTagDetector.detectMotif(frame).ifPresent(motif -> {
                    if (motif != scoringEngine.getMatchState().getDetectedMotif()) {
                        scoringEngine.setDetectedMotif(motif);
                        final Motif detectedMotif = motif;
                        SwingUtilities.invokeLater(() -> {
                            addEventLog("自動偵測到主題: " + detectedMotif.getPattern());
                        });
                    }
                });
            }
        }

        // 更新比賽時間
        if (scoringEngine.getMatchState().isMatchInProgress()) {
            scoringEngine.updateTime();
            SwingUtilities.invokeLater(this::updateMatchInfo);
        }
    }

    /**
     * 更新影像顯示 (填滿整個 Label，簡化座標轉換)
     */
    private void updateVideoLabel(Mat frame) {
        // 取得 videoLabel 目前的尺寸
        int labelWidth = videoLabel.getWidth();
        int labelHeight = videoLabel.getHeight();

        // 如果 label 尚未初始化，使用預設尺寸
        if (labelWidth <= 0 || labelHeight <= 0) {
            labelWidth = VIDEO_WIDTH;
            labelHeight = VIDEO_HEIGHT;
        }

        // 直接縮放到 label 尺寸 (不維持長寬比，簡化座標計算)
        Mat resizedFrame = new Mat();
        Imgproc.resize(frame, resizedFrame, new Size(labelWidth, labelHeight), 0, 0, Imgproc.INTER_LINEAR);

        BufferedImage image = matToBufferedImage(resizedFrame);
        ImageIcon icon = new ImageIcon(image);
        SwingUtilities.invokeLater(() -> videoLabel.setIcon(icon));

        resizedFrame.release();
    }

    /**
     * Mat 轉 BufferedImage
     */
    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }

        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] buffer = new byte[bufferSize];
        mat.get(0, 0, buffer);

        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);

        return image;
    }

    /**
     * 停止攝影機
     */
    private void stopCamera() {
        isRunning = false;

        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                log.error("等待執行緒終止時發生錯誤", e);
            }
        }

        if (camera != null && camera.isOpened()) {
            camera.release();
        }

        videoLabel.setIcon(null);
        videoLabel.setText("攝影機已停止");

        log.info("攝影機已停止");
    }

    /**
     * 擷取畫面
     */
    private void captureImage() {
        if (camera == null || !camera.isOpened()) {
            JOptionPane.showMessageDialog(this,
                    "請先啟動攝影機！",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Mat frame = new Mat();
        if (camera.read(frame)) {
            String dir = "capture_photo";
            File dirFile = new File(dir);
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String filename = String.format("%s/ftc_capture_%s.jpg", dir, timestamp);

            Imgcodecs.imwrite(filename, frame);
            log.info("畫面已儲存: {}", filename);
            JOptionPane.showMessageDialog(this,
                    "畫面已儲存: " + filename,
                    "成功", JOptionPane.INFORMATION_MESSAGE);
        }

        frame.release();
    }

    /**
     * 開始比賽
     */
    private void startMatch() {
        scoringEngine.startMatch();
        cycleAnalyzer.reset();
        updateMatchInfo();
        updateScoreboards();
        addEventLog("比賽開始！");
        log.info("比賽開始");
    }

    /**
     * 結束比賽
     */
    private void endMatch() {
        scoringEngine.endMatch();
        updateMatchInfo();
        updateScoreboards();
        addEventLog("比賽結束！最終分數 - 紅:" +
                scoringEngine.getMatchState().getRedScore().getTotalScore() +
                " 藍:" + scoringEngine.getMatchState().getBlueScore().getTotalScore());
        log.info("比賽結束");
    }

    /**
     * 清空坡道
     */
    private void clearRamp() {
        scoringEngine.clearRamp();
        updateMatchInfo();
        addEventLog("坡道已清空");
    }

    /**
     * 手動計分
     */
    private void manualScore(Alliance alliance) {
        // 隨機選擇文物類型
        ArtifactType type = Math.random() > 0.5 ? ArtifactType.PURPLE : ArtifactType.GREEN;
        scoringEngine.scoreClassified(alliance, type);
        updateScoreboards();
        updateMatchInfo();
        addEventLog(String.format("%s +3 分 (%s)", alliance.getDisplayName(), type.getDisplayName()));
    }

    /**
     * 更新比賽資訊顯示
     */
    private void updateMatchInfo() {
        MatchState state = scoringEngine.getMatchState();

        timeLabel.setText(state.getTimeString());
        phaseLabel.setText(state.getCurrentPhase().getDisplayName());
        motifLabel.setText(state.getDetectedMotif().getPattern());

        int rampCount = state.getRampArtifacts().size();
        rampLabel.setText(rampCount + "/" + MatchState.RAMP_CAPACITY);
        rampProgressBar.setValue(rampCount);
        rampProgressBar.setString(rampCount + "/" + MatchState.RAMP_CAPACITY);

        // 更新計分板
        updateScoreboards();
    }

    /**
     * 更新計分板
     */
    private void updateScoreboards() {
        redScorePanel.updateScore(scoringEngine.getMatchState().getRedScore());
        blueScorePanel.updateScore(scoringEngine.getMatchState().getBlueScore());
    }

    /**
     * 新增事件記錄
     * /**
     * 偵測紅色和藍色柱子並建立得分區
     */
    private void detectColoredPillars(Mat frame, int frameWidth, int frameHeight) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

        // 紅色 HSV 範圍 (紅色跨越 0 和 180 度)
        Mat redMask1 = new Mat();
        Mat redMask2 = new Mat();
        Core.inRange(hsv, new Scalar(0, 100, 100), new Scalar(10, 255, 255), redMask1);
        Core.inRange(hsv, new Scalar(160, 100, 100), new Scalar(180, 255, 255), redMask2);
        Mat redMask = new Mat();
        Core.add(redMask1, redMask2, redMask);

        // 藍色 HSV 範圍
        Mat blueMask = new Mat();
        Core.inRange(hsv, new Scalar(100, 100, 100), new Scalar(130, 255, 255), blueMask);

        // 型態學處理
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(15, 15));
        Imgproc.morphologyEx(redMask, redMask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(redMask, redMask, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(blueMask, blueMask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(blueMask, blueMask, Imgproc.MORPH_CLOSE, kernel);

        // 尋找紅色柱子 (坡道區)
        Rect redZoneBounds = findLargestColoredRegion(redMask, frameWidth, frameHeight);
        if (redZoneBounds != null) {
            com.telearn.ftc.model.ScoringZone redZone = new com.telearn.ftc.model.ScoringZone(
                    1, "紅方坡道", com.telearn.ftc.model.Alliance.RED, redZoneBounds);
            scoringZones.add(redZone);
            addEventLog("偵測到紅方坡道區 (" + redZoneBounds.x + ", " + redZoneBounds.y + ")");
        }

        // 尋找藍色柱子 (坡道區)
        Rect blueZoneBounds = findLargestColoredRegion(blueMask, frameWidth, frameHeight);
        if (blueZoneBounds != null) {
            com.telearn.ftc.model.ScoringZone blueZone = new com.telearn.ftc.model.ScoringZone(
                    2, "藍方坡道", com.telearn.ftc.model.Alliance.BLUE, blueZoneBounds);
            scoringZones.add(blueZone);
            addEventLog("偵測到藍方坡道區 (" + blueZoneBounds.x + ", " + blueZoneBounds.y + ")");
        }

        if (!scoringZones.isEmpty()) {
            autoScoring = true;
            addEventLog("得分區偵測完成，共 " + scoringZones.size() + " 個區域");
        }

        // 釋放資源
        hsv.release();
        redMask1.release();
        redMask2.release();
        redMask.release();
        blueMask.release();
        kernel.release();
    }

    /**
     * 找出最大的有色區域並建立得分區範圍
     */
    private Rect findLargestColoredRegion(Mat mask, int frameWidth, int frameHeight) {
        List<MatOfPoint> contours = new java.util.ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        double maxArea = 0;
        Rect largestRect = null;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            // 過濾太小的區域 (至少佔畫面 0.5%)
            if (area > frameWidth * frameHeight * 0.005 && area > maxArea) {
                maxArea = area;
                Rect boundingRect = Imgproc.boundingRect(contour);
                // 擴大區域作為坡道檢測範圍
                int expandX = (int) (boundingRect.width * 0.5);
                int expandY = (int) (boundingRect.height * 0.3);
                int newX = Math.max(0, boundingRect.x - expandX);
                int newY = Math.max(0, boundingRect.y - expandY);
                int newWidth = Math.min(frameWidth - newX, boundingRect.width + expandX * 2);
                int newHeight = Math.min(frameHeight - newY, boundingRect.height + expandY * 2);
                largestRect = new Rect(newX, newY, newWidth, newHeight);
            }
        }

        hierarchy.release();
        return largestRect;
    }

    /**
     * 新增事件記錄
     */
    private void addEventLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        eventLogArea.append(String.format("[%s] %s\n", timestamp, message));
        eventLogArea.setCaretPosition(eventLogArea.getDocument().getLength());
    }

    /**
     * 清理資源
     */
    private void cleanup() {
        stopCamera();
        artifactDetector.release();
        robotDetector.release();
        zoneDetector.release();
        aprilTagDetector.release();
        log.info("資源已清理");
    }

    /**
     * 程式進入點
     */
    public static void main(String[] args) {
        // 設定外觀
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.warn("無法設定系統外觀", e);
        }

        SwingUtilities.invokeLater(() -> {
            FTCMainWindow window = new FTCMainWindow();
            window.setVisible(true);
        });
    }
}
