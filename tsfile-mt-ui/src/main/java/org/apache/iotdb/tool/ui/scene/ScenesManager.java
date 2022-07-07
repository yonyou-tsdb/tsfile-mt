package org.apache.iotdb.tool.ui.scene;

import org.apache.iotdb.tool.core.util.OffLineTsFileUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * scenes manager for scene change
 *
 * @author oortCloudFei
 */
public class ScenesManager {

  private static final Logger logger = LoggerFactory.getLogger(ScenesManager.class);

  static final ScenesManager scenesManager = new ScenesManager();

  private IoTDBParsePageV13 ioTDBParsePage;

  /** base stage */
  private Stage baseStage = null;

  private ScenesManager() {
    ioTDBParsePage = new IoTDBParsePageV13();
  }

  public IoTDBParsePageV13 getIoTDBParsePage() {
    return ioTDBParsePage;
  }

  public static final ScenesManager getInstance() {
    return scenesManager;
  }

  public void setBaseStage(Stage stage) {
    this.baseStage = stage;
  }

  public void loadTsFile(ProgressBar progressBar) {
    Task progressTask = progressWorker(ioTDBParsePage);
    progressBar.progressProperty().unbind();
    progressBar.progressProperty().bind(progressTask.progressProperty());
    new Thread(progressTask).start();
  }

  public void showBaseStage() {
    ioTDBParsePage.init(baseStage);
    baseStage.setScene(ioTDBParsePage.getScene());
    baseStage.setTitle(ioTDBParsePage.getName());
    baseStage.getIcons().add(new Image("/icons/yonyou-logo.png"));
    baseStage.centerOnScreen();
    baseStage.show();
  }

  public boolean fileCheck(String path) {
    int i = 0;
    try {
      i = OffLineTsFileUtil.fetchTsFileVersionNumber(path);
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (i != 3) {
      Alert alert = new Alert(Alert.AlertType.ERROR, "none support version", ButtonType.OK);
      alert.showAndWait();
      return false;
    }
    return true;
  }

  public Task progressWorker(IoTDBParsePageV13 ioTDBParsePage) {
    return new Task() {
      @Override
      protected Object call() throws Exception {
        while (ioTDBParsePage.getTsFileAnalyserV13().getRateOfProcess() < 1) {
          updateProgress(ioTDBParsePage.getTsFileAnalyserV13().getRateOfProcess(), 1);
        }
        updateProgress(1, 1);
        logger.info("TsFile Load completed.");
        System.out.println("TsFile Load completed.");
        Platform.runLater(() -> ioTDBParsePage.chunkGroupTreeDataInit());
        return true;
      }
    };
  }
}
