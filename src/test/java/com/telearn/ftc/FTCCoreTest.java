package com.telearn.ftc;

import com.telearn.ftc.model.*;
import com.telearn.ftc.scoring.ScoringEngine;
import com.telearn.ftc.strategy.CycleAnalyzer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * FTC 核心模組單元測試
 */
public class FTCCoreTest {

    private ScoringEngine scoringEngine;
    private CycleAnalyzer cycleAnalyzer;

    @Before
    public void setUp() {
        scoringEngine = new ScoringEngine();
        cycleAnalyzer = new CycleAnalyzer();
    }

    // ========== MatchState 測試 ==========

    @Test
    public void testMatchStateInitialization() {
        MatchState state = scoringEngine.getMatchState();

        assertNotNull("MatchState should not be null", state);
        assertEquals("Initial phase should be PRE_MATCH", MatchPhase.PRE_MATCH, state.getCurrentPhase());
        assertFalse("Match should not be in progress initially", state.isMatchInProgress());
        assertEquals("Ramp should be empty", 0, state.getRampArtifacts().size());
    }

    @Test
    public void testMatchStart() {
        scoringEngine.startMatch();
        MatchState state = scoringEngine.getMatchState();

        assertTrue("Match should be in progress", state.isMatchInProgress());
        assertEquals("Phase should be AUTONOMOUS", MatchPhase.AUTONOMOUS, state.getCurrentPhase());
        assertEquals("Red score should be 0", 0, state.getRedScore().getTotalScore());
        assertEquals("Blue score should be 0", 0, state.getBlueScore().getTotalScore());
    }

    @Test
    public void testRampCapacity() {
        MatchState state = scoringEngine.getMatchState();

        assertEquals("Ramp capacity should be 9", 9, MatchState.RAMP_CAPACITY);
        assertEquals("Max carry per robot should be 3", 3, MatchState.MAX_CARRY_PER_ROBOT);

        // 填滿坡道
        for (int i = 0; i < 9; i++) {
            boolean added = state.addToRamp(ArtifactType.PURPLE);
            assertTrue("Should add artifact " + i, added);
        }

        // 第10顆應該進入溢出
        boolean overflow = state.addToRamp(ArtifactType.GREEN);
        assertFalse("10th artifact should overflow", overflow);
        assertEquals("Overflow count should be 1", 1, state.getOverflowCount());
    }

    // ========== 計分規則測試 ==========

    @Test
    public void testClassifiedScoring() {
        scoringEngine.startMatch();

        scoringEngine.scoreClassified(Alliance.RED, ArtifactType.PURPLE);

        AllianceScore redScore = scoringEngine.getMatchState().getRedScore();
        assertEquals("Classified score should be 3", 3, redScore.getAutoClassifiedScore());
        assertEquals("Total classified should be 1", 1, redScore.getTotalClassified());
    }

    @Test
    public void testLeaveScoring() {
        scoringEngine.startMatch();

        scoringEngine.scoreLeave(Alliance.RED);
        scoringEngine.scoreLeave(Alliance.RED);

        AllianceScore redScore = scoringEngine.getMatchState().getRedScore();
        assertEquals("Leave score should be 6 (2 robots)", 6, redScore.getAutoLeaveScore());
    }

    @Test
    public void testParkingScoring() {
        scoringEngine.startMatch();
        scoringEngine.getMatchState().setCurrentPhase(MatchPhase.ENDGAME);

        scoringEngine.scorePark(Alliance.BLUE, true);
        scoringEngine.scorePark(Alliance.BLUE, true);
        scoringEngine.scoreBothParked(Alliance.BLUE);

        AllianceScore blueScore = scoringEngine.getMatchState().getBlueScore();
        assertEquals("Parking score should be 20", 20, blueScore.getParkingScore());
        assertEquals("Bonus should be 10", 10, blueScore.getParkingBonusScore());
        assertEquals("Endgame subtotal should be 30", 30, blueScore.getEndgameSubtotal());
    }

    @Test
    public void testTotalScoreCalculation() {
        scoringEngine.startMatch();

        // Auto 階段
        scoringEngine.scoreLeave(Alliance.RED); // +3
        scoringEngine.scoreClassified(Alliance.RED, ArtifactType.PURPLE); // +3
        scoringEngine.scoreClassified(Alliance.RED, ArtifactType.GREEN); // +3

        AllianceScore redScore = scoringEngine.getMatchState().getRedScore();
        assertEquals("Total should be 9", 9, redScore.getTotalScore());
        assertEquals("Auto subtotal should be 9", 9, redScore.getAutoSubtotal());
    }

    // ========== Motif 測試 ==========

    @Test
    public void testMotifFromAprilTagId() {
        assertEquals("ID 21 should be GPP", Motif.GPP, Motif.fromAprilTagId(21));
        assertEquals("ID 22 should be PGP", Motif.PGP, Motif.fromAprilTagId(22));
        assertEquals("ID 23 should be PPG", Motif.PPG, Motif.fromAprilTagId(23));
        assertEquals("Unknown ID should be UNKNOWN", Motif.UNKNOWN, Motif.fromAprilTagId(99));
    }

