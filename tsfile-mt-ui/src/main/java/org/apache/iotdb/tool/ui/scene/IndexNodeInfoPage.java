package org.apache.iotdb.tool.ui.scene;


import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URL;

public class IndexNodeInfoPage {
    private static final Logger logger = LoggerFactory.getLogger(IoTDBParsePageV13.class);

    private static final double WIDTH = 810;
    private static final double HEIGHT = 300;

    private AnchorPane anchorPane;
    private Scene scene;
    private IoTDBParsePageV13 ioTDBParsePage;
    private Stage stage;

    private AnchorPane pageHeaderPane;

    private AnchorPane pageDataPane;

    private String menuItemInfo;

    private String nodeInfo;

    public IndexNodeInfoPage() {}

    public IndexNodeInfoPage(
            Stage stage,
            String menuItemInfo,
            String nodeInfo) {
        this.menuItemInfo = menuItemInfo;
        this.nodeInfo = nodeInfo;
        this.stage = stage;
        init(stage);
    }

    public Scene getScene() {
        return scene;
    }

    private void init(Stage stage) {
        anchorPane = new AnchorPane();
        scene = new Scene(anchorPane, WIDTH, HEIGHT);
        stage.setScene(scene);
        stage.setTitle(menuItemInfo);
        stage.show();
        stage.setResizable(false);

        TextArea textArea = new TextArea(nodeInfo);
        textArea.setEditable(false);
        textArea.setPrefWidth(WIDTH);
        textArea.setPrefHeight(HEIGHT);
        anchorPane.getChildren().add(textArea);


        URL uiDarkCssResource = getClass().getClassLoader().getResource("css/ui-dark.css");
        if (uiDarkCssResource != null) {
            this.getScene().getStylesheets().add(uiDarkCssResource.toExternalForm());
        }
    }
}
