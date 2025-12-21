package com.telearn.ftc.ui;

import com.telearn.ftc.model.*;
import com.telearn.ftc.scoring.ScoringEngine;
import com.telearn.ftc.strategy.CycleAnalyzer;
import com.telearn.ftc.util.MatchDataExporter;
import com.telearn.ftc.vision.*;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
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
    private Thread captureThread;

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

    // 視窗尺寸
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;

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

        overlayControlPanel.add(showArtifactsChk);
        overlayControlPanel.add(showRobotsChk);
        overlayControlPanel.add(showZonesChk);
        overlayControlPanel.add(showAprilTagsChk);
        overlayControlPanel.add(autoDetectMotifChk);

        panel.add(overlayControlPanel, BorderLayout.SOUTH);

        return panel;
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

        // 攝影機控制
        JButton startCameraBtn = new JButton("▶ 啟動攝影機");
        startCameraBtn.addActionListener(e -> startCamera());

        JButton stopCameraBtn = new JButton("⏹ 停止攝影機");
        stopCameraBtn.addActionListener(e -> stopCamera());

        JButton captureBtn = new JButton("📷 擷取畫面");
        captureBtn.addActionListener(e -> captureImage());

        panel.add(startCameraBtn);
        panel.add(stopCameraBtn);
        panel.add(captureBtn);

        panel.add(new JSeparator(SwingConstants.VERTICAL));

        // 比賽控制
        JButton startMatchBtn = new JButton("🏁 開始比賽");
        startMatchBtn.addActionListener(e -> startMatch());

        JButton endMatchBtn = new JButton("🔚 結束比賽");
        endMatchBtn.addActionListener(e -> endMatch());

        JButton clearRampBtn = new JButton("🚿 清空坡道");
        clearRampBtn.addActionListener(e -> clearRamp());

        panel.add(startMatchBtn);
        panel.add(endMatchBtn);
        panel.add(clearRampBtn);

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
     * 啟動攝影機
     */
    private void startCamera() {
        if (isRunning) {
            log.info("攝影機已在運作中");
            return;
        }

        camera = new VideoCapture(0);

        if (!camera.isOpened()) {
            JOptionPane.showMessageDialog(this,
                    "無法開啟攝影機！請確認攝影機已連接。",
                    "錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }

        camera.set(Videoio.CAP_PROP_FRAME_WIDTH, VIDEO_WIDTH);
        camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, VIDEO_HEIGHT);

        isRunning = true;

        captureThread = new Thread(this::captureLoop);
        captureThread.start();

        log.info("攝影機已啟動");
    }

    /**
     * 影像擷取迴圈
     */
    private void captureLoop() {
        Mat frame = new Mat();

        while (isRunning) {
            if (camera.read(frame)) {
                processFrame(frame);
                updateVideoLabel(frame);
            }

            try {
                Thread.sleep(33); // ~30 FPS
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
        // 偵測文物
        if (showArtifacts) {
            List<DetectedArtifact> artifacts = artifactDetector.detect(frame);
            artifactDetector.drawDetections(frame, artifacts);

            // 自動計分 (如果啟用)
            if (autoScoring && scoringEngine.getMatchState().isMatchInProgress()) {
                // TODO: 實作基於位置的自動計分邏輯
            }
        }

        // 偵測機器人
        if (showRobots) {
            List<DetectedRobot> robots = robotDetector.detect(frame);
            robotDetector.drawDetections(frame, robots);
        }

        // 顯示區域
        if (showZones) {
            zoneDetector.drawZones(frame, true);
        }

        // AprilTag 偵測與主題自動識別
        if (showAprilTags || autoDetectMotif) {
            List<AprilTagDetector.TagDetection> tags = aprilTagDetector.detect(frame);

            if (showAprilTags) {
                aprilTagDetector.drawDetections(frame, tags);
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
     * 更新影像顯示
     */
    private void updateVideoLabel(Mat frame) {
        BufferedImage image = matToBufferedImage(frame);
        ImageIcon icon = new ImageIcon(image);
        SwingUtilities.invokeLater(() -> videoLabel.setIcon(icon));
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
