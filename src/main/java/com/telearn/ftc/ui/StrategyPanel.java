package com.telearn.ftc.ui;

import com.telearn.ftc.model.Alliance;
import com.telearn.ftc.strategy.CycleAnalyzer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 策略分析面板
 * 顯示週期時間與效能評估
 */
public class StrategyPanel extends JPanel {

    private CycleAnalyzer cycleAnalyzer;

    // 紅色聯盟指標
    private JLabel redCycleTimeLabel;
    private JLabel redPerformanceLabel;
    private JLabel redPotentialLabel;
    private JLabel redCyclesLabel;

    // 藍色聯盟指標
    private JLabel blueCycleTimeLabel;
    private JLabel bluePerformanceLabel;
    private JLabel bluePotentialLabel;
    private JLabel blueCyclesLabel;

    // 建議區域
    private JTextArea suggestionArea;

    public StrategyPanel(CycleAnalyzer cycleAnalyzer) {
        this.cycleAnalyzer = cycleAnalyzer;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(new TitledBorder("📊 策略分析"));

        // 上方：雙聯盟對比
        JPanel comparisonPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        // 紅色聯盟
        JPanel redPanel = createAlliancePanel(Alliance.RED);
        comparisonPanel.add(redPanel);

        // 藍色聯盟
        JPanel bluePanel = createAlliancePanel(Alliance.BLUE);
        comparisonPanel.add(bluePanel);

        add(comparisonPanel, BorderLayout.CENTER);

        // 下方：策略建議
        JPanel suggestionPanel = new JPanel(new BorderLayout());
        suggestionPanel.setBorder(new TitledBorder("💡 策略建議"));

        suggestionArea = new JTextArea(3, 40);
        suggestionArea.setEditable(false);
        suggestionArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
        suggestionArea.setLineWrap(true);
        suggestionArea.setWrapStyleWord(true);
        suggestionArea.setText("開始比賽後將顯示即時策略建議...");

        suggestionPanel.add(new JScrollPane(suggestionArea), BorderLayout.CENTER);
        add(suggestionPanel, BorderLayout.SOUTH);
    }

    private JPanel createAlliancePanel(Alliance alliance) {
        boolean isRed = alliance == Alliance.RED;
        Color bgColor = isRed ? new Color(255, 245, 245) : new Color(245, 245, 255);
        Color fgColor = isRed ? new Color(180, 0, 0) : new Color(0, 0, 180);

        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 3));
        panel.setBackground(bgColor);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(fgColor, 1),
                isRed ? "🔴 紅方" : "🔵 藍方",
                TitledBorder.CENTER, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12), fgColor));

        // 週期時間
        panel.add(createLabel("平均週期:", Font.PLAIN));
        JLabel cycleLabel = createValueLabel("--", fgColor);
        panel.add(cycleLabel);

        // 效能等級
        panel.add(createLabel("效能:", Font.PLAIN));
        JLabel perfLabel = createValueLabel("無資料", fgColor);
        panel.add(perfLabel);

        // 分數潛力
        panel.add(createLabel("潛力:", Font.PLAIN));
        JLabel potLabel = createValueLabel("--", fgColor);
        panel.add(potLabel);

        // 已完成週期
        panel.add(createLabel("週期數:", Font.PLAIN));
        JLabel cyclesLabel = createValueLabel("0", fgColor);
        panel.add(cyclesLabel);

        // 儲存參考
        if (isRed) {
            redCycleTimeLabel = cycleLabel;
            redPerformanceLabel = perfLabel;
            redPotentialLabel = potLabel;
            redCyclesLabel = cyclesLabel;
        } else {
            blueCycleTimeLabel = cycleLabel;
            bluePerformanceLabel = perfLabel;
            bluePotentialLabel = potLabel;
            blueCyclesLabel = cyclesLabel;
        }

        return panel;
    }

    private JLabel createLabel(String text, int style) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", style, 11));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    private JLabel createValueLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Monospaced", Font.BOLD, 11));
        label.setForeground(color);
        return label;
    }

    /**
     * 更新顯示
     */
    public void update() {
        // 更新紅色聯盟
        updateAllianceMetrics(Alliance.RED);

        // 更新藍色聯盟
        updateAllianceMetrics(Alliance.BLUE);

        // 更新策略建議
        updateSuggestions();
    }

    private void updateAllianceMetrics(Alliance alliance) {
        CycleAnalyzer.CycleMetrics metrics = cycleAnalyzer.getAverageMetrics(alliance);
        String performance = cycleAnalyzer.getPerformanceRating(alliance);
        String potential = cycleAnalyzer.getScorePotential(alliance);
        int cycles = cycleAnalyzer.getCompletedCycles(alliance);

        JLabel cycleLabel = alliance == Alliance.RED ? redCycleTimeLabel : blueCycleTimeLabel;
        JLabel perfLabel = alliance == Alliance.RED ? redPerformanceLabel : bluePerformanceLabel;
        JLabel potLabel = alliance == Alliance.RED ? redPotentialLabel : bluePotentialLabel;
        JLabel cyclesLabel = alliance == Alliance.RED ? redCyclesLabel : blueCyclesLabel;

        double totalTime = metrics.getTotalCycleTime();
        if (totalTime > 0) {
            cycleLabel.setText(String.format("%.1fs", totalTime));
        } else {
            cycleLabel.setText("--");
        }

        perfLabel.setText(performance);
        potLabel.setText(potential);
        cyclesLabel.setText(String.valueOf(cycles));
    }

    private void updateSuggestions() {
        CycleAnalyzer.CycleMetrics redMetrics = cycleAnalyzer.getAverageMetrics(Alliance.RED);
        CycleAnalyzer.CycleMetrics blueMetrics = cycleAnalyzer.getAverageMetrics(Alliance.BLUE);

        StringBuilder sb = new StringBuilder();

        double redTime = redMetrics.getTotalCycleTime();
        double blueTime = blueMetrics.getTotalCycleTime();

        if (redTime <= 0 && blueTime <= 0) {
            sb.append("等待週期數據收集中...\n");
            sb.append("提示: 追蹤機器人從 Loading Zone 到發射區的完整週期以獲得分析。");
        } else {
            // 比較雙方
            if (redTime > 0 && blueTime > 0) {
                if (redTime < blueTime) {
                    sb.append("🔴 紅方週期較快 (").append(String.format("%.1fs", blueTime - redTime)).append(" 優勢)\n");
                } else if (blueTime < redTime) {
                    sb.append("🔵 藍方週期較快 (").append(String.format("%.1fs", redTime - blueTime)).append(" 優勢)\n");
                } else {
                    sb.append("⚖️ 雙方週期相當\n");
                }
            }

            // 個別建議
            if (redTime > 7.0) {
                sb.append("紅方建議: 加快收球速度或優化移動路徑\n");
            }
            if (blueTime > 7.0) {
                sb.append("藍方建議: 加快收球速度或優化移動路徑\n");
            }

            // 通用建議
            sb.append("\n💡 關鍵: Loading Zone 的人類選手配合是縮短週期的決勝點！");
        }

        suggestionArea.setText(sb.toString());
    }

    /**
     * 重置面板
     */
    public void reset() {
        redCycleTimeLabel.setText("--");
        redPerformanceLabel.setText("無資料");
        redPotentialLabel.setText("--");
        redCyclesLabel.setText("0");

        blueCycleTimeLabel.setText("--");
        bluePerformanceLabel.setText("無資料");
        bluePotentialLabel.setText("--");
        blueCyclesLabel.setText("0");

        suggestionArea.setText("開始比賽後將顯示即時策略建議...");
    }
}
