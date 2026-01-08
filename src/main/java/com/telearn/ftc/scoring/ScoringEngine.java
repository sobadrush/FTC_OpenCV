package com.telearn.ftc.scoring;

import com.telearn.ftc.model.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 計分引擎
 * 實作所有 FTC 2025 計分規則
 */
@Slf4j
public class ScoringEngine {

    // ===== 計分常數 =====
    public static final int SCORE_LEAVE = 3; // 機器人離開得分
    public static final int SCORE_CLASSIFIED = 3; // 分類得分
    public static final int SCORE_OVERFLOW = 1; // 溢出得分
    public static final int SCORE_PATTERN = 2; // 圖案加分 (每顆)
    public static final int SCORE_PARK = 10; // 停車得分
    public static final int SCORE_BOTH_PARKED = 10; // 雙機停車獎勵

    // 比賽狀態
    @Getter
    private MatchState matchState;

    // 事件紀錄
    private List<ScoreEvent> scoreEvents = new ArrayList<>();

    public ScoringEngine() {
        this.matchState = new MatchState();
        log.info("計分引擎初始化完成");
    }

    /**
     * 處理文物分類事件
     */
    public void scoreClassified(Alliance alliance, ArtifactType type) {
        AllianceScore score = matchState.getScore(alliance);
        boolean isAuto = matchState.getCurrentPhase() == MatchPhase.AUTONOMOUS;

        score.addClassified(1, isAuto);

        // 嘗試新增到坡道
        boolean addedToRamp = matchState.addToRamp(type);

        ScoreEvent event = new ScoreEvent(
                ScoreEvent.EventType.CLASSIFY,
                alliance,
                SCORE_CLASSIFIED,
                String.format("%s 文物分類 (%s)",
                        alliance.getDisplayName(),
                        type.getDisplayName()));
        scoreEvents.add(event);

        if (!addedToRamp) {
            // 坡道已滿，計入溢出
            score.addOverflow(1);
            log.info("坡道已滿，文物溢出");
        }

        log.info("記錄分類得分: {} +{} (坡道: {}/{})",
                alliance, SCORE_CLASSIFIED,
                matchState.getRampArtifacts().size(),
                MatchState.RAMP_CAPACITY);
    }

    /**
     * 處理機器人離開事件 (Auto)
     */
    public void scoreLeave(Alliance alliance) {
        if (matchState.getCurrentPhase() != MatchPhase.AUTONOMOUS) {
            log.warn("離開得分僅限自主階段");
            return;
        }

        AllianceScore score = matchState.getScore(alliance);
        score.addLeave(1);

        ScoreEvent event = new ScoreEvent(
                ScoreEvent.EventType.LEAVE,
                alliance,
                SCORE_LEAVE,
                alliance.getDisplayName() + " 機器人離開");
        scoreEvents.add(event);

        log.info("記錄離開得分: {} +{}", alliance, SCORE_LEAVE);
    }

    /**
     * 處理停車事件 (Endgame)
     */
    public void scorePark(Alliance alliance, boolean fullyReturned) {
        AllianceScore score = matchState.getScore(alliance);

        // 簡化處理：每次呼叫視為一台機器人停車
        score.addParking(1, false);

        ScoreEvent event = new ScoreEvent(
                ScoreEvent.EventType.PARK,
                alliance,
                SCORE_PARK,
                alliance.getDisplayName() + (fullyReturned ? " 完全返回基地" : " 停車"));
        scoreEvents.add(event);

        log.info("記錄停車得分: {} +{}", alliance, SCORE_PARK);
    }

    /**
     * 處理雙機停車獎勵
     */
    public void scoreBothParked(Alliance alliance) {
        AllianceScore score = matchState.getScore(alliance);
        score.addParking(0, true);

        ScoreEvent event = new ScoreEvent(
                ScoreEvent.EventType.BONUS,
                alliance,
                SCORE_BOTH_PARKED,
                alliance.getDisplayName() + " 雙機完全返回獎勵");
        scoreEvents.add(event);

        log.info("記錄雙機獎勵: {} +{}", alliance, SCORE_BOTH_PARKED);
    }

    /**
     * 觸發圖案快照 (Auto 結束或比賽結束)
     */
    public void triggerPatternSnapshot(Alliance alliance) {
        int patternMatches = matchState.calculatePatternScore();

        if (patternMatches > 0) {
            AllianceScore score = matchState.getScore(alliance);
            boolean isAuto = matchState.getCurrentPhase() == MatchPhase.AUTONOMOUS;
            score.addPatternScore(patternMatches, isAuto);

            ScoreEvent event = new ScoreEvent(
                    ScoreEvent.EventType.PATTERN,
                    alliance,
                    patternMatches * SCORE_PATTERN,
                    String.format("%s 圖案加分 (%d顆符合)",
                            alliance.getDisplayName(), patternMatches));
            scoreEvents.add(event);

            log.info("記錄圖案得分: {} +{} ({}顆符合)",
                    alliance, patternMatches * SCORE_PATTERN, patternMatches);
        }
    }

