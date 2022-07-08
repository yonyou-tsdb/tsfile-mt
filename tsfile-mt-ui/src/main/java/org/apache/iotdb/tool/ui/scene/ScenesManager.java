package org.apache.iotdb.tool.ui.scene;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.control.*;
import org.apache.iotdb.tool.core.service.TsFileAnalyserV13;
import org.apache.iotdb.tool.core.util.OffLineTsFileUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
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
    // 关闭 stage 时清空缓存
    baseStage.setOnCloseRequest(event -> {
      clearCache();
      baseStage = null;
    });
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

  // TODO
  // 清空缓存
  public void clearCache() {
    ioTDBParsePage.clearParsePageCache();
  }
}
