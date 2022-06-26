package org.apache.iotdb.tool.ui.scene;

import java.io.*;
import java.net.URL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TsFileChooserPage
 *
 * @author oortCloudFei
 */
public class TsFileChooserPage extends Page<GridPane> {

  private static  final Logger logger = LoggerFactory.getLogger(TsFileChooserPage.class);

  private static final double WIDTH = 350 * 1.5;
  private static final double HEIGHT = 250 * 1.5;

  public static boolean isFileLoaded = false;

  private FileChooser fileChooser = new FileChooser();
  private TextField filePath = new TextField("...");
  private ProgressBar progressBar = new ProgressBar(0);

  public TsFileChooserPage() {
    super(new GridPane(), WIDTH, HEIGHT);
  }

  public TsFileChooserPage(Stage stage) {
    super(new GridPane(), WIDTH, HEIGHT);
//    init(stage);
  }

  private void init(Stage stage) {

    this.root.setAlignment(Pos.CENTER);
    this.root.setHgap(10);
    this.root.setVgap(10);
    this.root.setPadding(new Insets(25, 25, 25, 25));

    // fileCHooser
    filePath.setEditable(false);
    fileChooser.setSelectedExtensionFilter(
            new FileChooser.ExtensionFilter("tsfiles (*.tsfile)", "*.tsfile"));
    this.root.add(filePath, 0, 1);
    Button button = new Button("Select File");
      button.setOnAction(
        event -> {
          File selectedFile = fileChooser.showOpenDialog(stage);
          filePath.setText(selectedFile.getAbsolutePath());
        });
    this.root.add(button, 1, 1);


    this.root.add(progressBar, 0, 3);
    progressBar.setVisible(false);
    progressBar.setDisable(true);

    Button startButton = new Button("Load File");
    startButton.setOnAction(
        event -> {
            ScenesManager scenesManager = ScenesManager.getInstance();
            String path = filePath.getText();
            progressBar.setVisible(true);
            progressBar.setDisable(false);
            scenesManager.loadTsFile(path, progressBar);
        });
    this.root.add(startButton, 0, 2);

    URL cssResource = getClass().getClassLoader().getResource("css/IoTDB.css");
    if (cssResource != null) {
      this.getScene().getStylesheets().add(cssResource.toExternalForm());
    }
  }

  @Override
  public String getName() {
    return "TSFILE工具-文件选择";
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
//    if (selectedfolder != null) {
//      File[] files = selectedfolder.listFiles();
//      for (File f : files) {
//        System.out.println(f.getAbsolutePath() + File.separator + f.getName());
//      }
//    }

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
}
