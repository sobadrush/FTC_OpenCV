# FTC_OpenCV - OpenCV 攝影機展示專案

這是一個簡單的 Java Swing 應用程式，用以展示如何使用 OpenCV 從攝影機擷取即時影像。

## 版本

- **專案版本**: 1.0-SNAPSHOT

## 功能

- **啟動/停止攝影機**: 開始或停止影像的擷取。
- **拍照**: 從當前的攝影機畫面擷取一張靜態圖片並儲存為 JPG 檔案。預設儲存於 `capture_photo/` 目錄下。
- **設定存檔目錄**: 可自訂拍照後的存檔路徑。
- **人臉偵測**: 使用 OpenCV DNN 模組 (ResNet-10 SSD) 進行即時人臉偵測。

## Demo 畫面

### 一般模式
![Demo Screenshot](readme_pics/demo_01.png)

### 人臉辨識功能
![Face Detection Demo](readme_pics/demo_02.png)

## 使用技術

- **Java**: 21
- **Maven**: 專案管理與建構
- **OpenCV**: 4.9.0
  - 核心影像處理 (via `org.openpnp:opencv`)
  - **DNN 模組**: 使用 ResNet-10 SSD 模型 (Caffe Framework) 進行人臉偵測
- **SLF4J + Log4j2**: 日誌記錄框架
- **Lombok**: 簡化樣板程式碼
- **Swing**: 圖形化使用者介面 (GUI)

## 環境需求

- **JDK 21** 或更高版本
- **Maven** 3.6 或更高版本
- 一個可用的**攝影機**

## 如何執行

1.  **複製專案**:
    ```bash
    git clone <repository-url>
    cd FTC_OpenCV
    ```

2.  **使用 Maven 執行**:
    在專案根目錄下，執行以下指令來編譯並啟動應用程式：
    ```bash
    mvn clean compile exec:java
    ```
    應用程式啟動後，會顯示一個包含攝影機畫面的視窗，以及控制按鈕。

## 日誌系統

- **設定檔**: 日誌的設定位於 `src/main/resources/log4j2.xml`。
- **日誌輸出**:
  - **主控台**: 應用程式的日誌會即時顯示在主控台。
  - **檔案**: 日誌會被寫入專案根目錄下的 `ap_logs/` 資料夾中。
    - `opencv.log`: 當前的日誌檔案。
    - `opencv-i.log.gz`: 當日誌檔案大小超過 10MB 時，會被壓縮並輪替，最多保留 10 個舊檔案。

## 專案結構

> **注意**: Maven groupId 使用 `com.nanshan`，但實際 Java 套件名稱為 `com.telearn`。這是專案的設定方式，兩者可以不同。

```
FTC_OpenCV/
├── ap_logs/              # 日誌檔案儲存目錄 (由 .gitignore 排除)
├── capture_photo/        # 預設照片存檔目錄
├── readme_pics/          # 說明文件圖片目錄
├── pom.xml               # Maven 專案設定檔
├── .gitignore            # Git 忽略清單
├── README.md             # 專案說明文件
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/telearn/
    │   │       ├── App.java              # 應用程式主類別 (程式進入點)
    │   │       ├── OpenCVCameraDemo.java # 核心應用程式邏輯與 UI 控制
    │   │       └── FaceDetector.java     # 人臉偵測模組 (使用 DNN)
    │   └── resources/
    │       ├── models/             # 人臉偵測模型檔案
    │       │   ├── deploy.prototxt          # Caffe 模型架構定義
    │       │   ├── res10_300x300_ssd_iter_140000.caffemodel  # 預訓練模型權重
    │       │   └── opencv_face_detector.pbtxt  # OpenCV 人臉偵測器配置
    │       └── log4j2.xml          # Log4j2 日誌設定檔
    └── test/
        └── java/
            └── com/telearn/
                └── AppTest.java    # 單元測試
```

## 系統架構圖

### 類別關係圖

```mermaid
classDiagram
    class App {
        +main(String[] args)
    }
    
    class OpenCVCameraDemo {
        -VideoCapture camera
        -JFrame frame
        -JLabel imageLabel
        -boolean isRunning
        -String saveDirectory
        -FaceDetector faceDetector
        -boolean isFaceDetectionEnabled
        +OpenCVCameraDemo()
        -initializeUI()
        +startCamera()
        +stopCamera()
        +captureImage()
        -chooseSaveDirectory()
        -updateImageLabel(Mat)
        -matToBufferedImage(Mat)
    }
    
    class FaceDetector {
        -Net net
        -String PROTO_FILE
        -String MODEL_FILE
        -double CONFIDENCE_THRESHOLD
        +FaceDetector()
        -loadModel()
        +detectAndDraw(Mat)
    }
    
    class VideoCapture {
        <<OpenCV>>
    }
    
    class Net {
        <<OpenCV DNN>>
    }
    
    App --> OpenCVCameraDemo : 建立實例
    OpenCVCameraDemo --> FaceDetector : 使用
    OpenCVCameraDemo --> VideoCapture : 控制攝影機
    FaceDetector --> Net : 載入 DNN 模型
```

