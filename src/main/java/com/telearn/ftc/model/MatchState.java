package com.telearn.ftc.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 比賽狀態模型
 * 追蹤整場比賽的即時狀態
 */
@Data
public class MatchState {
    /** 當前比賽階段 */
    private MatchPhase currentPhase = MatchPhase.PRE_MATCH;

    /** 比賽剩餘時間 (秒) */
    private int remainingTimeSeconds = 150;

    /** 偵測到的主題 */
    private Motif detectedMotif = Motif.UNKNOWN;

    /** 紅色聯盟分數 */
    private AllianceScore redScore = new AllianceScore(Alliance.RED);

    /** 藍色聯盟分數 */
    private AllianceScore blueScore = new AllianceScore(Alliance.BLUE);

    /** 坡道上的文物 (最多 9 顆) */
    private List<ArtifactType> rampArtifacts = new ArrayList<>();

    /** 溢出區文物數量 */
    private int overflowCount = 0;

    /** 比賽是否進行中 */
    private boolean matchInProgress = false;

    /** 比賽開始時間戳 */
    private long matchStartTimestamp;

    /** 坡道最大容量 */
    public static final int RAMP_CAPACITY = 9;

    /** 每台機器人最大持有量 */
    public static final int MAX_CARRY_PER_ROBOT = 3;

    /**
     * 開始比賽
     */
    public void startMatch() {
        this.matchInProgress = true;
        this.matchStartTimestamp = System.currentTimeMillis();
        this.currentPhase = MatchPhase.AUTONOMOUS;
        this.remainingTimeSeconds = 150;
        this.redScore.reset();
        this.blueScore.reset();
        this.rampArtifacts.clear();
        this.overflowCount = 0;
    }

    /**
     * 更新比賽時間
     */
    public void updateTime() {
        if (!matchInProgress)
            return;

        long elapsed = (System.currentTimeMillis() - matchStartTimestamp) / 1000;
        this.remainingTimeSeconds = Math.max(0, 150 - (int) elapsed);

        // 更新階段
        if (elapsed < 30) {
            this.currentPhase = MatchPhase.AUTONOMOUS;
        } else if (elapsed < 120) {
            this.currentPhase = MatchPhase.TELEOP;
        } else if (elapsed < 150) {
            this.currentPhase = MatchPhase.ENDGAME;
        } else {
            this.currentPhase = MatchPhase.POST_MATCH;
            this.matchInProgress = false;
        }
    }

    /**
     * 新增文物到坡道
     * 
     * @return 是否成功新增 (坡道未滿)
     */
    public boolean addToRamp(ArtifactType artifact) {
        if (rampArtifacts.size() < RAMP_CAPACITY) {
            rampArtifacts.add(artifact);
            return true;
        } else {
            overflowCount++;
            return false;
        }
    }

    /**
     * 清空坡道 (開閘)
     */
    public void clearRamp() {
        rampArtifacts.clear();
    }

    /**
     * 計算當前坡道上符合主題的圖案數量
     */
    public int calculatePatternScore() {
        if (detectedMotif == Motif.UNKNOWN || rampArtifacts.isEmpty()) {
            return 0;
        }
        ArtifactType[] arr = rampArtifacts.toArray(new ArtifactType[0]);
        return detectedMotif.countMatches(arr);
    }

    /**
     * 取得指定聯盟的分數
     */
    public AllianceScore getScore(Alliance alliance) {
        return alliance == Alliance.RED ? redScore : blueScore;
    }

    /**
     * 是否在圖案快照時間點
     * (Auto 結束或比賽結束)
     */
    public boolean isPatternSnapshotTime() {
        return currentPhase == MatchPhase.AUTONOMOUS && remainingTimeSeconds <= 1
                || currentPhase == MatchPhase.POST_MATCH;
    }

    /**
     * 取得比賽時間字串 (MM:SS 格式)
     */
    public String getTimeString() {
        int minutes = remainingTimeSeconds / 60;
        int seconds = remainingTimeSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
