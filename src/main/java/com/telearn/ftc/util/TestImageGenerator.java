package com.telearn.ftc.util;

import com.telearn.ftc.model.Motif;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;

public class TestImageGenerator {

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public static void main(String[] args) {
        generateTestImage("ftc_test_field.jpg");
    }

    public static void generateTestImage(String filename) {
        // Create 640x480 white image
        Mat image = new Mat(480, 640, CvType.CV_8UC3, new Scalar(255, 255, 255));

        // 1. Green Artifact (Circle) - Pure Green
        // RGB(0, 255, 0) -> BGR(0, 255, 0)
        Imgproc.circle(image, new Point(150, 240), 40, new Scalar(0, 255, 0), -1);
        Imgproc.putText(image, "Green", new Point(120, 190), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 0), 1);

        // 2. Purple Artifact (Circle) - Magenta
        // RGB(255, 0, 255) -> BGR(255, 0, 255)
        Imgproc.circle(image, new Point(300, 240), 40, new Scalar(255, 0, 255), -1);
        Imgproc.putText(image, "Purple", new Point(270, 190), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 0),
                1);

        // 3. Blue Robot (Rectangle) - Pure Blue
        // RGB(0, 0, 255) -> BGR(255, 0, 0)
        Imgproc.rectangle(image, new Point(450, 200), new Point(530, 280), new Scalar(255, 0, 0), -1);
        Imgproc.putText(image, "Blue Bot", new Point(450, 190), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 0, 0),
                1);

        // 4. ArUco Marker (AprilTag 36h11 ID 21)
        try {
            Dictionary dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_APRILTAG_36h11);
            Mat markerImage = new Mat();
            Objdetect.generateImageMarker(dictionary, 21, 100, markerImage);

            // Convert grayscale marker to BGR
            Mat markerColor = new Mat();
            Imgproc.cvtColor(markerImage, markerColor, Imgproc.COLOR_GRAY2BGR);

            // Region of Interest (ROI) to copy marker
            Mat roi = image.submat(new Rect(270, 350, 100, 100));
            markerColor.copyTo(roi);

            Imgproc.putText(image, "ID 21 (GPP)", new Point(270, 340), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5,
                    new Scalar(0, 0, 0), 1);

            System.out.println("Generated ArUco marker ID 21 successfully.");
        } catch (Exception e) {
            System.err.println("Failed to generate ArUco marker: " + e.getMessage());
            Imgproc.putText(image, "ArUco Fail", new Point(270, 400), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5,
                    new Scalar(0, 0, 255), 1);
        }

        // Save
        Imgcodecs.imwrite(filename, image);
        System.out.println("Test image saved to: " + filename);

        image.release();
    }
}
