package com.telearn;

import javax.swing.*;

/**
 * 應用程式主類別
 */
public class App {

    /**
     * 主程式進入點
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(OpenCVCameraDemo::new);
    }

}
