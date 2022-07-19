package org.apache.iotdb.tool.ui.view;

import org.apache.iotdb.tool.ui.config.TableAlign;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.util.Callback;

/**
 * base table view
 *
 * @author shenguanchu
 */
public class BaseTableView {

  public BaseTableView() {}

  public void tableViewInit(
      Pane pane,
      TableView tableView,
      ObservableList datas,
      boolean isShow,
      TableColumn... genColumn) {
    tableView.setItems(datas);
    tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    pane.getChildren().add(tableView);
    tableView.getColumns().addAll(genColumn);
    tableView.setVisible(isShow);
  }

  public TableColumn genColumn(TableAlign align, String showName, String name) {
    if (align == null) {
      align = TableAlign.CENTER;
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
                if (item == getItem()) {return;}
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
}
