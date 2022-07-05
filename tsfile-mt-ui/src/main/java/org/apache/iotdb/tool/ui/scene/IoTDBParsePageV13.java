package org.apache.iotdb.tool.ui.scene;

import org.apache.iotdb.tool.core.model.ChunkGroupMetadataModel;
import org.apache.iotdb.tool.core.model.TimeSeriesMetadataNode;
import org.apache.iotdb.tool.core.service.TsFileAnalyserV13;
import org.apache.iotdb.tool.ui.node.IndexNode;
import org.apache.iotdb.tool.ui.view.IconView;
import org.apache.iotdb.tsfile.file.header.ChunkHeader;
import org.apache.iotdb.tsfile.file.metadata.AlignedChunkMetadata;
import org.apache.iotdb.tsfile.file.metadata.ChunkMetadata;
import org.apache.iotdb.tsfile.file.metadata.IChunkMetadata;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.iotdb.tsfile.read.controller.IChunkLoader;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;

import com.sun.javafx.scene.control.skin.LabeledText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * IoTDBParsePage
 *
 * @author oortCloudFei
 */
public class IoTDBParsePageV13 extends IoTDBParsePage {

  private static final Logger logger = LoggerFactory.getLogger(IoTDBParsePageV13.class);
  public static final double WIDTH = 1080;
  public static final double HEIGHT = 750;
  // TODO 优化
  private static final String TREE_ITEM_TYPE_CHUNK_GROUP = "cg";
  private static final String TREE_ITEM_TYPE_CHUNK = "c";
  private static final String TREE_ITEM_TYPE_CHUNK_PAGE = "cp";
  private static final String TREE_ITEM_TYPE_FOLDER = "folder";
  private static final String TREE_ITEM_TYPE_TSFILE = "tsfile";

  /** version 13 interface */
  private TsFileAnalyserV13 tsFileAnalyserV13;

  /** index region */
  private Group indexGroup;

  /** TsFile Item */
  private TreeItem tsfileItem;

  private TreeView<ChunkTreeItemValue> treeView;

  /** table datas */
  private TableView tvTableView = new TableView();

  private ObservableList<TimesValues> tvDatas = FXCollections.observableArrayList();
  private TableView chunkTableView = new TableView();
  private ObservableList<ChunkInfo> chunkDatas = FXCollections.observableArrayList();
  private TableView pageTableView = new TableView();
  private ObservableList<PageInfo> pageDatas = FXCollections.observableArrayList();
  private TableView fileTableView = new TableView();

  /** click index to tree */
  private Map<String, TreeItem<ChunkTreeItemValue>> indexMap = new HashMap<>(256);

  private List<String> timeseriesList = new ArrayList<>();

  /** measurementSearch Stage */
  private MeasurementSearchPage measurementSearchPage;

  private TsFileInfoPage tsfileInfoPage;

  private ChunkInfoPage chunkInfoPage;

  private PageInfoPage pageInfoPage;

  private TsFileLoadPage tsFileLoadPage;

  private File selectedfolder;

  private boolean hasTsFileLoaded;

  private Stage tsfileLoadStage;

  public IoTDBParsePageV13() {
    super(new Group(), WIDTH, HEIGHT);
    tsFileLoadPage = new TsFileLoadPage();
  }

  public void setTsFileAnalyserV13(TsFileAnalyserV13 tsFileAnalyserV13) {
    this.tsFileAnalyserV13 = tsFileAnalyserV13;
  }

