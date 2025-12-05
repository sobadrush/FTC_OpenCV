package com.telearn;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import java.io.File;

/**
 * 使用 OpenCV DNN 模組進行人臉偵測
 * 模型採用 ResNet-10 架構 (SSD 框架)
 */
@Slf4j
public class FaceDetector {

    private Net net;
    private static final String PROTO_FILE = "src/main/resources/models/deploy.prototxt";
    private static final String MODEL_FILE = "src/main/resources/models/res10_300x300_ssd_iter_140000.caffemodel";
    private static final double CONFIDENCE_THRESHOLD = 0.5;

    public FaceDetector() {
        loadModel();
    }

    private void loadModel() {
        File proto = new File(PROTO_FILE);
        File model = new File(MODEL_FILE);

        if (!proto.exists() || !model.exists()) {
            log.error("找不到模型檔案！請確認 {} 與 {} 是否存在", PROTO_FILE, MODEL_FILE);
            return;
        }

        try {
            net = Dnn.readNetFromCaffe(PROTO_FILE, MODEL_FILE);
            log.info("人臉偵測模型載入成功");
        } catch (Exception e) {
            log.error("模型載入失敗", e);
        }
    }

    /**
     * 偵測人臉並在影像上繪製框線
     * @param frame 原始影像 (會被直接修改)
     */
    public void detectAndDraw(Mat frame) {
        if (net == null || net.empty()) {
            return;
        }

        // 建立 blob
        // 300x300 是此模型的輸入尺寸
        // 1.0 是縮放因子 (scale factor)
        // new Scalar(104.0, 177.0, 123.0) 是平均值減法 (mean subtraction) 的數值
        Mat blob = Dnn.blobFromImage(frame, 1.0, new Size(300, 300), 
                                     new Scalar(104.0, 177.0, 123.0), false, false);

        net.setInput(blob);
        Mat detections = net.forward();

        // 解析偵測結果
        // detections 形狀為 [1, 1, N, 7]
        // N 是偵測到的物件數量
        // 7 個值分別為: [batchId, classId, confidence, left, top, right, bottom]
        int cols = frame.cols();
        int rows = frame.rows();

        Mat detectionMat = detections.reshape(1, (int) detections.total() / 7);

        for (int i = 0; i < detectionMat.rows(); i++) {
            double confidence = detectionMat.get(i, 2)[0];

            if (confidence > CONFIDENCE_THRESHOLD) {
                int x1 = (int) (detectionMat.get(i, 3)[0] * cols);
                int y1 = (int) (detectionMat.get(i, 4)[0] * rows);
                int x2 = (int) (detectionMat.get(i, 5)[0] * cols);
                int y2 = (int) (detectionMat.get(i, 6)[0] * rows);

                // 繪製矩形
                Imgproc.rectangle(frame, new Point(x1, y1), new Point(x2, y2), new Scalar(0, 255, 0), 2);
                
                // 繪製信心度
                String label = String.format("%.2f", confidence);
                Imgproc.putText(frame, label, new Point(x1, y1 - 10), 
                                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 255, 0), 2);
            }
        }
    }
}
