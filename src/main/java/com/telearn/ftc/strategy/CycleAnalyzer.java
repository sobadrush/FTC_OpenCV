package com.telearn.ftc.strategy;

import com.telearn.ftc.model.Alliance;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 週期分析器
 * 追蹤機器人的收球-投放週期時間
 */
@Slf4j
public class CycleAnalyzer {

    /**
     * 週期事件類型
     */
    public enum CycleEventType {
        INTAKE_START, // 開始收球
        INTAKE_END, // 收球完成
        TRAVEL_START, // 開始移動
        TRAVEL_END, // 到達目的地
        DELIVERY_START, // 開始投放
        DELIVERY_END // 投放完成
    }

    /**
     * 週期指標
     */
    @Data
    public static class CycleMetrics {
        /** 收球時間 (秒) */
        private double intakeTime = 0;

        /** 移動時間 (秒) */
        private double travelTime = 0;

        /** 投放時間 (秒) */
        private double deliveryTime = 0;

        /** 總週期時間 (秒) */
        public double getTotalCycleTime() {
            return intakeTime + travelTime + deliveryTime;
        }

        @Override
        public String toString() {
            return String.format("收球:%.1fs 移動:%.1fs 投放:%.1fs 總計:%.1fs",
                    intakeTime, travelTime, deliveryTime, getTotalCycleTime());
        }
    }

    /**
     * 週期事件記錄
     */
    @Data
    private static class CycleEvent {
        CycleEventType type;
        long timestamp;
        int robotId;

        CycleEvent(CycleEventType type, int robotId) {
            this.type = type;
            this.timestamp = System.currentTimeMillis();
            this.robotId = robotId;
        }
    }

    // 每個聯盟的事件記錄
    private LinkedList<CycleEvent> redEvents = new LinkedList<>();
    private LinkedList<CycleEvent> blueEvents = new LinkedList<>();

    // 已完成的週期記錄
    private List<CycleMetrics> redCycles = new ArrayList<>();
    private List<CycleMetrics> blueCycles = new ArrayList<>();

    // 最大保留的事件數量
    private static final int MAX_EVENTS = 100;

    // 效能等級閾值 (秒)
    private static final double WORLD_CLASS_CYCLE = 4.0;
    private static final double COMPETITIVE_CYCLE = 7.0;
    private static final double AVERAGE_CYCLE = 10.0;

    public CycleAnalyzer() {
        log.info("週期分析器初始化完成");
    }

    /**
     * 記錄週期事件
     */
    public void recordEvent(Alliance alliance, CycleEventType type, int robotId) {
        CycleEvent event = new CycleEvent(type, robotId);
        LinkedList<CycleEvent> events = alliance == Alliance.RED ? redEvents : blueEvents;

        events.add(event);

        // 限制事件數量
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }

        // 嘗試計算完成的週期
        tryCalculateCycle(alliance);

