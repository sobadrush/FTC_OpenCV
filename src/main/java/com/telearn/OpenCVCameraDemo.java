package com.telearn;

/**
 * @author RogerLo
 * @date 2025/12/5
 */

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;

/**
 * OpenCV 攝影機控制範例程式
 * 展示如何使用 OpenCV 擷取攝影機影像並顯示在視窗中
 */
@Slf4j
public class OpenCVCameraDemo {

    private VideoCapture camera;
    private JFrame frame;
    private JLabel imageLabel;
    private volatile boolean isRunning = false;
    private String saveDirectory = "capture_photo";
    private FaceDetector faceDetector;
    private volatile boolean isFaceDetectionEnabled = false;

    // 載入 OpenCV 原生函式庫
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public OpenCVCameraDemo() {
        faceDetector = new FaceDetector();
        initializeUI();
    }

    /**
     * 初始化使用者介面
     */
    private void initializeUI() {
        frame = new JFrame("OpenCV 攝影機控制");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        frame.add(imageLabel, BorderLayout.CENTER);

        // 建立控制面板
        JPanel controlPanel = new JPanel();
        JButton startButton = new JButton("開始");
        JButton stopButton = new JButton("停止");
        JButton captureButton = new JButton("拍照");
        JButton setDirButton = new JButton("設定存檔目錄");
        JToggleButton faceDetectToggle = new JToggleButton("人臉偵測: 關");

        startButton.addActionListener(e -> startCamera());
        stopButton.addActionListener(e -> stopCamera());
        captureButton.addActionListener(e -> captureImage());
        setDirButton.addActionListener(e -> chooseSaveDirectory());
        faceDetectToggle.addActionListener(e -> {
            isFaceDetectionEnabled = faceDetectToggle.isSelected();
            faceDetectToggle.setText(isFaceDetectionEnabled ? "人臉偵測: 開" : "人臉偵測: 關");
        });

        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(captureButton);
        controlPanel.add(setDirButton);
        controlPanel.add(faceDetectToggle);
        frame.add(controlPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    /**
     * 選擇存檔目錄
     */
    private void chooseSaveDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("選擇照片存檔目錄");
        
        File currentDir = new File(saveDirectory);
        if (currentDir.exists()) {
            fileChooser.setCurrentDirectory(currentDir);
        } else {
            fileChooser.setCurrentDirectory(new File("."));
        }

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            saveDirectory = selectedFile.getAbsolutePath();
            log.info("存檔目錄已更新為: {}", saveDirectory);
            JOptionPane.showMessageDialog(frame, "存檔目錄已設定為: " + saveDirectory);
        }
    }

    /**
     * 啟動攝影機
     */
    public void startCamera() {
        if (isRunning) {
            log.info("攝影機已經在運作中");
            return;
        }

        // 開啟預設攝影機 (索引 0)
        camera = new VideoCapture(0);

        if (!camera.isOpened()) {
            JOptionPane.showMessageDialog(frame,
                    "無法開啟攝影機！請確認攝影機已連接。",
                    "錯誤",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 設定攝影機解析度
        camera.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
        camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);

        isRunning = true;

        // 在新執行緒中持續擷取影像
        new Thread(() -> {
            Mat frame = new Mat();
            while (isRunning) {
                if (camera.read(frame)) {
                    if (isFaceDetectionEnabled) {
                        faceDetector.detectAndDraw(frame);
                    }
                    updateImageLabel(frame);
                }

                try {
                    Thread.sleep(33); // 約 30 FPS
                } catch (InterruptedException e) {
                    log.error("相機禎數讀取發生錯誤", e);
                }
            }
        }).start();

        log.info("攝影機已啟動");
    }

    /**
     * 停止攝影機
     */
    public void stopCamera() {
        if (!isRunning) {
            return;
        }

        isRunning = false;

        if (camera != null && camera.isOpened()) {
            camera.release();
        }

        log.info("攝影機已停止");
    }

    /**
     * 拍照並儲存
     */
    public void captureImage() {
        if (camera == null || !camera.isOpened()) {
            JOptionPane.showMessageDialog(frame,
                    "請先啟動攝影機！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Mat capturedFrame = new Mat();
        if (camera.read(capturedFrame)) {
            // 確保目錄存在
            File dir = new File(saveDirectory);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filename = "capture_" + System.currentTimeMillis() + ".jpg";
            File fileToSave = new File(dir, filename);
            String absolutePath = fileToSave.getAbsolutePath();

            Imgcodecs.imwrite(absolutePath, capturedFrame);
            log.info("照片已儲存: {}", absolutePath);
            JOptionPane.showMessageDialog(frame,
                    "照片已儲存: " + absolutePath,
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * 更新顯示的影像
     */
    private void updateImageLabel(Mat frame) {
        BufferedImage image = matToBufferedImage(frame);
        ImageIcon icon = new ImageIcon(image);
        imageLabel.setIcon(icon);
    }

    /**
     * 將 OpenCV Mat 轉換為 BufferedImage
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

}