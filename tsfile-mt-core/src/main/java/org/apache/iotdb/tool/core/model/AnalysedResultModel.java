package org.apache.iotdb.tool.core.model;

import java.util.List;

public class AnalysedResultModel {

  private EncodeCompressAnalysedModel currentAnalysed;

  private List<EncodeCompressAnalysedModel> analysedList;

  public EncodeCompressAnalysedModel getCurrentAnalysed() {
    return currentAnalysed;
  }

  public void setCurrentAnalysed(EncodeCompressAnalysedModel currentAnalysed) {
    this.currentAnalysed = currentAnalysed;
  }

  public List<EncodeCompressAnalysedModel> getAnalysedList() {
    return analysedList;
  }

  public void setAnalysedList(List<EncodeCompressAnalysedModel> analysedList) {
    this.analysedList = analysedList;
  }
}
