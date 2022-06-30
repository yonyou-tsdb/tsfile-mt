package org.apache.iotdb.tool.ui.scene;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.iotdb.tool.core.service.TsFileAnalyserV13;
import org.apache.iotdb.tool.core.util.OffLineTsFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;

/**
 * TsFileLoadPage
 *
 * @author shenguanchu
 */
public class TsFileLoadPage {
    private static final Logger logger = LoggerFactory.getLogger(ScenesManager.class);

    private static final double WIDTH = 540;
    private static final double HEIGHT = 200;

    private Label infoLabel;
    private Button loadButton;
    private Button cancelButton;
    private GridPane pane;
    private Scene scene;
    private Stage stage;

    private ProgressBar progressBar = new ProgressBar(0);

    private TsFileAnalyserV13 tsFileAnalyserV13;

    // TODO 这里代码需要优化，重构
    private IoTDBParsePageV13 ioTDBParsePageV13;

    private String filePath;

    public TsFileLoadPage() {

    }

    public TsFileLoadPage(Stage stage, String filePath) {
        this.stage = stage;
        this.filePath = filePath;
        init(stage);
    }

    public Scene getScene() {
        return scene;
    }

    public void setIoTDBParsePageV13(IoTDBParsePageV13 ioTDBParsePageV13) {
        this.ioTDBParsePageV13 = ioTDBParsePageV13;
    }

    private void init(Stage stage) {
        stage.setResizable(false);
        pane = new GridPane();
        scene = new Scene(this.pane, WIDTH, HEIGHT);
        stage.setScene(scene);

        pane.setHgap(10);
        pane.setVgap(10);

        pane.setPadding(new Insets(20));
        pane.setAlignment(Pos.CENTER);

        String[] filePathArr = filePath.split("\\\\");
        String tsfileName = filePathArr[filePathArr.length - 1];
        infoLabel = new Label("Please confirm whether to load: " + tsfileName);
        pane.add(infoLabel, 0, 0);

        stage.setTitle("Confirm Loading");
        loadButton = new Button("load");
        cancelButton = new Button("cancel");
        pane.add(loadButton, 1, 1);
        pane.add(cancelButton, 2, 1);

        pane.add(progressBar, 0, 2);
        progressBar.getStyleClass().add("progress-bar-field");
        progressBar.setPrefWidth(300);
        progressBar.setVisible(false);
        progressBar.setDisable(true);

        cancelButton.setOnAction(
                event -> {
                    stage.close();
                });

        loadButton.setOnAction(
                event -> {
                    // TODO 清空上一个打开的 tsfile 的缓存
                    // 异步加载文件
                    loadTsFile(filePath);
                    // TODO 优化代码
                    ioTDBParsePageV13.setTsFileAnalyserV13(tsFileAnalyserV13);
                    // 进度条
                    ScenesManager scenesManager = ScenesManager.getInstance();
                    progressBar.setVisible(true);
                    progressBar.setDisable(false);
                    scenesManager.loadTsFile(progressBar);
                });
        stage.show();
        URL uiDarkCssResource = getClass().getClassLoader().getResource("css/ui-dark.css");
        if (uiDarkCssResource != null) {
            this.getScene().getStylesheets().add(uiDarkCssResource.toExternalForm());
        }
    }

    public File loadFolder(Stage baseStage) {
        DirectoryChooser folderChooser = new DirectoryChooser();
        folderChooser.setTitle("Open Folder");

        // directoryCache.txt
        File cacheFile = new File("directoryCache.txt");
        if (cacheFile.exists()) {
            try (InputStream inputStream = new FileInputStream(cacheFile)) {
                byte[] bytes = new byte[(int) cacheFile.length()];
                // Read the contents of the directoryCache.txt
                inputStream.read(bytes);
                File directory = new File(new String(bytes));
                if (directory.exists()) {
                    folderChooser.setInitialDirectory(directory);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        File selectedfolder = folderChooser.showDialog(baseStage);

        // Store the directory to the directoryCache.txt
        if (selectedfolder != null) {
            try (OutputStream outputStream = new FileOutputStream(cacheFile)) {
                byte[] bytes = selectedfolder.getAbsolutePath().getBytes();
                outputStream.write(bytes);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return selectedfolder;
    }

    public Boolean fileTypeCheck(String filePath) {
        return filePath.endsWith(".tsfile");
    }

    public boolean fileVersionCheck(String filePath) {
        int version = 0;
        try {
            version = OffLineTsFileUtil.fetchTsFileVersionNumber(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return version == 3;
    }

    private void loadTsFile(String filePath) {
        // 异步
        try {
          // tsfile parse
          this.tsFileAnalyserV13 = new TsFileAnalyserV13(filePath);
        } catch (IOException e) {
          logger.error("Failed to get TsFileAnalysedV13 instance.");
          e.printStackTrace();
          return;
        }
    }
}
