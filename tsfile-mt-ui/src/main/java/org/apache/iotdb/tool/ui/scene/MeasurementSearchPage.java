package org.apache.iotdb.tool.ui.scene;

import com.browniebytes.javafx.control.DateTimePicker;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.ZoneId;
import java.util.Date;


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
        stage.setTitle("Search: measurement");
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
                e -> {
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
//                        ioTDBParsePage.showQueryDataSet(deviceIdTextText, measurementIdTextText, queryDataSet);
                        showQueryDataSet(deviceIdTextText, measurementIdTextText, queryDataSet);
                    } catch (Exception ex) {
                        logger.error("Failed to query data set, deviceId:{}, measurementId:{}", deviceIdTextText, measurementIdTextText);
                        ex.printStackTrace();
                    }
                });

        // search result
        searchResultPane = new AnchorPane();
        searchResultPane.setLayoutX(stage.getWidth() / 4);
        searchResultPane.setLayoutY(0);
        searchResultPane.setPrefHeight(stage.getHeight());
        searchResultPane.setPrefWidth(3 * stage.getWidth() / 4);
        searchResultPane.setStyle("-fx-background-color: rgb(44, 108, 131)");
        anchorPane.getChildren().add(searchResultPane);
        stage.heightProperty().addListener((observable, oldValueb, newValue) -> {
            searchResultPane.setPrefHeight(stage.getHeight());
        });
        stage.widthProperty().addListener((observable, oldValueb, newValue) -> {
            searchResultPane.setLayoutX(searchFilterBox.getWidth());
            searchResultPane.setPrefWidth(stage.getWidth() - searchFilterBox.getWidth());
        });
        // TODO 把 stage 的放大缩小按钮禁用掉

        tableViewInit(
                tvTableView,
                tvDatas,
                true,
                genColumn(IoTDBParsePageV13.TableAlign.CENTER, "timestamp", "timestamp"),
                genColumn(IoTDBParsePageV13.TableAlign.CENTER_LEFT, "value", "value"));
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

   private void tableViewInit(
           TableView tableView, ObservableList datas, boolean isShow, TableColumn... genColumn) {

        tableView.setItems(datas);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        searchResultPane.getChildren().add(tableView);
        tableView.getColumns().addAll(genColumn);
        tableView.setVisible(isShow);
    }

    private TableColumn genColumn(IoTDBParsePageV13.TableAlign align, String showName, String name) {

        if (align == null) {
            align = IoTDBParsePageV13.TableAlign.CENTER;
        }
        TableColumn column = new TableColumn<>(showName);
        column.setCellValueFactory(new PropertyValueFactory<>(name));
        column.setCellFactory(
                new Callback<TableColumn<?, ?>, TableCell<?, ?>>() {
                    private final Tooltip tooltip = new Tooltip();

                    @Override
                    public TableCell<?, ?> call(TableColumn<?, ?> param) {
                        return new TableCell<Object, Object>() {
                            @Override
                            protected void updateItem(Object item, boolean empty) {
                                if (item == getItem()) return;
                                super.updateItem(item, empty);
                                if (item == null) {
                                    super.setText(null);
                                    super.setGraphic(null);
                                } else if (item instanceof Node) {
                                    super.setText(null);
                                    super.setGraphic((Node) item);
                                } else {
                                    // tool tip
                                    super.setText(item.toString());
                                    super.setGraphic(null);
                                    super.setTooltip(tooltip);
                                    tooltip.setText(item.toString());
                                }
                            }
                        };
                    }
                });
        column.setStyle("-fx-alignment: " + align.getAlign() + ";");
        return column;
    }

    public void showQueryDataSet(String deviceId, String measurementId, QueryDataSet queryDataSet) throws Exception {
        // select root
//        this.treeView.getSelectionModel().select(0);

        // show datas
        this.tvDatas.clear();
//        this.pageDatas.clear();
//        this.chunkTableView.setVisible(false);
        while (queryDataSet.hasNext()) {
            RowRecord next = queryDataSet.next();
            StringBuilder sb = new StringBuilder();
            for (Field f : next.getFields()) {
                sb.append("\t");
                sb.append(f);
            }
            this.tvDatas.add(new IoTDBParsePageV13.TimesValues(new Date(next.getTimestamp()).toString(), sb.toString()));
        }
        this.tvTableView.setVisible(true);
//        this.pageTableView.setVisible(true);
    }
}
