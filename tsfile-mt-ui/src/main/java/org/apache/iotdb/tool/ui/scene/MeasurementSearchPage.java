package org.apache.iotdb.tool.ui.scene;

import com.browniebytes.javafx.control.DateTimePicker;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.iotdb.tool.ui.config.TableAlign;
import org.apache.iotdb.tool.ui.view.BaseTableView;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.ZoneId;
import java.util.Date;

/**
 * measurement search stage
 *
 * @author shenguanchu
 */
public class MeasurementSearchPage {

    private static final Logger logger = LoggerFactory.getLogger(IoTDBParsePageV13.class);

    private static final double WIDTH = 1080;
    private static final double HEIGHT = 300;

    private AnchorPane anchorPane;
    private Scene scene;
    private IoTDBParsePageV13 ioTDBParsePage;

    private VBox searchFilterBox;

    private AnchorPane searchResultPane;

    /** table datas */
    private TableView tvTableView = new TableView();

    private ObservableList<IoTDBParsePageV13.TimesValues> tvDatas = FXCollections.observableArrayList();

    public MeasurementSearchPage(Stage stage, IoTDBParsePageV13 ioTDBParsePage) {
        this.ioTDBParsePage = ioTDBParsePage;
        init(stage);
    }

    public Scene getScene() {
        return scene;
    }

    private void init(Stage stage) {

        anchorPane = new AnchorPane();
        scene = new Scene(anchorPane, WIDTH, HEIGHT);
        stage.setScene(scene);
        stage.setTitle("Search: Measurement");
        stage.show();

        // search filter
        searchFilterBox = new VBox();
        anchorPane.getChildren().add(searchFilterBox);
        searchFilterBox.getStyleClass().add("search-filter-box");

        searchFilterBox.setPrefWidth(stage.getWidth() / 4);
        searchFilterBox.setPrefHeight(stage.getHeight());
        stage.heightProperty().addListener((observable, oldValue, newValue) -> {
            searchFilterBox.setPrefHeight(stage.getHeight());
        });
        stage.widthProperty().addListener((observable, oldValueb, newValue) -> {
            searchFilterBox.setPrefWidth(stage.getWidth() / 4);
        });

        Label startTime = new Label("startTime:");
        DateTimePicker startPicker = new DateTimePicker();
        Label endTime = new Label("endTime:");
        DateTimePicker endPicker = new DateTimePicker();
        searchFilterBox.getChildren().addAll(startTime, startPicker, endTime, endPicker);

        Label deviceIdLabel = new Label("deviceId:");
        TextField deviceIdText = new TextField();
        Label measurementIdLabel = new Label("measurementId:");
        TextField measurementIdText = new TextField();
        Button searchButton = new Button("Search");
        searchButton.setGraphic(new ImageView("/icons/find-light.png"));
        searchButton.getStyleClass().add("search-button");
        searchFilterBox.getChildren().addAll(deviceIdLabel, deviceIdText, measurementIdLabel, measurementIdText, searchButton);

        // shortcut: Enter
//        KeyCombination searchButtonKC = new KeyCodeCombination(KeyCode.ENTER);
//        this.getScene().getAccelerators().put(searchButtonKC, ()-> searchButton.fire());

        // button click event
        searchButton.setOnMouseClicked(
                event -> {
                    long startLocalTime =
                            startPicker
                                    .dateTimeProperty()
                                    .getValue()
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli();
                    long endLocalTime =
                            endPicker
                                    .dateTimeProperty()
                                    .getValue()
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli();
                    String deviceIdTextText = deviceIdText.getText();
                    String measurementIdTextText = measurementIdText.getText();
                    try {
                        QueryDataSet queryDataSet =
                                ioTDBParsePage
                                        .getTsFileAnalyserV13()
                                        .queryResult(
                                                startLocalTime,
                                                endLocalTime,
                                                deviceIdTextText,
                                                measurementIdTextText,
                                                "", 0, 0);
                        showQueryDataSet(queryDataSet);
                    } catch (Exception exception) {
                        logger.error("Failed to query data set, deviceId:{}, measurementId:{}", deviceIdTextText, measurementIdTextText);
                        exception.printStackTrace();
                    }
                });

        // search result
        searchResultPane = new AnchorPane();
        searchResultPane.setLayoutX(stage.getWidth() / 4);
        searchResultPane.setLayoutY(0);
        searchResultPane.setPrefHeight(stage.getHeight());
        searchResultPane.setPrefWidth(3 * stage.getWidth() / 4);
        anchorPane.getChildren().add(searchResultPane);
        stage.heightProperty().addListener((observable, oldValueb, newValue) -> {
            searchResultPane.setPrefHeight(stage.getHeight());
        });
        stage.widthProperty().addListener((observable, oldValueb, newValue) -> {
            searchResultPane.setLayoutX(searchFilterBox.getWidth());
            searchResultPane.setPrefWidth(stage.getWidth() - searchFilterBox.getWidth());
        });
        // TODO
        // 4. ENTER 绑定

        BaseTableView baseTableView = new BaseTableView();
        TableColumn timestampCol = baseTableView.genColumn(TableAlign.CENTER, "timestamp", "timestamp");
        TableColumn valueCol = baseTableView.genColumn(TableAlign.CENTER_LEFT, "value", "value");
        baseTableView.tableViewInit(
                searchResultPane,
                tvTableView,
                tvDatas,
                true,
                timestampCol,
                valueCol);
        tvTableView.setLayoutX(searchFilterBox.getWidth());
        tvTableView.setLayoutY(0);
        tvTableView.setPrefWidth(searchResultPane.getPrefWidth() - 15);
        tvTableView.setPrefHeight(searchResultPane.getPrefHeight());

        searchResultPane.widthProperty().addListener((observable, oldValueb, newValue) -> {
            tvTableView.setPrefWidth(searchResultPane.getPrefWidth() - 15);
        });
        searchResultPane.heightProperty().addListener((observable, oldValueb, newValue) -> {
            tvTableView.setPrefHeight(searchResultPane.getPrefHeight());
        });

        URL uiDarkCssResource = getClass().getClassLoader().getResource("css/ui-dark.css");
        if (uiDarkCssResource != null) {
            this.getScene().getStylesheets().add(uiDarkCssResource.toExternalForm());
        }
    }

    public void showQueryDataSet(QueryDataSet queryDataSet) throws Exception {
        tvDatas.clear();
        while (queryDataSet.hasNext()) {
            RowRecord next = queryDataSet.next();
            StringBuilder sb = new StringBuilder();
            for (Field f : next.getFields()) {
                sb.append("\t");
                sb.append(f);
            }
            tvDatas.add(new IoTDBParsePageV13.TimesValues(new Date(next.getTimestamp()).toString(), sb.toString()));
        }
        tvTableView.setVisible(true);
    }
}
