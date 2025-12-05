package com.nanshan;

import javax.swing.*;

/**
 * Hello world!
 *
 */
public class App {

    /**
     * 主程式進入點
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            OpenCVCameraDemo demo = new OpenCVCameraDemo();
        });
    }

}