### 應用程式流程圖

```mermaid
flowchart TD
    Start([應用程式啟動]) --> Init[初始化 OpenCVCameraDemo]
    Init --> LoadModel[載入人臉偵測模型]
    LoadModel --> CreateUI[建立 Swing UI 介面]
    CreateUI --> Ready[等待使用者操作]
    
    Ready --> |點擊「開始」| StartCam[啟動攝影機]
    StartCam --> CheckCam{攝影機<br/>是否可用?}
    CheckCam -->|否| ShowError[顯示錯誤訊息]
    ShowError --> Ready
    CheckCam -->|是| OpenCam[開啟 VideoCapture]
    OpenCam --> CaptureLoop[持續擷取影像]
    
    CaptureLoop --> CheckFace{人臉偵測<br/>是否啟用?}
    CheckFace -->|是| DetectFace[執行人臉偵測]
    DetectFace --> DrawBox[繪製偵測框]
    DrawBox --> Display[更新顯示畫面]
    CheckFace -->|否| Display[更新顯示畫面]
    Display --> Sleep[延遲 33ms]
    Sleep --> CheckRunning{isRunning?}
    CheckRunning -->|是| CaptureLoop
    CheckRunning -->|否| ReleaseCam[釋放攝影機資源]
    
    Ready -->|點擊「拍照」| CheckCamOpen{攝影機<br/>是否啟動?}
    CheckCamOpen -->|否| WarnUser[提示啟動攝影機]
    WarnUser --> Ready
    CheckCamOpen -->|是| CaptureFrame[擷取當前影像]
    CaptureFrame --> CreateDir[確保存檔目錄存在]
    CreateDir --> SaveImage[儲存為 JPG 檔案]
    SaveImage --> ShowSuccess[顯示成功訊息]
    ShowSuccess --> Ready
    
    Ready -->|點擊「停止」| StopCam[停止攝影機]
    StopCam --> ReleaseCam
    ReleaseCam --> Ready
    
    Ready -->|點擊「設定存檔目錄」| ChooseDir[開啟目錄選擇對話框]
    ChooseDir --> UpdateDir[更新存檔路徑]
    UpdateDir --> Ready
    
    Ready -->|切換「人臉偵測」| ToggleFace[切換偵測狀態]
    ToggleFace --> Ready
    
    Ready -->|關閉視窗| Exit([程式結束])
```

### 技術堆疊架構

```mermaid
graph TB
    subgraph "使用者介面層"
        UI[Java Swing GUI]
        UI_Frame[JFrame 視窗]
        UI_Label[JLabel 影像顯示]
        UI_Buttons[JButton 控制按鈕]
    end
    
    subgraph "應用程式邏輯層"
        App[App.java<br/>程式進入點]
        Demo[OpenCVCameraDemo.java<br/>主要控制邏輯]
        Detector[FaceDetector.java<br/>人臉偵測]
    end
    
    subgraph "OpenCV 函式庫層"
        VideoIO[VideoCapture<br/>視訊擷取]
        DNN[DNN Module<br/>深度學習推論]
        ImgProc[Image Processing<br/>影像處理]
        ImgCodecs[Image Codecs<br/>影像編解碼]
    end
    
    subgraph "系統資源層"
        Camera[實體攝影機]
        Models[DNN 模型檔案<br/>deploy.prototxt<br/>res10_300x300_ssd_iter_140000.caffemodel]
        Files[檔案系統<br/>capture_photo/]
    end
    
    subgraph "支援服務層"
        Logging[SLF4J + Log4j2<br/>日誌系統]
        Lombok[Lombok<br/>程式碼簡化]
    end
    
    UI --> App
    App --> Demo
    Demo --> UI_Frame
    Demo --> UI_Label
    Demo --> UI_Buttons
    Demo --> Detector
    Demo --> VideoIO
    Demo --> ImgCodecs
    Detector --> DNN
    Detector --> ImgProc
    VideoIO --> Camera
    DNN --> Models
    ImgCodecs --> Files
    Demo -.使用.-> Logging
    Demo -.使用.-> Lombok
    Detector -.使用.-> Logging
```
