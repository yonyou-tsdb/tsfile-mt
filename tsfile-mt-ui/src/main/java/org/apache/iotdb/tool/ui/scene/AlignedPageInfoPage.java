package org.apache.iotdb.tool.ui.scene;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.apache.iotdb.tool.core.model.PageInfo;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class AlignedPageInfoPage {
    private static final Logger logger = LoggerFactory.getLogger(IoTDBParsePageV13.class);

    private static final double WIDTH = 1080;
    private static final double HEIGHT = 300;

    private AnchorPane anchorPane;
    private Scene scene;
    private IoTDBParsePageV13 ioTDBParsePage;
    private Stage stage;

    private AnchorPane pageDataPane;

    private TreeItem<IoTDBParsePageV13.ChunkTreeItemValue> pageItem;

    public AlignedPageInfoPage() {}

    public AlignedPageInfoPage(
            Stage stage,
            IoTDBParsePageV13 ioTDBParsePage,
            TreeItem<IoTDBParsePageV13.ChunkTreeItemValue> pageItem) {
        this.stage = stage;
        this.ioTDBParsePage = ioTDBParsePage;
        this.pageItem = pageItem;
        init(stage);
    }

    public Scene getScene() {
        return scene;
    }

    private void init(Stage stage) {
        anchorPane = new AnchorPane();
        scene = new Scene(anchorPane, WIDTH, HEIGHT);
        stage.setScene(scene);
        stage.setTitle("Aligned Page Information");
        stage.show();
        stage.setResizable(false);

        // Table Data Source
        ObservableList<HashMap<String, SimpleStringProperty>> columnDataList = FXCollections.observableArrayList();
        // Table Init
        TableView<HashMap<String, SimpleStringProperty>> alignedTableView = new TableView<HashMap<String, SimpleStringProperty>>(columnDataList);
        pageDataPane = new AnchorPane();
        pageDataPane.setLayoutX(0);
        pageDataPane.setLayoutY(0);
        pageDataPane.setPrefHeight(WIDTH);
        anchorPane.getChildren().add(pageDataPane);
        pageDataPane.getChildren().add(alignedTableView);
        alignedTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        alignedTableView.setVisible(true);
        // Add Column and Data to TableView
        List<PageInfo> pageInfoList = (List<PageInfo>) pageItem.getValue().getParams();
        try {
            BatchData batchData = ioTDBParsePage.getTsFileAnalyserV13().fetchBatchDataByPageInfo(pageInfoList);
            // 1. Add Time Column and it's Data
            TableColumn<HashMap<String, SimpleStringProperty>, String> timestampCol = new TableColumn<HashMap<String, SimpleStringProperty>, String>("timestamp");
            alignedTableView.getColumns().add(timestampCol);
            timestampCol.setCellValueFactory(new MapValueFactory("timestamp"));
            // 2. Add Value Columns and it's Data
            int idx = 0;
            while (batchData.hasCurrent()) {
                HashMap<String, SimpleStringProperty> pageInfoMap = new HashMap<>();
                // Add TimeColumn Data
                pageInfoMap.put("timestamp", new SimpleStringProperty(new Date(batchData.currentTime()).toString()));
                // Add Value Column and it's data
                String[] values = batchData.currentTsPrimitiveType().getStringValue().split(",");
                int measurementCounts = values.length;
                for (int i = 0; i < measurementCounts; i++) {
                    // Add value columns
                    if (idx == 0) {
                        TableColumn<HashMap<String, SimpleStringProperty>, String> valueCol = new TableColumn<HashMap<String, SimpleStringProperty>, String>("value" + i);
                        valueCol.setCellValueFactory(new MapValueFactory("value" + i));
                        alignedTableView.getColumns().add(valueCol);
                    }
                    if (values[i] != null && values[i].length() > 0) {
                        values[i] = values[i].trim();
                        if (i == 0) {
                            values[i] = values[i].substring(1);
                        } else if (i == measurementCounts - 1) {
                            values[i] = values[i].substring(0, values[i].length() - 1);
                        }
                    }
                    pageInfoMap.put("value" + i, new SimpleStringProperty(values[i]));
                }
                columnDataList.add(pageInfoMap);
                idx++;
                batchData.next();
            }
        } catch (IOException e) {
            logger.error(
                    "Failed to get Aligned Page details, the TimePage statistics:{}",
            pageInfoList.get(0).getStatistics());
        }

        alignedTableView.setItems(columnDataList);
        alignedTableView.setLayoutX(0);
        alignedTableView.setLayoutY(0);
        alignedTableView.setPrefWidth(WIDTH);
        alignedTableView.setPrefHeight(HEIGHT * 0.9);

        stage.show();
        URL uiDarkCssResource = getClass().getClassLoader().getResource("css/ui-dark.css");
        if (uiDarkCssResource != null) {
            this.getScene().getStylesheets().add(uiDarkCssResource.toExternalForm());
        }
    }
}
