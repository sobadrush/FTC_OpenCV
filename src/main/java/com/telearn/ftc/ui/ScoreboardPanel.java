package com.telearn.ftc.ui;

import com.telearn.ftc.model.Alliance;
import com.telearn.ftc.model.AllianceScore;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 計分板面板
 * 顯示單一聯盟的得分明細
 */
public class ScoreboardPanel extends JPanel {

    private final Alliance alliance;

    // 分數標籤
    private JLabel totalScoreLabel;
    private JLabel autoScoreLabel;
    private JLabel teleOpScoreLabel;
    private JLabel endgameScoreLabel;
    private JLabel classifiedLabel;
    private JLabel cyclesLabel;

    public ScoreboardPanel(Alliance alliance) {
        this.alliance = alliance;
        initializeUI();
    }

    private void initializeUI() {
        // 設定邊框顏色
        Color borderColor = alliance == Alliance.RED
                ? new Color(200, 50, 50)
                : new Color(50, 50, 200);

        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(borderColor, 2),
                alliance == Alliance.RED ? "🔴 紅色聯盟" : "🔵 藍色聯盟",
                TitledBorder.CENTER,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 14),
                borderColor));

        setLayout(new GridLayout(6, 2, 5, 3));
        setBackground(alliance == Alliance.RED
                ? new Color(255, 240, 240)
                : new Color(240, 240, 255));

        // 總分 (大字)
        add(createLabel("總分:", Font.BOLD));
        totalScoreLabel = createValueLabel("0", 24, Font.BOLD);
        add(totalScoreLabel);

        // Auto 小計
        add(createLabel("Auto:", Font.PLAIN));
        autoScoreLabel = createValueLabel("0", 14, Font.PLAIN);
        add(autoScoreLabel);

        // TeleOp 小計
        add(createLabel("TeleOp:", Font.PLAIN));
        teleOpScoreLabel = createValueLabel("0", 14, Font.PLAIN);
        add(teleOpScoreLabel);

        // Endgame 小計
        add(createLabel("Endgame:", Font.PLAIN));
        endgameScoreLabel = createValueLabel("0", 14, Font.PLAIN);
        add(endgameScoreLabel);

        // 文物數量
        add(createLabel("已分類:", Font.PLAIN));
        classifiedLabel = createValueLabel("0 顆", 12, Font.PLAIN);
        add(classifiedLabel);

        // 週期數
        add(createLabel("循環:", Font.PLAIN));
        cyclesLabel = createValueLabel("0 次", 12, Font.PLAIN);
        add(cyclesLabel);
    }

    private JLabel createLabel(String text, int style) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", style, 12));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    private JLabel createValueLabel(String text, int fontSize, int style) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Monospaced", style, fontSize));
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setForeground(alliance == Alliance.RED
                ? new Color(180, 0, 0)
                : new Color(0, 0, 180));
        return label;
    }

    /**
     * 更新分數顯示
     */
    public void updateScore(AllianceScore score) {
        totalScoreLabel.setText(String.valueOf(score.getTotalScore()));
        autoScoreLabel.setText(String.valueOf(score.getAutoSubtotal()));
        teleOpScoreLabel.setText(String.valueOf(score.getTeleOpSubtotal()));
        endgameScoreLabel.setText(String.valueOf(score.getEndgameSubtotal()));
        classifiedLabel.setText(score.getTotalClassified() + " 顆");
        cyclesLabel.setText(score.getCompletedCycles() + " 次");
    }

    /**
     * 取得聯盟
     */
    public Alliance getAlliance() {
        return alliance;
    }
}
