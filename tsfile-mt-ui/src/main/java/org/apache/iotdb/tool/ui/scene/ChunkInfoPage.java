package org.apache.iotdb.tool.ui.scene;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.apache.iotdb.tsfile.file.header.ChunkHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class ChunkInfoPage {
    private static final Logger logger = LoggerFactory.getLogger(IoTDBParsePageV13.class);

    private static final double WIDTH = 455;
    private static final double HEIGHT = 345;

    private IoTDBParsePageV13 ioTDBParsePage;
    private GridPane pane;
    private Scene scene;
    private Stage stage;
    // TODO
    private TableView fileInfoTableView = new TableView();
    private ObservableList<TsFileInfoPage.FileInfo> fileDatas = FXCollections.observableArrayList();

    private TreeItem<IoTDBParsePageV13.ChunkTreeItemValue> chunkItem;

    public ChunkInfoPage() {

    }

    public ChunkInfoPage(Stage stage, IoTDBParsePageV13 ioTDBParsePage, TreeItem<IoTDBParsePageV13.ChunkTreeItemValue> chunkItem) {
        this.stage = stage;
        this.ioTDBParsePage = ioTDBParsePage;
        this.chunkItem = chunkItem;
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

        // 数据来源
        IoTDBParsePageV13.ChunkWrap params = (IoTDBParsePageV13.ChunkWrap) chunkItem.getValue().getParams();
        ChunkHeader chunkHeader = params.getChunkHeader();

        // TODO 支持复制（text lable 不支持复制）
        // 可以参考 IDEA 一键复制
        // Logo
        // 字体颜色
        Label tsfileNameLabel = new Label("DataSize:");
        TextField tsfileNameResult = new TextField(chunkHeader.getDataSize() + "");
        tsfileNameResult.setEditable(false);
        tsfileNameResult.setFocusTraversable(false);
        tsfileNameResult.getStyleClass().add("copiable-text");
        pane.add(tsfileNameLabel, 0, 0);
        pane.add(tsfileNameResult, 1, 0);

        Label versionLabel = new Label("DataType:");
        TextField versionResult = new TextField(chunkHeader.getDataType() + "");
        versionResult.setEditable(false);
        versionResult.setFocusTraversable(false);
        versionResult.getStyleClass().add("copiable-text");
        pane.add(versionLabel, 0, 1);
        pane.add(versionResult, 1, 1);

        Label sizeLabel = new Label("CompressionType:");
        TextField sizeResult = new TextField(chunkHeader.getCompressionType() + "");
        sizeResult.setEditable(false);
        sizeResult.setFocusTraversable(false);
        sizeResult.getStyleClass().add("copiable-text");
        pane.add(sizeLabel, 0, 2);
        pane.add(sizeResult, 1, 2);

        Label dataCountsLabel = new Label("EncodingType:");
        TextField dataCountsResult = new TextField(chunkHeader.getEncodingType() + "");
        dataCountsResult.setEditable(false);
        dataCountsResult.setFocusTraversable(false);
        dataCountsResult.getStyleClass().add("copiable-text");
        pane.add(dataCountsLabel, 0, 3);
        pane.add(dataCountsResult, 1, 3);

        stage.show();
        URL uiDarkCssResource = getClass().getClassLoader().getResource("css/copiable-text.css");
        if (uiDarkCssResource != null) {
            this.getScene().getStylesheets().add(uiDarkCssResource.toExternalForm());
        }
    }
}