        log.debug("記錄週期事件: {} {} robot#{}", alliance, type, robotId);
    }

    /**
     * 嘗試計算已完成的週期
     */
    private void tryCalculateCycle(Alliance alliance) {
        LinkedList<CycleEvent> events = alliance == Alliance.RED ? redEvents : blueEvents;
        List<CycleMetrics> cycles = alliance == Alliance.RED ? redCycles : blueCycles;

        // 尋找完整的週期序列
        CycleEvent intakeStart = null, intakeEnd = null;
        CycleEvent travelStart = null, travelEnd = null;
        CycleEvent deliveryStart = null, deliveryEnd = null;

        for (CycleEvent event : events) {
            switch (event.type) {
                case INTAKE_START -> intakeStart = event;
                case INTAKE_END -> intakeEnd = event;
                case TRAVEL_START -> travelStart = event;
                case TRAVEL_END -> travelEnd = event;
                case DELIVERY_START -> deliveryStart = event;
                case DELIVERY_END -> deliveryEnd = event;
            }
        }

        // 檢查是否有完整週期
        if (intakeStart != null && intakeEnd != null &&
                travelStart != null && travelEnd != null &&
                deliveryStart != null && deliveryEnd != null &&
                intakeStart.timestamp < intakeEnd.timestamp &&
                intakeEnd.timestamp <= travelStart.timestamp &&
                travelStart.timestamp < travelEnd.timestamp &&
                travelEnd.timestamp <= deliveryStart.timestamp &&
                deliveryStart.timestamp < deliveryEnd.timestamp) {

            CycleMetrics metrics = new CycleMetrics();
            metrics.setIntakeTime((intakeEnd.timestamp - intakeStart.timestamp) / 1000.0);
            metrics.setTravelTime((travelEnd.timestamp - travelStart.timestamp) / 1000.0);
            metrics.setDeliveryTime((deliveryEnd.timestamp - deliveryStart.timestamp) / 1000.0);

            cycles.add(metrics);
            log.info("{} 完成週期: {}", alliance, metrics);

            // 清除已處理的事件
            events.clear();
        }
    }

    /**
     * 取得平均週期指標
     */
    public CycleMetrics getAverageMetrics(Alliance alliance) {
        List<CycleMetrics> cycles = alliance == Alliance.RED ? redCycles : blueCycles;

        if (cycles.isEmpty()) {
            return new CycleMetrics();
        }

        double totalIntake = 0, totalTravel = 0, totalDelivery = 0;

        for (CycleMetrics m : cycles) {
            totalIntake += m.getIntakeTime();
            totalTravel += m.getTravelTime();
            totalDelivery += m.getDeliveryTime();
        }

        CycleMetrics avg = new CycleMetrics();
        avg.setIntakeTime(totalIntake / cycles.size());
        avg.setTravelTime(totalTravel / cycles.size());
        avg.setDeliveryTime(totalDelivery / cycles.size());

        return avg;
    }

    /**
     * 取得效能等級
     */
    public String getPerformanceRating(Alliance alliance) {
        CycleMetrics avg = getAverageMetrics(alliance);
        double cycleTime = avg.getTotalCycleTime();

        if (cycleTime <= 0) {
            return "無資料";
        } else if (cycleTime <= WORLD_CLASS_CYCLE) {
            return "🏆 極限最快 (World Class)";
        } else if (cycleTime <= COMPETITIVE_CYCLE) {
            return "⭐ 優秀 (Competitive)";
        } else if (cycleTime <= AVERAGE_CYCLE) {
            return "📈 一般 (Average)";
        } else {
            return "⚠️ 需改進 (Needs Work)";
        }
    }

    /**
     * 取得預估分數潛力
     */
    public String getScorePotential(Alliance alliance) {
        CycleMetrics avg = getAverageMetrics(alliance);
        double cycleTime = avg.getTotalCycleTime();

        if (cycleTime <= 0) {
            return "無法估算";
        } else if (cycleTime <= WORLD_CLASS_CYCLE) {
            return "350+ 分 (神級)";
        } else if (cycleTime <= COMPETITIVE_CYCLE) {
            return "200+ 分 (冠軍競爭者)";
        } else if (cycleTime <= AVERAGE_CYCLE) {
            return "100-150 分";
        } else {
            return "< 100 分";
        }
    }

    /**
     * 取得完成的週期數量
     */
    public int getCompletedCycles(Alliance alliance) {
        return alliance == Alliance.RED ? redCycles.size() : blueCycles.size();
    }

    /**
     * 取得最近 N 個週期
     */
    public List<CycleMetrics> getRecentCycles(Alliance alliance, int count) {
        List<CycleMetrics> cycles = alliance == Alliance.RED ? redCycles : blueCycles;
        int start = Math.max(0, cycles.size() - count);
        return new ArrayList<>(cycles.subList(start, cycles.size()));
    }

    /**
     * 計算預估剩餘可完成的週期數
     */
    public int estimateRemainingCycles(Alliance alliance, int remainingSeconds) {
        CycleMetrics avg = getAverageMetrics(alliance);
        double cycleTime = avg.getTotalCycleTime();

        if (cycleTime <= 0) {
            // 預設使用一般水準
            cycleTime = AVERAGE_CYCLE;
        }

        return (int) (remainingSeconds / cycleTime);
    }

    /**
     * 取得分析報告
     */
    public String getAnalysisReport(Alliance alliance) {
        CycleMetrics avg = getAverageMetrics(alliance);
        int completed = getCompletedCycles(alliance);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== %s 週期分析 ===\n", alliance.getDisplayName()));
        sb.append(String.format("已完成週期: %d\n", completed));
        sb.append(String.format("平均時間: %s\n", avg));
        sb.append(String.format("效能等級: %s\n", getPerformanceRating(alliance)));
        sb.append(String.format("分數潛力: %s\n", getScorePotential(alliance)));

        return sb.toString();
    }

    /**
     * 重置分析資料
     */
    public void reset() {
        redEvents.clear();
        blueEvents.clear();
        redCycles.clear();
        blueCycles.clear();
        log.info("週期分析資料已重置");
    }
}
