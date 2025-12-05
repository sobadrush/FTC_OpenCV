package com.nanshan;

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

    // 載入 OpenCV 原生函式庫
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public OpenCVCameraDemo() {
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

        startButton.addActionListener(e -> startCamera());
        stopButton.addActionListener(e -> stopCamera());
        captureButton.addActionListener(e -> captureImage());

        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(captureButton);
        frame.add(controlPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
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
            String filename = "capture_" + System.currentTimeMillis() + ".jpg";
            Imgcodecs.imwrite(filename, capturedFrame);
            log.info("照片已儲存: {}", filename);
            JOptionPane.showMessageDialog(frame,
                    "照片已儲存: " + filename,
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

    /**
     * 主程式進入點
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            OpenCVCameraDemo demo = new OpenCVCameraDemo();
        });
    }
}