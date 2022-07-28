package org.apache.iotdb.tool.ui.table;

import javafx.beans.property.SimpleStringProperty;
import org.apache.iotdb.tool.ui.scene.IoTDBParsePageV3;

/**
 * EncodeCompressAnalyseTable
 *
 * @author shenguanchu
 */
public class EncodeCompressAnalyseTable extends IoTDBParsePageV3.TimesValues {
  private final SimpleStringProperty typeName;

  private final SimpleStringProperty encodeName;

  private final SimpleStringProperty compressName;

  private final SimpleStringProperty originSize;

  private final SimpleStringProperty encodedSize;

  private final SimpleStringProperty uncompressSize;

  private final SimpleStringProperty compressedSize;

  private final SimpleStringProperty compressedCost;

  public EncodeCompressAnalyseTable(
          String typeName,
          String encodeName,
          String compressName,
          String originSize,
          String encodedSize,
          String uncompressSize,
          String compressedSize,
          String compressedCost) {
    this.typeName = new SimpleStringProperty(typeName);
    this.encodeName = new SimpleStringProperty(encodeName);
    this.compressName = new SimpleStringProperty(compressName);
    this.originSize = new SimpleStringProperty(originSize);
    this.encodedSize = new SimpleStringProperty(encodedSize);
    this.uncompressSize = new SimpleStringProperty(uncompressSize);
    this.compressedSize = new SimpleStringProperty(compressedSize);
    this.compressedCost = new SimpleStringProperty(compressedCost);
  }

  public String getTypeName() {
    return typeName.get();
  }

  public SimpleStringProperty typeNameProperty() {
    return typeName;
  }

  public void setTypeName(String typeName) {
    this.typeName.set(typeName);
  }

  public String getEncodeName() {
    return encodeName.get();
  }

  public SimpleStringProperty encodeNameProperty() {
    return encodeName;
  }

  public void setEncodeName(String encodeName) {
    this.encodeName.set(encodeName);
  }

  public String getCompressName() {
    return compressName.get();
  }

  public SimpleStringProperty compressNameProperty() {
    return compressName;
  }

  public void setCompressName(String compressName) {
    this.compressName.set(compressName);
  }

  public String getOriginSize() {
    return originSize.get();
  }

  public SimpleStringProperty originSizeProperty() {
    return originSize;
  }

  public void setOriginSize(String originSize) {
    this.originSize.set(originSize);
  }

  public String getEncodedSize() {
    return encodedSize.get();
  }

  public SimpleStringProperty encodedSizeProperty() {
    return encodedSize;
  }

  public void setEncodedSize(String encodedSize) {
    this.encodedSize.set(encodedSize);
  }

  public String getUncompressSize() {
    return uncompressSize.get();
  }

  public SimpleStringProperty uncompressSizeProperty() {
    return uncompressSize;
  }

  public void setUncompressSize(String uncompressSize) {
    this.uncompressSize.set(uncompressSize);
  }

  public String getCompressedSize() {
    return compressedSize.get();
  }

  public SimpleStringProperty compressedSizeProperty() {
    return compressedSize;
  }

  public void setCompressedSize(String compressedSize) {
    this.compressedSize.set(compressedSize);
  }

  public String getCompressedCost() {
    return compressedCost.get();
  }

  public SimpleStringProperty compressedCostProperty() {
    return compressedCost;
  }

  public void setCompressedCost(String compressedCost) {
    this.compressedCost.set(compressedCost);
  }
}
