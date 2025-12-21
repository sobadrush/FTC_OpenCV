package com.telearn.ftc.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.telearn.ftc.model.*;
import com.telearn.ftc.scoring.ScoreEvent;
import com.telearn.ftc.scoring.ScoringEngine;
import com.telearn.ftc.strategy.CycleAnalyzer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 比賽資料匯出工具
 * 支援 JSON 和 CSV 格式匯出
 */
@Slf4j
public class MatchDataExporter {

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();

    /**
     * 匯出資料結構
     */
    @Data
    public static class MatchExportData {
        private String exportTime;
        private String matchDuration;
        private String detectedMotif;
        private AllianceData redAlliance;
        private AllianceData blueAlliance;
        private List<EventData> events;
    }

    @Data
    public static class AllianceData {
        private String name;
        private int totalScore;
        private int autoScore;
        private int teleOpScore;
        private int endgameScore;
        private int totalClassified;
        private int totalOverflow;
        private double avgCycleTime;
        private String performanceRating;
        private int completedCycles;
    }

    @Data
    public static class EventData {
        private String timestamp;
        private String type;
        private String alliance;
        private int points;
        private String description;
    }

    /**
     * 匯出比賽資料為 JSON
     */
    public static String exportToJson(ScoringEngine engine, CycleAnalyzer analyzer) {
        MatchExportData data = buildExportData(engine, analyzer);
        return gson.toJson(data);
    }

    /**
     * 匯出比賽資料到 JSON 檔案
     */
    public static void exportToJsonFile(ScoringEngine engine, CycleAnalyzer analyzer, String directory) {
        String json = exportToJson(engine, analyzer);
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = String.format("%s/match_data_%s.json", directory, timestamp);

        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(json);
            log.info("比賽資料已匯出: {}", filename);
        } catch (IOException e) {
            log.error("匯出失敗: {}", e.getMessage());
        }
    }

    /**
     * 匯出比賽資料為 CSV
     */
    public static String exportToCsv(ScoringEngine engine, CycleAnalyzer analyzer) {
        MatchState state = engine.getMatchState();
        StringBuilder sb = new StringBuilder();

        // 標題列
        sb.append("項目,紅色聯盟,藍色聯盟\n");

        // 總分
        sb.append("總分,").append(state.getRedScore().getTotalScore())
                .append(",").append(state.getBlueScore().getTotalScore()).append("\n");

        // Auto 分數
        sb.append("Auto小計,").append(state.getRedScore().getAutoSubtotal())
                .append(",").append(state.getBlueScore().getAutoSubtotal()).append("\n");

        // TeleOp 分數
        sb.append("TeleOp小計,").append(state.getRedScore().getTeleOpSubtotal())
                .append(",").append(state.getBlueScore().getTeleOpSubtotal()).append("\n");

        // Endgame 分數
        sb.append("Endgame小計,").append(state.getRedScore().getEndgameSubtotal())
                .append(",").append(state.getBlueScore().getEndgameSubtotal()).append("\n");

        // 文物數量
        sb.append("已分類文物,").append(state.getRedScore().getTotalClassified())
                .append(",").append(state.getBlueScore().getTotalClassified()).append("\n");

        // 週期時間
        CycleAnalyzer.CycleMetrics redMetrics = analyzer.getAverageMetrics(Alliance.RED);
        CycleAnalyzer.CycleMetrics blueMetrics = analyzer.getAverageMetrics(Alliance.BLUE);

        sb.append("平均週期(秒),")
                .append(String.format("%.1f", redMetrics.getTotalCycleTime())).append(",")
                .append(String.format("%.1f", blueMetrics.getTotalCycleTime())).append("\n");

        // 效能評級
        sb.append("效能評級,")
                .append(analyzer.getPerformanceRating(Alliance.RED)).append(",")
                .append(analyzer.getPerformanceRating(Alliance.BLUE)).append("\n");

        return sb.toString();
    }

    /**
     * 匯出比賽資料到 CSV 檔案
     */
    public static void exportToCsvFile(ScoringEngine engine, CycleAnalyzer analyzer, String directory) {
        String csv = exportToCsv(engine, analyzer);
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = String.format("%s/match_data_%s.csv", directory, timestamp);

        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(csv);
            log.info("比賽資料已匯出: {}", filename);
        } catch (IOException e) {
            log.error("匯出失敗: {}", e.getMessage());
        }
    }

    /**
     * 建立匯出資料結構
     */
    private static MatchExportData buildExportData(ScoringEngine engine, CycleAnalyzer analyzer) {
        MatchExportData data = new MatchExportData();
        MatchState state = engine.getMatchState();

        data.setExportTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        data.setMatchDuration(state.getTimeString());
        data.setDetectedMotif(state.getDetectedMotif().getPattern());

        // 紅色聯盟
        data.setRedAlliance(buildAllianceData(Alliance.RED, state.getRedScore(), analyzer));

        // 藍色聯盟
        data.setBlueAlliance(buildAllianceData(Alliance.BLUE, state.getBlueScore(), analyzer));

        // 事件列表
        data.setEvents(engine.getScoreEvents().stream()
                .map(MatchDataExporter::buildEventData)
                .toList());

        return data;
    }

    private static AllianceData buildAllianceData(Alliance alliance, AllianceScore score, CycleAnalyzer analyzer) {
        AllianceData data = new AllianceData();

        data.setName(alliance.getDisplayName());
        data.setTotalScore(score.getTotalScore());
        data.setAutoScore(score.getAutoSubtotal());
        data.setTeleOpScore(score.getTeleOpSubtotal());
        data.setEndgameScore(score.getEndgameSubtotal());
        data.setTotalClassified(score.getTotalClassified());
        data.setTotalOverflow(score.getTotalOverflow());

        CycleAnalyzer.CycleMetrics metrics = analyzer.getAverageMetrics(alliance);
        data.setAvgCycleTime(metrics.getTotalCycleTime());
        data.setPerformanceRating(analyzer.getPerformanceRating(alliance));
        data.setCompletedCycles(analyzer.getCompletedCycles(alliance));

        return data;
    }

    private static EventData buildEventData(ScoreEvent event) {
        EventData data = new EventData();

        data.setTimestamp(new SimpleDateFormat("HH:mm:ss").format(new Date(event.getTimestamp())));
        data.setType(event.getType().getDisplayName());
        data.setAlliance(event.getAlliance() != null ? event.getAlliance().name() : "N/A");
        data.setPoints(event.getPoints());
        data.setDescription(event.getDescription());

        return data;
    }
}