    @Test
    public void testMotifPatternMatching() {
        Motif gpp = Motif.GPP;

        // 符合的序列
        ArtifactType[] matching = {
                ArtifactType.GREEN, ArtifactType.PURPLE, ArtifactType.PURPLE
        };
        assertEquals("GPP pattern should match 3", 3, gpp.countMatches(matching));

        // 不符合的序列
        ArtifactType[] notMatching = {
                ArtifactType.PURPLE, ArtifactType.PURPLE, ArtifactType.GREEN
        };
        assertEquals("PPG pattern should not match GPP", 0, gpp.countMatches(notMatching));
    }

    @Test
    public void testMotifPatternTypes() {
        ArtifactType[] gppTypes = Motif.GPP.getPatternTypes();

        assertEquals("GPP should have 3 types", 3, gppTypes.length);
        assertEquals("First should be GREEN", ArtifactType.GREEN, gppTypes[0]);
        assertEquals("Second should be PURPLE", ArtifactType.PURPLE, gppTypes[1]);
        assertEquals("Third should be PURPLE", ArtifactType.PURPLE, gppTypes[2]);
    }

    // ========== CycleAnalyzer 測試 ==========

    @Test
    public void testCycleAnalyzerInitialization() {
        assertEquals("Initial red cycles should be 0", 0, cycleAnalyzer.getCompletedCycles(Alliance.RED));
        assertEquals("Initial blue cycles should be 0", 0, cycleAnalyzer.getCompletedCycles(Alliance.BLUE));
    }

    @Test
    public void testPerformanceRating() {
        // 無資料時
        String rating = cycleAnalyzer.getPerformanceRating(Alliance.RED);
        assertEquals("No data rating", "無資料", rating);
    }

    @Test
    public void testScorePotential() {
        String potential = cycleAnalyzer.getScorePotential(Alliance.BLUE);
        assertEquals("No data potential", "無法估算", potential);
    }

    // ========== Alliance 測試 ==========

    @Test
    public void testAllianceEnums() {
        assertEquals("Red display name", "紅色聯盟", Alliance.RED.getDisplayName());
        assertEquals("Blue display name", "藍色聯盟", Alliance.BLUE.getDisplayName());
        assertEquals("Red index", 0, Alliance.RED.getIndex());
        assertEquals("Blue index", 1, Alliance.BLUE.getIndex());
    }

    // ========== ArtifactType 測試 ==========

    @Test
    public void testArtifactTypeEnums() {
        assertEquals("Purple code", "P", ArtifactType.PURPLE.getCode());
        assertEquals("Green code", "G", ArtifactType.GREEN.getCode());
        assertEquals("Purple display", "紫色", ArtifactType.PURPLE.getDisplayName());
        assertEquals("Green display", "綠色", ArtifactType.GREEN.getDisplayName());
    }

    // ========== MatchPhase 測試 ==========

    @Test
    public void testMatchPhaseEnums() {
        assertEquals("Auto duration", 30, MatchPhase.AUTONOMOUS.getDurationSeconds());
        assertEquals("TeleOp duration", 120, MatchPhase.TELEOP.getDurationSeconds());
        assertEquals("Endgame duration", 30, MatchPhase.ENDGAME.getDurationSeconds());
    }

    // ========== 整合測試 ==========

    @Test
    public void testFullMatchSimulation() {
        // 開始比賽
        scoringEngine.startMatch();
        scoringEngine.setDetectedMotif(Motif.GPP);

        // Auto 階段 - 雙方都離開
        scoringEngine.scoreLeave(Alliance.RED);
        scoringEngine.scoreLeave(Alliance.RED);
        scoringEngine.scoreLeave(Alliance.BLUE);
        scoringEngine.scoreLeave(Alliance.BLUE);

        // 紅方分類 6 顆
        for (int i = 0; i < 6; i++) {
            ArtifactType type = i % 3 == 0 ? ArtifactType.GREEN : ArtifactType.PURPLE;
            scoringEngine.scoreClassified(Alliance.RED, type);
        }

        // 藍方分類 3 顆
        for (int i = 0; i < 3; i++) {
            scoringEngine.scoreClassified(Alliance.BLUE, ArtifactType.PURPLE);
        }

        // 結束比賽
        scoringEngine.endMatch();

        // 驗證結果
        MatchState state = scoringEngine.getMatchState();
        assertTrue("Red should have higher score",
                state.getRedScore().getTotalScore() > state.getBlueScore().getTotalScore());

        // 紅方: 離開(6) + 分類(18) = 24
        // 藍方: 離開(6) + 分類(9) = 15
        assertTrue("Red score should be >= 24", state.getRedScore().getTotalScore() >= 24);
        assertTrue("Blue score should be >= 15", state.getBlueScore().getTotalScore() >= 15);
    }

    @Test
    public void testScoreEventTracking() {
        scoringEngine.startMatch();
        scoringEngine.scoreClassified(Alliance.RED, ArtifactType.PURPLE);
        scoringEngine.scoreLeave(Alliance.BLUE);

        assertTrue("Should have at least 3 events", scoringEngine.getScoreEvents().size() >= 3);
    }
}
