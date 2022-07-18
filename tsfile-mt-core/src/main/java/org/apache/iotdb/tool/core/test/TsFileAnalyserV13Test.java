package org.apache.iotdb.tool.core.test;

import org.apache.iotdb.tool.core.model.ChunkGroupMetadataModel;
import org.apache.iotdb.tool.core.model.IPageInfo;
import org.apache.iotdb.tool.core.model.PageInfo;
import org.apache.iotdb.tool.core.model.TimeSeriesMetadataNode;
import org.apache.iotdb.tool.core.service.TsFileAnalyserV13;
import org.apache.iotdb.tool.core.util.OffLineTsFileUtil;
import org.apache.iotdb.tsfile.read.common.BatchData;

import java.io.IOException;
import java.util.List;

public class TsFileAnalyserV13Test {

  public static void main(String[] strings) throws IOException, InterruptedException {
    TsFileAnalyserV13 tsFileAnalyserV13 = new TsFileAnalyserV13("1652336687038-87274-2-5.tsfile");

    System.out.println(
        OffLineTsFileUtil.fetchTsFileVersionNumber("1652336687038-87274-2-5.tsfile"));



    TimeSeriesMetadataNode node = tsFileAnalyserV13.getTimeSeriesMetadataNode();
    List<ChunkGroupMetadataModel> modelList = tsFileAnalyserV13.getChunkGroupMetadataModelList();
    List<IPageInfo> pageInfosList =
        tsFileAnalyserV13.fetchPageInfoListByIChunkMetadata(
            modelList.get(0).getChunkMetadataList().get(0));

    BatchData batchData = tsFileAnalyserV13.fetchBatchDataByPageInfo(pageInfosList.get(0));
    while (batchData.hasCurrent()) {
      System.out.println(
          batchData.currentTime() + " : " + batchData.currentTsPrimitiveType().getStringValue());
      batchData.next();
    }

    //    QueryDataSet result = tsFileAnalyserV13.queryResult(0, 0, "root.sg.device_1", "sensor_1",
    // "", 0, 0);
    //    while (result.hasNext()) {
    //        RowRecord rowRecord = result.next();
    //        System.out.println(rowRecord.toString());
    //    }
  }
}
