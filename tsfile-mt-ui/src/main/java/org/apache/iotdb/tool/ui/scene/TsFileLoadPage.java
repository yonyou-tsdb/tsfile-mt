package org.apache.iotdb.tool.ui.scene;

import com.sun.javafx.scene.control.skin.LabeledText;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.net.URL;

/**
 * TsFileLoadPage
 *
 * @author shenguanchu
 */
public class TsFileLoadPage {
    private static final double WIDTH = 540;
    private static final double HEIGHT = 200;

    private Label infoLabel;
    private Button loadButton;
    private Button cancelButton;
    private GridPane pane;
    private Scene scene;
    private String tsfileName;
    private Stage stage;

    public TsFileLoadPage(Stage stage, String tsfileName) {
        this.stage = stage;
        this.tsfileName = tsfileName;
        init(stage);
    }

    public Scene getScene() {
        return scene;
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

        infoLabel = new Label("Please confirm whether to load: " + tsfileName);
        pane.add(infoLabel, 0, 0);

        stage.setTitle("Confirm Loading");
        loadButton = new Button("load");
        cancelButton = new Button("cancel");
        pane.add(loadButton, 1, 1);
        pane.add(cancelButton, 2, 1);

//        loadButton.setOnAction(
//                event -> {
//                    ScenesManager scenesManager = ScenesManager.getInstance();
//                    String path = filePath.getText();
//                    progressBar.setVisible(true);
//                    progressBar.setDisable(false);
//                    scenesManager.loadTsFile(path, progressBar);
//                });

        cancelButton.setOnAction(
                event -> {
                    stage.close();
                });

        stage.show();

        URL uiDarkCssResource = getClass().getClassLoader().getResource("css/ui-dark.css");
        if (uiDarkCssResource != null) {
            this.getScene().getStylesheets().add(uiDarkCssResource.toExternalForm());
        }
    }
}