  // public
  public void init(Stage baseStage) {
    // TreeView Region
    treeView = new TreeView<ChunkTreeItemValue>();
    treeView.setLayoutX(0);
    treeView.setLayoutY(HEIGHT * 0.04);
    treeView.setPrefWidth(WIDTH * 0.3);
    treeView.setPrefHeight(HEIGHT * 0.93);
    this.root.getChildren().add(treeView);

    // 双击打开 tsfile 监听事件
    treeView.addEventHandler(
        MouseEvent.MOUSE_CLICKED,
        new EventHandler<MouseEvent>() {
          @Override
          public void handle(MouseEvent event) {
            MouseButton mouseButton = event.getButton();
            if (event.getTarget() instanceof LabeledText
                && mouseButton == MouseButton.PRIMARY
                && event.getClickCount() == 2) {
              // create new stage
              TreeItem<ChunkTreeItemValue> currItem =
                  treeView.getSelectionModel().getSelectedItem();
              if (currItem != null) {
                String type = currItem.getValue().getType();
                if (TREE_ITEM_TYPE_TSFILE.equals(type)) {
                  String filePath = (String) currItem.getValue().getParams();
                  // 1. file type check
                  if (!tsFileLoadPage.fileTypeCheck(filePath)) {
                    Alert alert =
                        new Alert(Alert.AlertType.ERROR, "Please choose TsFile!", ButtonType.OK);
                    alert.showAndWait();
                  }
                  // 2. file version check
                  if (!tsFileLoadPage.fileVersionCheck(filePath)) {
                    Alert alert =
                        new Alert(
                            Alert.AlertType.ERROR,
                            "Sorry, We currently only support the 3.0 TsFile version!",
                            ButtonType.OK);
                    alert.showAndWait();
                  }
                  tsfileLoadStage = new Stage();
                  tsfileLoadStage.initModality(Modality.APPLICATION_MODAL);
                  tsfileLoadStage.show();
                  // 3. load file 初始化, 实际上在弹窗 load
                  tsfileItem = currItem;
                  tsFileLoadPage = new TsFileLoadPage(tsfileLoadStage, filePath);
                  tsFileLoadPage.setIoTDBParsePageV13(IoTDBParsePageV13.this);
                }
              }
            }
          }
        });

    // tree listener
    treeView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              TreeItem<ChunkTreeItemValue> treeItem = observable.getValue();
              String type = treeItem.getValue().getType();
              if (TREE_ITEM_TYPE_CHUNK.equals(type)) {
                showItemChunk(treeItem);
              } else if (TREE_ITEM_TYPE_CHUNK_PAGE.equals(type)) {
                // TODO 删掉相关逻辑
                //                  showPageDetail(treeItem);
              } else if (TREE_ITEM_TYPE_TSFILE.equals(type)) {
                this.fileTableView.setVisible(true);
                this.chunkTableView.setVisible(false);
                this.tvTableView.setVisible(false);
                this.pageTableView.setVisible(false);
              } else {
                this.fileTableView.setVisible(false);
                this.chunkTableView.setVisible(false);
                this.tvTableView.setVisible(false);
                this.pageTableView.setVisible(false);
              }
            });

    // TODO
    // TreeView Menu
    ContextMenu treeViewMenu = new ContextMenu();
    treeView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              // TODO 对于已加载的文件才会显示
              // PlanA: 弹出警告框（文件未加载）
              // PlanB: 未加载的文件直接邮件没有反应
              TreeItem<ChunkTreeItemValue> currItem =
                  treeView.getSelectionModel().getSelectedItem();
              treeViewMenu.getItems().clear();
              String type = currItem.getValue().getType();
              switch (type) {
                case TREE_ITEM_TYPE_TSFILE:
                  MenuItem tsfileMenuItem = new MenuItem("tsfile details");
                  treeViewMenu.getItems().add(tsfileMenuItem);
                  tsfileMenuItem.setOnAction(
                      event -> {
                        Stage tsfileInfoStage = new Stage();
                        tsfileInfoStage.initStyle(StageStyle.UTILITY);
                        tsfileInfoStage.initModality(Modality.APPLICATION_MODAL);
                        tsfileInfoPage = new TsFileInfoPage(tsfileInfoStage, this);
                      });
                  break;
                case TREE_ITEM_TYPE_CHUNK:
                  MenuItem chunkMenuItem = new MenuItem("chunk details");
                  treeViewMenu.getItems().add(chunkMenuItem);
                  chunkMenuItem.setOnAction(
                      event -> {
                        Stage chunkInfoStage = new Stage();
                        chunkInfoStage.initStyle(StageStyle.UTILITY);
                        chunkInfoStage.initModality(Modality.APPLICATION_MODAL);
                        chunkInfoPage = new ChunkInfoPage(chunkInfoStage, this, currItem);
                      });
                  break;
                case TREE_ITEM_TYPE_CHUNK_PAGE:
                  MenuItem pageMenuItem = new MenuItem("page details");
                  treeViewMenu.getItems().add(pageMenuItem);
                  pageMenuItem.setOnAction(
                      event -> {
                        Stage pageInfoStage = new Stage();
                        pageInfoStage.initStyle(StageStyle.UTILITY);
                        pageInfoStage.initModality(Modality.APPLICATION_MODAL);
                        pageInfoPage = new PageInfoPage(pageInfoStage, this, currItem);
                      });
                  break;
                default:
                  logger.info("unexpect type:{}", type);
              }
            });
    treeView.setContextMenu(treeViewMenu);

    // TODO 代码结构（应该把这些都拆出来， menu 单独的）
    // menu region
    MenuBar menuBar = new MenuBar();
    menuBar.prefWidthProperty().bind(baseStage.widthProperty());
    this.root.getChildren().add(menuBar);
    menuBar.setLayoutX(0);
    menuBar.setLayoutY(0);
    menuBar.setPrefHeight(HEIGHT * 0.03);
    // load file
    Menu fileMenu = new Menu("File");
    MenuItem loadFileMenuItem = new MenuItem("Load");
    fileMenu.getItems().addAll(loadFileMenuItem);
    loadFileMenuItem.setOnAction(
        event -> {
          selectedfolder = tsFileLoadPage.loadFolder(baseStage);
          if (selectedfolder != null) {
            TreeItem<ChunkTreeItemValue> treeRoot =
                new TreeItem<>(
                    new ChunkTreeItemValue(selectedfolder.getName(), TREE_ITEM_TYPE_FOLDER, null));
            Node folderIcon = new IconView("/icons/folder-package.png");
            treeRoot.setGraphic(folderIcon);

            File[] files = selectedfolder.listFiles();
            // TODO 判断 files != null
            for (File f : files) {
              String filePath = f.getPath();
              TreeItem<ChunkTreeItemValue> fileItem =
                  new TreeItem<>(
                      new ChunkTreeItemValue(f.getName(), TREE_ITEM_TYPE_TSFILE, filePath));
              treeRoot.getChildren().add(fileItem);
              Node tsfileIcon = new IconView("/icons/folder-source.png");
              fileItem.setGraphic(tsfileIcon);
              // TODO 每个 fileItem 增加双击打开监听事件
              // 不能让用户重复点击某一个已打开的 tsfile
              // 清空缓存
              // 加载大文件，最后渲染时，进度条会卡主（此时文件已经加载完，在渲染）
              // 加载文件的窗口需要优化：按钮点击之后，不能重复点（可以隐藏起来）
              // 选中文件加 Enter 快捷键
            }
            treeView.setRoot(treeRoot);
            treeRoot.setExpanded(true);
          }
        });

    Menu searchMenu = new Menu("Search");
    CheckMenuItem searchMenuItem = new CheckMenuItem("Search Measurements (CTR + SHIFT + F)");
    searchMenu.getItems().addAll(searchMenuItem);
    Menu encodeMenu = new Menu("Encode");
    CheckMenuItem simulateMenuItem = new CheckMenuItem("Encode Simulation");
    encodeMenu.getItems().addAll(simulateMenuItem);
    Menu configMenu = new Menu("Config");
    Menu helpManeu = new Menu("Help");
    helpManeu.getItems().addAll(new CheckMenuItem("Documentation"), new CheckMenuItem("Contact"));
    menuBar.getMenus().addAll(fileMenu, searchMenu, encodeMenu, configMenu, helpManeu);

    // Measurement Search
    searchMenuItem.setOnAction(
        event -> {
          Stage measurementSearchStage = new Stage();
          measurementSearchStage.initStyle(StageStyle.UTILITY);
          measurementSearchPage = new MeasurementSearchPage(measurementSearchStage, this);
        });
    // TODO shorcut key binding: CTR+SHIFT+F

    // TimeSeries search
    HBox searchHBox = new HBox();
    TextField searchText = new TextField();
    searchText.setPromptText("Search...");
    searchText.getStyleClass().add("search-field");
    searchText.setOnKeyReleased(
        e -> {
          // TODO switch
          // Clear search
          if (e.getCode() == KeyCode.ESCAPE) searchText.setText("");
          // Navigation keys refocus the tree
          else if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
            treeView.requestFocus();
          }
        });
    Button searchButton = new Button();
    searchButton.setGraphic(new ImageView("/icons/find-light.png"));
    searchButton.getStyleClass().add("search-button");
    searchHBox.getChildren().addAll(searchText, searchButton);
    searchText.setPrefWidth(WIDTH * 0.27);
    searchHBox.setLayoutX(0);
    searchHBox.setLayoutY(HEIGHT * 0.968);
    this.root.getChildren().add(searchHBox);
    // search hidden button
    Button searchHiddenButton = new Button("searchHiddenButton");
    searchHiddenButton.setManaged(false);
    this.root.getChildren().add(searchHiddenButton);
    // shortcut key binding: CTR + F
    KeyCombination shButtonKC = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    this.getScene().getAccelerators().put(shButtonKC, searchHiddenButton::fire);
    // hiddenButton: focus event
    searchHiddenButton.setOnAction(
        event -> {
          searchText.requestFocus();
          searchText.selectAll();
        });
    // searchButton: ENTER shortcut binding
    KeyCombination sButtonKC = new KeyCodeCombination(KeyCode.ENTER);
    this.getScene().getAccelerators().put(sButtonKC, searchButton::fire);
    // searchButton: search event
    searchButton.setOnAction(
        event -> {
          // TODO
          // 1. 优化为忽略大小写
          // 2. 动态查询（例如 idea） 也可以全部高亮显示
          String searchResult = timeseriesSearch(searchText.getText());
          if (searchResult == null) {
            return;
          }
          chooseTree(searchResult);
        });

    // index region
    ScrollPane indexRegion = new ScrollPane();
    indexGroup = new Group();
    indexRegion.setContent(indexGroup);
    indexRegion.setLayoutX(WIDTH * 0.3);
    indexRegion.setLayoutY(HEIGHT * 0.04);
    indexRegion.setPrefWidth(WIDTH);
    indexRegion.setPrefHeight(HEIGHT * 0.96);
    root.getChildren().add(indexRegion);
    // TODO
    //    indexDataInit();

    URL uiDarkCssResource = getClass().getClassLoader().getResource("css/ui-dark.css");
    if (uiDarkCssResource != null) {
      this.getScene().getStylesheets().add(uiDarkCssResource.toExternalForm());
    }
  }

  public TsFileAnalyserV13 getTsFileAnalyserV13() {
    return tsFileAnalyserV13;
  }

  public String timeseriesSearch(String searchText) {
    for (String timeseriesStr : timeseriesList) {
      if (timeseriesStr.contains(searchText)) {
        return timeseriesStr;
      }
    }
    return "";
  }

  public void showQueryDataSet(String deviceId, String measurementId, QueryDataSet queryDataSet)
      throws Exception {
    // select root
    this.treeView.getSelectionModel().select(0);
    // show datas
    this.tvDatas.clear();
    this.pageDatas.clear();
    this.chunkTableView.setVisible(false);
    while (queryDataSet.hasNext()) {
      RowRecord next = queryDataSet.next();
      StringBuilder sb = new StringBuilder();
      for (Field f : next.getFields()) {
        sb.append("\t");
        sb.append(f);
      }
      this.tvDatas.add(new TimesValues(new Date(next.getTimestamp()).toString(), sb.toString()));
    }
    this.tvTableView.setVisible(true);
    this.pageTableView.setVisible(true);
  }

  /**
   * click index meta show tree item
   *
   * @param path
   */
  public void chooseTree(String path) {
    TreeItem<ChunkTreeItemValue> chunkTreeItemValueTreeItem = this.indexMap.get(path);
    if (chunkTreeItemValueTreeItem != null) {
      treeView.getSelectionModel().select(chunkTreeItemValueTreeItem);
      int index = treeView.getSelectionModel().selectedIndexProperty().get();
      treeView.scrollTo(index);
    }
  }

  /** index tree init */
  private void indexDataInit() {
    try {
      TimeSeriesMetadataNode timeSeriesMetadataNode =
          this.tsFileAnalyserV13.getTimeSeriesMetadataNode();
      if (timeSeriesMetadataNode == null) {
        throw new Exception("index is null");
      }
      IndexNode indexNode = new IndexNode(timeSeriesMetadataNode, null, this.indexGroup, this);
      indexNode.draw();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * click chunk item show detail
   *
   * @param value
   */
  public void showItemChunk(TreeItem<ChunkTreeItemValue> value) {
    this.tvTableView.setVisible(false);
    this.chunkDatas.clear();
    this.pageTableView.setVisible(false);
    this.fileTableView.setVisible(false);
    ChunkWrap params = (ChunkWrap) value.getValue().getParams();
    ChunkHeader chunkHeader = params.getChunkHeader();
    this.chunkDatas.add(
        new ChunkInfo(
            chunkHeader.getDataSize(),
            chunkHeader.getDataType().toString(),
            chunkHeader.getCompressionType().toString(),
            chunkHeader.getEncodingType().toString()));
    this.chunkTableView.setVisible(true);
    try {
      List<org.apache.iotdb.tool.core.model.PageInfo> pageInfoList =
          this.tsFileAnalyserV13.fetchPageInfoListByChunkMetadata(params.getiChunkMetadata());
      ObservableList<TreeItem<ChunkTreeItemValue>> chunkChild = value.getChildren();
      if (chunkChild == null) {
        return;
      }
      if (chunkChild.size() == 0) {
        if (pageInfoList != null && pageInfoList.get(0) != null) {
          for (int i = 1; i <= pageInfoList.size(); i++) {
            ChunkTreeItemValue pageValue =
                new ChunkTreeItemValue(
                    "page " + i, TREE_ITEM_TYPE_CHUNK_PAGE, pageInfoList.get(i - 1));
            TreeItem<ChunkTreeItemValue> pageItem = new TreeItem<>(pageValue);
            Node pageIcon = new IconView("/icons/text.png");
            pageItem.setGraphic(pageIcon);
            chunkChild.add(pageItem);
          }
        }
      }
    } catch (IOException e) {
      logger.error(
          "Failed to get pageInfo list of the chunk, the chunk dataType:{}",
          params.getiChunkMetadata().getDataType());
      e.printStackTrace();
      return;
    }
  }

  /** chunk group tree data set */
  public void chunkGroupTreeDataInit() {
    // 阻塞文件加载完成展示
    while (true) {
      // TODO double 精度问题  0.9999..........
      if (tsFileAnalyserV13.getRateOfProcess() >= 1) {
        break;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    List<ChunkGroupMetadataModel> chunkGroupMetadataModelList =
        this.tsFileAnalyserV13.getChunkGroupMetadataModelList();
    if (chunkGroupMetadataModelList == null) {
      return;
    }
    chunkGroupMetadataModelList.forEach(
        chunkGroupMetadataMode -> {
          ChunkTreeItemValue chunkGroupMetaItemValue =
              new ChunkTreeItemValue(
                  chunkGroupMetadataMode.getDevice(),
                  TREE_ITEM_TYPE_CHUNK_GROUP,
                  chunkGroupMetadataMode);
          TreeItem<ChunkTreeItemValue> chunkGroupMetaItem = new TreeItem<>(chunkGroupMetaItemValue);
          Node entityIcon = new IconView("icons/stack.png");
          chunkGroupMetaItem.setGraphic(entityIcon);
          tsfileItem.getChildren().add(chunkGroupMetaItem);

          timeseriesList.add(chunkGroupMetaItemValue.getName());
          indexMap.put(chunkGroupMetadataMode.getDevice(), chunkGroupMetaItem);
          // TODO
          List<IChunkMetadata> chunkMetadataList = chunkGroupMetadataMode.getChunkMetadataList();
          List<List<ChunkHeader>> chunkHeaderList = chunkGroupMetadataMode.getChunkHeaderList();
          if (chunkMetadataList != null && !chunkMetadataList.isEmpty() && chunkHeaderList != null && !chunkHeaderList.isEmpty()) {
            ChunkTreeItemValue chunkMetaItemValue = null;
            if (chunkMetadataList.get(0) != null) {
              // 1. aligned
              if (chunkMetadataList.get(0) instanceof AlignedChunkMetadata) {
                chunkGroupMetaItem.getValue().setName("[Aligned]" + chunkGroupMetaItem.getValue().getName());
                // 对齐 Chunk: chunkMetadataList, chunkHeaderList 的 size() 都为 1
                IChunkMetadata iChunkMetadata = chunkMetadataList.get(0);
                List<ChunkHeader> chunkHeaders = chunkHeaderList.get(0);
                AlignedChunkMetadata alignedChunkMetadata = (AlignedChunkMetadata)iChunkMetadata;
                for (int i = 0; i < chunkHeaders.size(); i++) {
                  if (i == 0) {
                    String timeChunkName = "TimeChunk";
                    chunkMetaItemValue =
                      new ChunkTreeItemValue(
                              timeChunkName,
                              TREE_ITEM_TYPE_CHUNK,
                              new ChunkWrap(alignedChunkMetadata, chunkHeaders.get(i))
                      );
                  } else {
                    String measurementId = alignedChunkMetadata.getValueChunkMetadataList().get(i - 1).getMeasurementUid();
                    chunkMetaItemValue =
                      new ChunkTreeItemValue(
                              measurementId,
                              TREE_ITEM_TYPE_CHUNK,
                              new ChunkWrap(alignedChunkMetadata, chunkHeaders.get(i))
                      );
                  }
                  TreeItem<ChunkTreeItemValue> chunkMetaItem = new TreeItem<>(chunkMetaItemValue);
                  Node measurementIcon = new IconView("icons/text-code.png");
                  chunkMetaItem.setGraphic(measurementIcon);
                  chunkGroupMetaItem.getChildren().add(chunkMetaItem);
                  timeseriesList.add(
                          chunkGroupMetaItemValue.getName() + "." + chunkMetaItemValue.getName());
                  indexMap.put(
                          chunkGroupMetaItemValue.getName() + "." + chunkMetaItemValue.getName(),
                          chunkMetaItem);
                }
              } else {
                // 2. non-aligned
                for (int i = 0; i < chunkMetadataList.size(); i++) {
                  IChunkMetadata iChunkMetadata = chunkMetadataList.get(i);
                  List<ChunkHeader> chunkHeaders = chunkHeaderList.get(i);
                  chunkMetaItemValue =
                          new ChunkTreeItemValue(
                                  iChunkMetadata.getMeasurementUid(),
                                  TREE_ITEM_TYPE_CHUNK,
                                  new ChunkWrap(iChunkMetadata, chunkHeaders.get(0))
                          );
                  TreeItem<ChunkTreeItemValue> chunkMetaItem = new TreeItem<>(chunkMetaItemValue);
                  Node measurementIcon = new IconView("icons/text-code.png");
                  chunkMetaItem.setGraphic(measurementIcon);
                  chunkGroupMetaItem.getChildren().add(chunkMetaItem);
                  timeseriesList.add(
                          chunkGroupMetaItemValue.getName() + "." + chunkMetaItemValue.getName());
                  indexMap.put(
                          chunkGroupMetaItemValue.getName() + "." + chunkMetaItemValue.getName(),
                          chunkMetaItem);
                }
              }
            }
          }
        });
    tsfileItem.setExpanded(true);
    tsfileLoadStage.close();

    // TODO  改成异步？
    indexDataInit();
  }

  @Override
  public String getName() {
    return "TsFileV0.13";
  }

  /** time value table */
  public static class TimesValues {

    private final SimpleStringProperty timestamp;
    private final SimpleStringProperty value;

    public TimesValues(String timestamp, String value) {
      this.timestamp = new SimpleStringProperty(timestamp);
      this.value = new SimpleStringProperty(value);
    }

    public String getTimestamp() {
      return timestamp.get();
    }

    public SimpleStringProperty timestampProperty() {
      return timestamp;
    }

    public void setTimestamp(String timestamp) {
      this.timestamp.set(timestamp);
    }

    public String getValue() {
      return value.get();
    }

    public SimpleStringProperty valueProperty() {
      return value;
    }

    public void setValue(String value) {
      this.value.set(value);
    }
  }

  /** chunk info table */
  public static class ChunkInfo {

    // ----measurmentId:targetHost,dataSize:10224,dataType:TEXT,compression:SNAPPY,encoding:PLAIN
    private final SimpleIntegerProperty dataSize;
    private final SimpleStringProperty dataType;
    private final SimpleStringProperty compression;
    private final SimpleStringProperty encoding;

    public ChunkInfo(int dataSize, String dataType, String compression, String encoding) {
      this.dataSize = new SimpleIntegerProperty(dataSize);
      this.dataType = new SimpleStringProperty(dataType);
      this.compression = new SimpleStringProperty(compression);
      this.encoding = new SimpleStringProperty(encoding);
    }

    public int getDataSize() {
      return dataSize.get();
    }

    public SimpleIntegerProperty dataSizeProperty() {
      return dataSize;
    }

    public void setDataSize(int dataSize) {
      this.dataSize.set(dataSize);
    }

    public String getDataType() {
      return dataType.get();
    }

    public SimpleStringProperty dataTypeProperty() {
      return dataType;
    }

    public void setDataType(String dataType) {
      this.dataType.set(dataType);
    }

    public String getCompression() {
      return compression.get();
    }

    public SimpleStringProperty compressionProperty() {
      return compression;
    }

    public void setCompression(String compression) {
      this.compression.set(compression);
    }

    public String getEncoding() {
      return encoding.get();
    }

    public SimpleStringProperty encodingProperty() {
      return encoding;
    }

    public void setEncoding(String encoding) {
      this.encoding.set(encoding);
    }
  }

  /** time value table */
  public static class PageInfo {

    private final SimpleIntegerProperty uncompressedSize;
    private final SimpleIntegerProperty compressedSize;
    private final SimpleStringProperty statistics;

    public PageInfo(int uncompressedSize, int compressedSize, String statistics) {
      this.uncompressedSize = new SimpleIntegerProperty(uncompressedSize);
      this.compressedSize = new SimpleIntegerProperty(compressedSize);
      this.statistics = new SimpleStringProperty(statistics);
    }

    public int getUncompressedSize() {
      return uncompressedSize.get();
    }

    public SimpleIntegerProperty uncompressedSizeProperty() {
      return uncompressedSize;
    }

    public void setUncompressedSize(int uncompressedSize) {
      this.uncompressedSize.set(uncompressedSize);
    }

    public int getCompressedSize() {
      return compressedSize.get();
    }

    public SimpleIntegerProperty compressedSizeProperty() {
      return compressedSize;
    }

    public void setCompressedSize(int compressedSize) {
      this.compressedSize.set(compressedSize);
    }

    public String getStatistics() {
      return statistics.get();
    }

    public SimpleStringProperty statisticsProperty() {
      return statistics;
    }

    public void setStatistics(String statistics) {
      this.statistics.set(statistics);
    }
  }

  public static class FileInfo {

    private final SimpleIntegerProperty fileVersion;
    private final SimpleLongProperty fileSize;
    private final SimpleLongProperty dataCounts;

    public FileInfo(int fileVersion, long fileSize, long dataCounts) {
      this.fileVersion = new SimpleIntegerProperty(fileVersion);
      this.fileSize = new SimpleLongProperty(fileSize);
      this.dataCounts = new SimpleLongProperty(dataCounts);
    }

    public int getFileVersion() {
      return fileVersion.get();
    }

    public SimpleIntegerProperty fileVersionProperty() {
      return fileVersion;
    }

    public void setFileVersion(int fileVersion) {
      this.fileVersion.set(fileVersion);
    }

    public long getFileSize() {
      return fileSize.get();
    }

    public SimpleLongProperty fileSizeProperty() {
      return fileSize;
    }

    public void setFileSize(int fileSize) {
      this.fileSize.set(fileSize);
    }

    public long getDataCounts() {
      return dataCounts.get();
    }

    public SimpleLongProperty dataCountsProperty() {
      return dataCounts;
    }

    public void setDataCounts(int dataCounts) {
      this.dataCounts.set(dataCounts);
    }
  }

  public class ChunkTreeItemValue {

    private String name;
    private String type;
    private Object params;
    //    private Button button;

    public ChunkTreeItemValue(String name, String type, Object params) {
      this.name = name;
      this.type = type;
      this.params = params;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Object getParams() {
      return params;
    }

    public void setParams(Object params) {
      this.params = params;
    }

    @Override
    public String toString() {
      return getName();
    }
  }

  public static class ChunkWrap {
    public IChunkMetadata iChunkMetadata;
    public ChunkHeader chunkHeader;

    public ChunkWrap(IChunkMetadata iChunkMetadata, ChunkHeader chunkHeader) {
      this.iChunkMetadata = iChunkMetadata;
      this.chunkHeader = chunkHeader;
    }

    public IChunkMetadata getiChunkMetadata() {
      return iChunkMetadata;
    }

    public void setChunkMetadata(IChunkMetadata iChunkMetadata) {
      this.iChunkMetadata = iChunkMetadata;
    }

    public ChunkHeader getChunkHeader() {
      return chunkHeader;
    }

    public void setChunkHeader(ChunkHeader chunkHeader) {
      this.chunkHeader = chunkHeader;
    }
  }
}