    /**
     * 清空坡道 (開閘)
     */
    public void clearRamp() {
        int clearedCount = matchState.getRampArtifacts().size();
        matchState.clearRamp();

        ScoreEvent event = new ScoreEvent(
                ScoreEvent.EventType.CLEAR_RAMP,
                null,
                0,
                String.format("坡道清空 (%d顆)", clearedCount));
        scoreEvents.add(event);

        log.info("坡道已清空: {}顆文物", clearedCount);
    }

    /**
     * 設定偵測到的主題
     */
    public void setDetectedMotif(Motif motif) {
        matchState.setDetectedMotif(motif);
        log.info("設定主題: {}", motif.getPattern());
    }

    /**
     * 開始比賽
     */
    public void startMatch() {
        matchState.startMatch();
        scoreEvents.clear();

        ScoreEvent event = new ScoreEvent(
                ScoreEvent.EventType.MATCH_START,
                null,
                0,
                "比賽開始");
        scoreEvents.add(event);

        log.info("比賽開始");
    }

    /**
     * 結束比賽
     */
    public void endMatch() {
        matchState.setMatchInProgress(false);
        matchState.setCurrentPhase(MatchPhase.POST_MATCH);

        // 觸發終局圖案快照
        triggerPatternSnapshot(Alliance.RED);
        triggerPatternSnapshot(Alliance.BLUE);

        ScoreEvent event = new ScoreEvent(
                ScoreEvent.EventType.MATCH_END,
                null,
                0,
                String.format("比賽結束 - 紅:%d 藍:%d",
                        matchState.getRedScore().getTotalScore(),
                        matchState.getBlueScore().getTotalScore()));
        scoreEvents.add(event);

        log.info("比賽結束 - 紅:{} 藍:{}",
                matchState.getRedScore().getTotalScore(),
                matchState.getBlueScore().getTotalScore());
    }

    /**
     * 更新比賽時間
     */
    public void updateTime() {
        MatchPhase previousPhase = matchState.getCurrentPhase();
        matchState.updateTime();

        // 檢查階段變化
        if (previousPhase != matchState.getCurrentPhase()) {
            log.info("階段切換: {} -> {}",
                    previousPhase.getDisplayName(),
                    matchState.getCurrentPhase().getDisplayName());

            // Auto 結束時觸發圖案快照
            if (previousPhase == MatchPhase.AUTONOMOUS) {
                triggerPatternSnapshot(Alliance.RED);
                triggerPatternSnapshot(Alliance.BLUE);
            }
        }
    }

    /**
     * 取得目前紅藍分數差
     */
    public int getScoreDifference() {
        return matchState.getRedScore().getTotalScore()
                - matchState.getBlueScore().getTotalScore();
    }

    /**
     * 取得所有得分事件
     */
    public List<ScoreEvent> getScoreEvents() {
        return new ArrayList<>(scoreEvents);
    }

    /**
     * 取得最近 N 個事件
     */
    public List<ScoreEvent> getRecentEvents(int count) {
        int start = Math.max(0, scoreEvents.size() - count);
        return new ArrayList<>(scoreEvents.subList(start, scoreEvents.size()));
    }

    /**
     * 模擬預估最終分數 (基於目前趨勢)
     */
    public int estimateFinalScore(Alliance alliance) {
        AllianceScore score = matchState.getScore(alliance);
        int currentScore = score.getTotalScore();

        // 根據剩餘時間估算
        int remainingTime = matchState.getRemainingTimeSeconds();
        double elapsedTime = 150 - remainingTime;

        if (elapsedTime <= 0) {
            return currentScore;
        }

        // 計算每秒得分率
        double scoreRate = currentScore / elapsedTime;

        // 預估終局分數 (假設停車成功)
        int estimatedEndgame = 20; // 一台停車 + 可能的獎勵

        return (int) (currentScore + scoreRate * remainingTime + estimatedEndgame);
    }

    /**
     * 取得比賽摘要
     */
    public String getMatchSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 比賽摘要 ===\n");
        sb.append(String.format("時間: %s (%s)\n",
                matchState.getTimeString(),
                matchState.getCurrentPhase().getDisplayName()));
        sb.append(String.format("主題: %s\n",
                matchState.getDetectedMotif().getPattern()));
        sb.append(String.format("坡道: %d/%d\n",
                matchState.getRampArtifacts().size(),
                MatchState.RAMP_CAPACITY));
        sb.append("\n--- 紅色聯盟 ---\n");
        sb.append(matchState.getRedScore().getSummary()).append("\n");
        sb.append("\n--- 藍色聯盟 ---\n");
        sb.append(matchState.getBlueScore().getSummary()).append("\n");

        return sb.toString();
    }
}
