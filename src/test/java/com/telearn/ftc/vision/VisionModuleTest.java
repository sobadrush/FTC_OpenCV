package com.telearn.ftc.vision;

import com.telearn.ftc.model.ArtifactType;
import com.telearn.ftc.model.Motif;
import org.junit.Before;
import org.junit.Test;
import org.opencv.core.*;

import static org.junit.Assert.*;

/**
 * 視覺模組單元測試
 * 注意: 這些測試需要 OpenCV 原生函式庫
 */
public class VisionModuleTest {

    private ArtifactDetector artifactDetector;
    private RobotDetector robotDetector;
    private ZoneDetector zoneDetector;
    private AprilTagDetector aprilTagDetector;

    private static boolean openCVLoaded = false;

    @Before
    public void setUp() {
        // 嘗試載入 OpenCV
        if (!openCVLoaded) {
            try {
                nu.pattern.OpenCV.loadLocally();
                openCVLoaded = true;
            } catch (Exception e) {
                System.out.println("OpenCV not available, skipping vision tests");
            }
        }

        if (openCVLoaded) {
            artifactDetector = new ArtifactDetector();
            robotDetector = new RobotDetector();
            zoneDetector = new ZoneDetector();
            aprilTagDetector = new AprilTagDetector();
        }
    }

    // ========== ArtifactDetector 測試 ==========

    @Test
    public void testArtifactDetectorInitialization() {
        if (!openCVLoaded)
            return;

        assertNotNull("ArtifactDetector should be created", artifactDetector);
    }

    @Test
    public void testDetectEmptyFrame() {
        if (!openCVLoaded)
            return;

        Mat emptyFrame = new Mat();
        var artifacts = artifactDetector.detect(emptyFrame);

        assertNotNull("Should return empty list, not null", artifacts);
        assertTrue("Should be empty for empty frame", artifacts.isEmpty());

        emptyFrame.release();
    }

    @Test
    public void testDetectWithValidFrame() {
        if (!openCVLoaded)
            return;

        // 建立測試影像 (640x480 黑色)
        Mat testFrame = Mat.zeros(480, 640, CvType.CV_8UC3);

        var artifacts = artifactDetector.detect(testFrame);

        assertNotNull("Should return list", artifacts);
        // 黑色影像不應該有偵測結果
        assertTrue("Black frame should have no artifacts", artifacts.isEmpty());

        testFrame.release();
    }

    @Test
    public void testPurpleMaskGeneration() {
        if (!openCVLoaded)
            return;

        Mat testFrame = Mat.zeros(100, 100, CvType.CV_8UC3);
        artifactDetector.detect(testFrame);

        Mat purpleMask = artifactDetector.getPurpleMask();
        assertNotNull("Purple mask should not be null", purpleMask);
        assertFalse("Purple mask should not be empty", purpleMask.empty());

        testFrame.release();
        purpleMask.release();
    }

    @Test
    public void testArtifactDetectorRelease() {
        if (!openCVLoaded)
            return;

        // 不應該拋出例外
        artifactDetector.release();
    }

    // ========== RobotDetector 測試 ==========

    @Test
    public void testRobotDetectorInitialization() {
        if (!openCVLoaded)
            return;

        assertNotNull("RobotDetector should be created", robotDetector);
    }

    @Test
    public void testRobotDetectEmptyFrame() {
        if (!openCVLoaded)
            return;

        Mat emptyFrame = new Mat();
        var robots = robotDetector.detect(emptyFrame);

        assertNotNull("Should return empty list", robots);
        assertTrue("Should be empty", robots.isEmpty());

        emptyFrame.release();
    }

    @Test
    public void testRobotDetectorRelease() {
        if (!openCVLoaded)
            return;

        robotDetector.release();
    }

    // ========== ZoneDetector 測試 ==========

    @Test
    public void testZoneDetectorInitialization() {
        if (!openCVLoaded)
            return;

        assertNotNull("ZoneDetector should be created", zoneDetector);
    }

    @Test
    public void testGetAllZones() {
        if (!openCVLoaded)
            return;

        var zones = zoneDetector.getAllZones();

        assertNotNull("Zones should not be null", zones);
        assertTrue("Should have predefined zones", zones.size() > 0);
    }

    @Test
    public void testZoneContains() {
        if (!openCVLoaded)
            return;

        var zones = zoneDetector.getAllZones();

        // 找紅方裝載區
        for (var zone : zones) {
            if (zone.getName().contains("紅方裝載區")) {
                // 測試區域內的點
                Point inside = new Point(50, 430);
                assertTrue("Should contain point inside", zone.contains(inside));

                // 測試區域外的點
                Point outside = new Point(500, 100);
                assertFalse("Should not contain point outside", zone.contains(outside));
                break;
            }
        }
    }

    @Test
    public void testUpdateFieldSize() {
        if (!openCVLoaded)
            return;

        int oldZoneCount = zoneDetector.getAllZones().size();
        zoneDetector.updateFieldSize(800, 600);
        int newZoneCount = zoneDetector.getAllZones().size();

        assertEquals("Zone count should be same after resize", oldZoneCount, newZoneCount);
    }

    @Test
    public void testCalibrationStatus() {
        if (!openCVLoaded)
            return;

        assertFalse("Should not be calibrated initially", zoneDetector.isCalibrated());
    }

    // ========== AprilTagDetector 測試 ==========

    @Test
    public void testAprilTagDetectorInitialization() {
        if (!openCVLoaded)
            return;

        assertNotNull("AprilTagDetector should be created", aprilTagDetector);
        // 初始化可能成功或失敗，取決於 OpenCV 版本
    }

    @Test
    public void testAprilTagDetectEmptyFrame() {
        if (!openCVLoaded)
            return;

        Mat emptyFrame = new Mat();
        var tags = aprilTagDetector.detect(emptyFrame);

        assertNotNull("Should return list", tags);

        emptyFrame.release();
    }

    @Test
    public void testAprilTagMotifDetection() {
        if (!openCVLoaded)
            return;

        Mat testFrame = Mat.zeros(480, 640, CvType.CV_8UC3);
        var motif = aprilTagDetector.detectMotif(testFrame);

        // 黑色影像不應該偵測到主題
        assertTrue("Should be empty or UNKNOWN", motif.isEmpty() || motif.get() == Motif.UNKNOWN);

        testFrame.release();
    }

    @Test
    public void testAprilTagLastDetectedMotif() {
        if (!openCVLoaded)
            return;

        assertEquals("Initial motif should be UNKNOWN", Motif.UNKNOWN, aprilTagDetector.getLastDetectedMotif());
    }

    @Test
    public void testAprilTagReset() {
        if (!openCVLoaded)
            return;

        aprilTagDetector.reset();
        assertEquals("Motif should be UNKNOWN after reset", Motif.UNKNOWN, aprilTagDetector.getLastDetectedMotif());
        assertEquals("Last detection time should be 0", 0, aprilTagDetector.getLastDetectionTime());
    }

    @Test
    public void testAprilTagRelease() {
        if (!openCVLoaded)
            return;

        aprilTagDetector.release();
    }
}
