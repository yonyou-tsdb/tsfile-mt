package org.apache.iotdb.tool.core.service;

import org.apache.iotdb.tool.core.model.*;
import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.common.constant.TsFileConstant;
import org.apache.iotdb.tsfile.encoding.decoder.Decoder;
import org.apache.iotdb.tsfile.file.MetaMarker;
import org.apache.iotdb.tsfile.file.header.ChunkGroupHeader;
import org.apache.iotdb.tsfile.file.header.ChunkHeader;
import org.apache.iotdb.tsfile.file.header.PageHeader;
import org.apache.iotdb.tsfile.file.metadata.*;
import org.apache.iotdb.tsfile.file.metadata.enums.MetadataIndexNodeType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.file.metadata.statistics.Statistics;
import org.apache.iotdb.tsfile.fileSystem.FSFactoryProducer;
import org.apache.iotdb.tsfile.read.TsFileReader;
import org.apache.iotdb.tsfile.read.TsFileSequenceReader;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.expression.IExpression;
import org.apache.iotdb.tsfile.read.expression.QueryExpression;
import org.apache.iotdb.tsfile.read.expression.impl.BinaryExpression;
import org.apache.iotdb.tsfile.read.expression.impl.GlobalTimeExpression;
import org.apache.iotdb.tsfile.read.expression.impl.SingleSeriesExpression;
import org.apache.iotdb.tsfile.read.filter.TimeFilter;
import org.apache.iotdb.tsfile.read.filter.ValueFilter;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.apache.iotdb.tsfile.read.reader.page.AlignedPageReader;
import org.apache.iotdb.tsfile.read.reader.page.PageReader;
import org.apache.iotdb.tsfile.read.reader.page.TimePageReader;
import org.apache.iotdb.tsfile.read.reader.page.ValuePageReader;
import org.apache.iotdb.tsfile.utils.Pair;
import org.apache.iotdb.tsfile.utils.TsPrimitiveType;
import org.apache.iotdb.tsfile.write.schema.IMeasurementSchema;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class TsFileAnalyserV13 {

  private static final Logger logger = LoggerFactory.getLogger(TsFileAnalyserV13.class);
  private final long fileSize;
  private final boolean seq = true;
  private final String version;
  private final String filePath;
  private final TsFileAnalysedToolReader reader;
  private final List<ChunkGroupMetadataModel> chunkGroupMetadataModelList = new ArrayList<>();
  private final Map<Path, IMeasurementSchema> newSchema = new HashMap<>();

  private TimeSeriesMetadataNode timeSeriesMetadataNode;

  private long allCount;

  private double rateOfProcess;

  private double loadOfPercent = 0.05;

  private double parseOfPercent = 0.9;

  private double indexOfPercent = 0.05;

  private CountDownLatch countDownLatch = new CountDownLatch(1);

  private final byte CHUNK_HEADER_MASK = (byte) 0x3F;

  public TsFileAnalyserV13(String filePath) throws IOException {
    this.filePath = filePath;
    reader = new TsFileAnalysedToolReader(filePath);
    fileSize = FSFactoryProducer.getFSFactory().getFile(filePath).length();
    version = reader.readVersionNumber() + "";
    rateOfProcess = loadOfPercent;
    // 异步加载
    new Thread(
            () -> {
              try {
                initTsFileAnalysed();
                timeSeriesMetadataNode = initTimeSeriesMetadataNodeAnalysed();
                rateOfProcess = 1.0;
                logger.info("init completed!");
                countDownLatch.countDown();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .start();
  }

  private void initTsFileAnalysed() throws IOException {

    ChunkMetadata currentChunk;
    String measurementID;
    TSDataType dataType;
    long fileOffsetOfChunk;

    // ChunkMetadata of current ChunkGroup
    List<IChunkMetadata> chunkMetadataList = new ArrayList<>();
    List<List<ChunkHeader>> chunkHeaderList = new ArrayList<>();

    long headerLength = TSFileConfig.MAGIC_STRING.getBytes().length + Byte.BYTES;

    reader.position(headerLength);

    byte marker;
    List<long[]> timeBatch = new ArrayList<>();
    String lastDeviceId = null;
    List<IMeasurementSchema> measurementSchemaList = new ArrayList<>();
    ChunkMetadata alignedTimeChunk = null;
    List<IChunkMetadata> alignedValueChunk = new ArrayList<>();
    List<ChunkHeader> alignedChunkHeader = new ArrayList<>();
    // 0 NonAligned, 1 TimeColumn, 2 ValueColumn
    int alignedFlag = 0;
    try {
      while ((marker = reader.readMarker()) != MetaMarker.SEPARATOR) {
        switch (marker) {
          case MetaMarker.CHUNK_HEADER:
          case MetaMarker.TIME_CHUNK_HEADER:
          case MetaMarker.VALUE_CHUNK_HEADER:
          case MetaMarker.ONLY_ONE_PAGE_CHUNK_HEADER:
          case MetaMarker.ONLY_ONE_PAGE_TIME_CHUNK_HEADER:
          case MetaMarker.ONLY_ONE_PAGE_VALUE_CHUNK_HEADER:
            setRateOfProcess();
            fileOffsetOfChunk = reader.position() - 1;
            // if there is something wrong with a chunk, we will drop the whole ChunkGroup
            // as different chunks may be created by the same insertions(sqls), and partial
            // insertion is not tolerable
            ChunkHeader chunkHeader = reader.readChunkHeader(marker);
            measurementID = chunkHeader.getMeasurementID();
            IMeasurementSchema measurementSchema =
                new MeasurementSchema(
                    measurementID,
                    chunkHeader.getDataType(),
                    chunkHeader.getEncodingType(),
                    chunkHeader.getCompressionType());
            measurementSchemaList.add(measurementSchema);
            dataType = chunkHeader.getDataType();
            if (chunkHeader.getDataType() == TSDataType.VECTOR) {
              timeBatch.clear();
            }
            Statistics<? extends Serializable> chunkStatistics =
                Statistics.getStatsByType(dataType);
            int dataSize = chunkHeader.getDataSize();

            if (dataSize > 0) {
              if (((byte) (chunkHeader.getChunkType() & CHUNK_HEADER_MASK))
                  == MetaMarker
                      .CHUNK_HEADER) { // more than one page, we could use page statistics to
                // generate chunk statistic
                while (dataSize > 0) {
                  setRateOfProcess();
                  // a new Page
                  PageHeader pageHeader = reader.readPageHeader(chunkHeader.getDataType(), true);
                  if (pageHeader.getUncompressedSize() != 0) {
                    // not empty page
                    chunkStatistics.mergeStatistics(pageHeader.getStatistics());
                  }
                  reader.skipPageData(pageHeader);
                  dataSize -= pageHeader.getSerializedPageSize();
                  chunkHeader.increasePageNums(1);
                }
                if ((chunkHeader.getChunkType() & TsFileConstant.TIME_COLUMN_MASK)
                    == TsFileConstant.TIME_COLUMN_MASK) {
                  alignedFlag = 1;
                } else if ((chunkHeader.getChunkType() & TsFileConstant.VALUE_COLUMN_MASK)
                    == TsFileConstant.VALUE_COLUMN_MASK) {
                  alignedFlag = 2;
                }
              } else { // only one page without statistic, we need to iterate each point to generate
                // chunk statistic
                PageHeader pageHeader = reader.readPageHeader(chunkHeader.getDataType(), false);
                Decoder valueDecoder =
                    Decoder.getDecoderByType(
                        chunkHeader.getEncodingType(), chunkHeader.getDataType());
                ByteBuffer pageData = reader.readPage(pageHeader, chunkHeader.getCompressionType());
                Decoder timeDecoder =
                    Decoder.getDecoderByType(
                        TSEncoding.valueOf(
                            TSFileDescriptor.getInstance().getConfig().getTimeEncoder()),
                        TSDataType.INT64);
                setRateOfProcess();
                if ((chunkHeader.getChunkType() & TsFileConstant.TIME_COLUMN_MASK)
                    == TsFileConstant.TIME_COLUMN_MASK) { // Time Chunk with only one page

                  TimePageReader timePageReader =
                      new TimePageReader(pageHeader, pageData, timeDecoder);
                  long[] currentTimeBatch = timePageReader.getNextTimeBatch();
                  timeBatch.add(currentTimeBatch);
                  for (long currentTime : currentTimeBatch) {
                    chunkStatistics.update(currentTime);
                  }
                  alignedFlag = 1;
                } else if ((chunkHeader.getChunkType() & TsFileConstant.VALUE_COLUMN_MASK)
                    == TsFileConstant.VALUE_COLUMN_MASK) { // Value Chunk with only one page

                  ValuePageReader valuePageReader =
                      new ValuePageReader(
                          pageHeader, pageData, chunkHeader.getDataType(), valueDecoder);
                  TsPrimitiveType[] valueBatch = valuePageReader.nextValueBatch(timeBatch.get(0));

                  if (valueBatch != null && valueBatch.length != 0) {
                    for (int i = 0; i < valueBatch.length; i++) {
                      TsPrimitiveType value = valueBatch[i];
                      if (value == null) {
                        continue;
                      }
                      long timeStamp = timeBatch.get(0)[i];
                      setChunkStatistics(chunkStatistics, timeStamp, value, dataType);
                    }
                  }
                  alignedFlag = 2;
                } else { // NonAligned Chunk with only one page
                  PageReader reader =
                      new PageReader(
                          pageHeader,
                          pageData,
                          chunkHeader.getDataType(),
                          valueDecoder,
                          timeDecoder,
                          null);
                  BatchData batchData = reader.getAllSatisfiedPageData();
                  while (batchData.hasCurrent()) {
                    setChunkStatistics(
                        chunkStatistics,
                        batchData.currentTime(),
                        batchData.currentTsPrimitiveType(),
                        dataType);
                    batchData.next();
                  }
                  alignedFlag = 0;
                }
                chunkHeader.increasePageNums(1);
              }
            }
            currentChunk =
                new ChunkMetadata(measurementID, dataType, fileOffsetOfChunk, chunkStatistics);

            if (alignedFlag == 1) {
              alignedTimeChunk = currentChunk;
              alignedChunkHeader.add(chunkHeader);
            } else if (alignedFlag == 2) {
              alignedValueChunk.add(currentChunk);
              alignedChunkHeader.add(chunkHeader);
            } else {
              if (alignedTimeChunk != null && alignedValueChunk.size() > 0) {
                chunkMetadataList.add(
                    new AlignedChunkMetadata(alignedTimeChunk, new ArrayList<>(alignedValueChunk)));
                chunkHeaderList.add(new ArrayList<>(alignedChunkHeader));
                alignedValueChunk.clear();
                alignedChunkHeader.clear();
              } else {
                chunkMetadataList.add(currentChunk);
                alignedChunkHeader.add(chunkHeader);
                chunkHeaderList.add(new ArrayList<>(alignedChunkHeader));
              }
            }

            break;
          case MetaMarker.CHUNK_GROUP_HEADER:
            // if there is something wrong with the ChunkGroup Header, we will drop this ChunkGroup
            // because we can not guarantee the correctness of the deviceId.
            logger.info("Starting read a new ChunkGroupHeader, lastDeviceId:{}", lastDeviceId);
            setRateOfProcess();
            if (lastDeviceId != null) {
              // schema of last chunk group
              if (newSchema != null) {
                for (IMeasurementSchema tsSchema : measurementSchemaList) {
                  newSchema.putIfAbsent(
                      new Path(lastDeviceId, tsSchema.getMeasurementId()), tsSchema);
                }
              }
              measurementSchemaList = new ArrayList<>();

              if (alignedTimeChunk != null && alignedValueChunk.size() > 0) {
                chunkMetadataList.add(
                    new AlignedChunkMetadata(alignedTimeChunk, new ArrayList<>(alignedValueChunk)));
                chunkHeaderList.add(new ArrayList<>(alignedChunkHeader));
                alignedValueChunk.clear();
                alignedChunkHeader.clear();
              }
              // last chunk group Metadata
              chunkGroupMetadataModelList.add(
                  new ChunkGroupMetadataModel(
                      lastDeviceId,
                      new ArrayList<>(chunkMetadataList),
                      new ArrayList<>(chunkHeaderList)));
              chunkMetadataList.clear();
              chunkHeaderList.clear();
            }
            // this is a chunk group
            chunkMetadataList = new ArrayList<>();
            ChunkGroupHeader chunkGroupHeader = reader.readChunkGroupHeader();
            lastDeviceId = chunkGroupHeader.getDeviceID();
            break;
          case MetaMarker.OPERATION_INDEX_RANGE:
            logger.info("Starting read OperationIndexRange, lastDeviceId:{}", lastDeviceId);
            setRateOfProcess();
            if (lastDeviceId != null) {
              // schema of last chunk group
              if (newSchema != null) {
                for (IMeasurementSchema tsSchema : measurementSchemaList) {
                  newSchema.putIfAbsent(
                      new Path(lastDeviceId, tsSchema.getMeasurementId()), tsSchema);
                }
              }
              measurementSchemaList = new ArrayList<>();
              if (alignedTimeChunk != null && alignedValueChunk.size() > 0) {
                chunkMetadataList.add(
                    new AlignedChunkMetadata(alignedTimeChunk, new ArrayList<>(alignedValueChunk)));
                chunkHeaderList.add(new ArrayList<>(alignedChunkHeader));
                alignedValueChunk.clear();
                alignedChunkHeader.clear();
              }
              // last chunk group Metadata
              chunkGroupMetadataModelList.add(
                  new ChunkGroupMetadataModel(
                      lastDeviceId,
                      new ArrayList<>(chunkMetadataList),
                      new ArrayList<>(chunkHeaderList)));
              chunkMetadataList.clear();
              chunkHeaderList.clear();
              lastDeviceId = null;
            }
            reader.readPlanIndex();
            break;
          default:
            // the disk file is corrupted, using this file may be dangerous
            logger.error("Unexpected marker:{}", marker);
            throw new IOException("Unexpected marker " + marker);
        }
      }
      // now we read the tail of the data section, so we are sure that the last
      // ChunkGroupFooter is complete.
      if (lastDeviceId != null) {
        logger.info("Read the tail of the data section, the lastDeviceId:{}", lastDeviceId);
        setRateOfProcess();
        // schema of last chunk group
        if (newSchema != null) {
          for (IMeasurementSchema tsSchema : measurementSchemaList) {
            newSchema.putIfAbsent(new Path(lastDeviceId, tsSchema.getMeasurementId()), tsSchema);
          }
        }
        if (alignedTimeChunk != null && alignedValueChunk.size() > 0) {
          chunkMetadataList.add(
              new AlignedChunkMetadata(alignedTimeChunk, new ArrayList<>(alignedValueChunk)));
          chunkHeaderList.add(new ArrayList<>(alignedChunkHeader));
          alignedValueChunk.clear();
          alignedChunkHeader.clear();
        }
        // last chunk group Metadata
        chunkGroupMetadataModelList.add(
            new ChunkGroupMetadataModel(
                lastDeviceId,
                new ArrayList<>(chunkMetadataList),
                new ArrayList<>(chunkHeaderList)));
        chunkMetadataList.clear();
        chunkHeaderList.clear();
      }

    } catch (Exception e) {
      logger.warn(
          "TsFile {} self-check cannot proceed at position {}, recovered, because : {}",
          filePath,
          reader.position(),
          e.getMessage());
    }
  }

  private void setChunkStatistics(
      Statistics<? extends Serializable> chunkStatistics,
      long currentTime,
      TsPrimitiveType value,
      TSDataType dataType)
      throws IOException {
    switch (dataType) {
      case INT32:
        chunkStatistics.update(currentTime, value.getInt());
        break;
      case INT64:
        chunkStatistics.update(currentTime, value.getLong());
        break;
      case FLOAT:
        chunkStatistics.update(currentTime, value.getFloat());
        break;
      case DOUBLE:
        chunkStatistics.update(currentTime, value.getDouble());
        break;
      case BOOLEAN:
        chunkStatistics.update(currentTime, value.getBoolean());
        break;
      case TEXT:
        chunkStatistics.update(currentTime, value.getBinary());
        break;
      default:
        logger.error("Unexpected type:{}", dataType);
        throw new IOException("Unexpected type " + dataType);
    }
  }

  private void setRateOfProcess() throws IOException {
    rateOfProcess = loadOfPercent + reader.position() / (double) fileSize * parseOfPercent;
  }

  private TimeSeriesMetadataNode initTimeSeriesMetadataNodeAnalysed() throws IOException {
    return reader.getAllTimeseriesMetadataWithOffset();
  }

  /**
   * 通过chunkMetadata获取chunk实例
   *
   * @param chunkMetadata
   * @return ChunkModel
   * @throws IOException
   */
  public ChunkModel fetchChunkByChunkMetadata(ChunkMetadata chunkMetadata)
      throws IOException, InterruptedException {

    countDownLatch.await();
    long offsetOfChunkHeader = chunkMetadata.getOffsetOfChunkHeader();
    reader.position(offsetOfChunkHeader);
    byte marker = reader.readMarker();
    ChunkHeader chunkHeader = reader.readChunkHeader(marker);

    Statistics<? extends Serializable> chunkStatistics =
        Statistics.getStatsByType(chunkMetadata.getDataType());
    int dataSize = chunkHeader.getDataSize();

    List<PageHeader> pageHeaders = new ArrayList<>();

    List<BatchData> batchDataList = new ArrayList<>();

    if (((byte) (chunkHeader.getChunkType() & CHUNK_HEADER_MASK)) == MetaMarker.CHUNK_HEADER) {
      while (dataSize > 0) {
        // a new Page
        PageHeader pageHeader = reader.readPageHeader(chunkHeader.getDataType(), true);
        if (pageHeader.getUncompressedSize() != 0) {
          // not empty page
          chunkStatistics.mergeStatistics(pageHeader.getStatistics());
        }
        Decoder valueDecoder =
            Decoder.getDecoderByType(chunkHeader.getEncodingType(), chunkHeader.getDataType());
        ByteBuffer pageData = reader.readPage(pageHeader, chunkHeader.getCompressionType());
        Decoder timeDecoder =
            Decoder.getDecoderByType(
                TSEncoding.valueOf(TSFileDescriptor.getInstance().getConfig().getTimeEncoder()),
                TSDataType.INT64);
        PageReader pageReader =
            new PageReader(
                pageHeader, pageData, chunkHeader.getDataType(), valueDecoder, timeDecoder, null);
        BatchData batchData = pageReader.getAllSatisfiedPageData();

        dataSize -= pageHeader.getSerializedPageSize();
        chunkHeader.increasePageNums(1);

        pageHeaders.add(pageHeader);
        batchDataList.add(batchData);
      }
    } else {
      // only one page without statistic, we need to iterate each point to generate
      // statistic
      PageHeader pageHeader = reader.readPageHeader(chunkHeader.getDataType(), false);
      Decoder valueDecoder =
          Decoder.getDecoderByType(chunkHeader.getEncodingType(), chunkHeader.getDataType());
      ByteBuffer pageData = reader.readPage(pageHeader, chunkHeader.getCompressionType());
      Decoder timeDecoder =
          Decoder.getDecoderByType(
              TSEncoding.valueOf(TSFileDescriptor.getInstance().getConfig().getTimeEncoder()),
              TSDataType.INT64);
      PageReader reader =
          new PageReader(
              pageHeader, pageData, chunkHeader.getDataType(), valueDecoder, timeDecoder, null);
      BatchData batchData = reader.getAllSatisfiedPageData();

      chunkHeader.increasePageNums(1);

      pageHeaders.add(pageHeader);
      batchDataList.add(batchData);
    }
    ChunkModel model = new ChunkModel();
    model.setChunkMetadata(chunkMetadata);
    model.setPageHeaders(pageHeaders);
    model.setBatchDataList(batchDataList);
    model.setChunk(reader.readMemChunk(chunkMetadata));
    return model;
  }

  /**
   * @param iChunkMetadata
   * @return
   * @throws IOException
   * @throws InterruptedException
   */
  public List<IPageInfo> fetchPageInfoListByIChunkMetadata(IChunkMetadata iChunkMetadata)
      throws IOException, InterruptedException {
    countDownLatch.await();
    List<IPageInfo> pageInfoList = new ArrayList<>();
    if (iChunkMetadata instanceof AlignedChunkMetadata) {
      AlignedChunkMetadata alignedChunkMetadata = (AlignedChunkMetadata) iChunkMetadata;
      IChunkMetadata timeChunkMetadata = alignedChunkMetadata.getTimeChunkMetadata();
      List<IChunkMetadata> valueChunkMetadatas = alignedChunkMetadata.getValueChunkMetadataList();
      List<IPageInfo> timePageInfoList = fetchPageInfoListByChunkMetadata(timeChunkMetadata);
      List<List<IPageInfo>> valuePageInfosList = new ArrayList<>();
      for (IChunkMetadata valueChunkMetadata : valueChunkMetadatas) {
        List<IPageInfo> valuePageInfoList = fetchPageInfoListByChunkMetadata(valueChunkMetadata);
        valuePageInfosList.add(valuePageInfoList);
      }
      // 合并timePageInfo和valuePageInfo
      for (int i = 0; i < timePageInfoList.size(); i++) {
        AlignedPageInfo alignedPageInfos = new AlignedPageInfo();
        alignedPageInfos.setTimePageInfo(timePageInfoList.get(i));
        List<IPageInfo> valuePageInfoList = new ArrayList<>();
        for (int j = 0; j < valuePageInfosList.size(); j ++) {
          valuePageInfoList.add(valuePageInfosList.get(j).get(i));
        }
        alignedPageInfos.setValuePageInfoList(valuePageInfoList);
        pageInfoList.add(alignedPageInfos);
      }

    } else {
      pageInfoList = fetchPageInfoListByChunkMetadata(iChunkMetadata);

    }
    return pageInfoList;
  }

  /**
   * 通过 ChunkMetadata 获取 Chunk 下的 pageInfoList
   *
   * @param chunkMetadata
   * @return
   * @throws IOException
   */
  public List<IPageInfo> fetchPageInfoListByChunkMetadata(IChunkMetadata chunkMetadata)
      throws IOException {

    long offsetOfChunkHeader = chunkMetadata.getOffsetOfChunkHeader();
    reader.position(offsetOfChunkHeader);
    byte marker = reader.readMarker();
    ChunkHeader chunkHeader = reader.readChunkHeader(marker);

    Statistics<? extends Serializable> chunkStatistics =
        Statistics.getStatsByType(chunkMetadata.getDataType());
    int dataSize = chunkHeader.getDataSize();

    List<IPageInfo> pageInfoList = new ArrayList<>();

    if (((byte) (chunkHeader.getChunkType() & CHUNK_HEADER_MASK)) == MetaMarker.CHUNK_HEADER) {
      logger.info(
          "read more than one page or read a page of aligned chunk, the chunkType:{}",
          chunkHeader.getChunkType());
      while (dataSize > 0) {
        PageInfo pageInfo = new PageInfo(reader.position());
        // a new Page
        PageHeader pageHeader = reader.readPageHeader(chunkHeader.getDataType(), true);
        if (pageHeader.getUncompressedSize() != 0) {
          // not empty page
          chunkStatistics.mergeStatistics(pageHeader.getStatistics());
          pageInfo.setStatistics(pageHeader.getStatistics());
        }
        Decoder valueDecoder =
            Decoder.getDecoderByType(chunkHeader.getEncodingType(), chunkHeader.getDataType());
        ByteBuffer pageData = reader.readPage(pageHeader, chunkHeader.getCompressionType());
        Decoder timeDecoder =
            Decoder.getDecoderByType(
                TSEncoding.valueOf(TSFileDescriptor.getInstance().getConfig().getTimeEncoder()),
                TSDataType.INT64);
        PageReader pageReader =
            new PageReader(
                pageHeader, pageData, chunkHeader.getDataType(), valueDecoder, timeDecoder, null);

        pageInfo.setUncompressedSize(pageHeader.getUncompressedSize());
        pageInfo.setCompressedSize(pageHeader.getCompressedSize());
        pageInfo.setDataType(chunkHeader.getDataType());
        pageInfo.setEncodingType(chunkHeader.getEncodingType());
        pageInfo.setCompressionType(chunkHeader.getCompressionType());
        pageInfo.setChunkType(chunkHeader.getChunkType());
        pageInfoList.add(pageInfo);

        dataSize -= pageHeader.getSerializedPageSize();
      }
    } else {
      logger.info("read a page of aligned chunk, the chunkType:{}", chunkHeader.getChunkType());
      PageInfo pageInfo = new PageInfo(reader.position());
      // only one page without statistic, we need to iterate each point to generate
      // statistic
      PageHeader pageHeader = reader.readPageHeader(chunkHeader.getDataType(), false);
      Decoder valueDecoder =
          Decoder.getDecoderByType(chunkHeader.getEncodingType(), chunkHeader.getDataType());
      ByteBuffer pageData = reader.readPage(pageHeader, chunkHeader.getCompressionType());
      Decoder timeDecoder =
          Decoder.getDecoderByType(
              TSEncoding.valueOf(TSFileDescriptor.getInstance().getConfig().getTimeEncoder()),
              TSDataType.INT64);
      PageReader pageReader =
          new PageReader(
              pageHeader, pageData, chunkHeader.getDataType(), valueDecoder, timeDecoder, null);

      pageInfo.setUncompressedSize(pageHeader.getUncompressedSize());
      pageInfo.setCompressedSize(pageHeader.getCompressedSize());
      pageInfo.setDataType(chunkHeader.getDataType());
      pageInfo.setEncodingType(chunkHeader.getEncodingType());
      pageInfo.setCompressionType(chunkHeader.getCompressionType());
      pageInfo.setChunkType(chunkHeader.getChunkType());
      pageInfoList.add(pageInfo);
    }

    return pageInfoList;
  }


  /**
   * 获取页数据点迭代器
   *
   * @param pageInfo
   * @return
   * @throws IOException
   */
  public BatchData fetchBatchDataByPageInfo(IPageInfo pageInfo) throws IOException {

    BatchData batchData;
    Decoder timeDecoder =
        Decoder.getDecoderByType(
            TSEncoding.valueOf(TSFileDescriptor.getInstance().getConfig().getTimeEncoder()),
            TSDataType.INT64);
    // 非对齐时间序列
    if (pageInfo instanceof PageInfo) {

      PageHeader pageHeader = fetchPageHeader(pageInfo);
      Decoder valueDecoder =
          Decoder.getDecoderByType(pageInfo.getEncodingType(), pageInfo.getDataType());
      ByteBuffer pageData = reader.readPage(pageHeader, pageInfo.getCompressionType());

      PageReader pageReader =
          new PageReader(
              pageHeader, pageData, pageInfo.getDataType(), valueDecoder, timeDecoder, null);

      batchData = pageReader.getAllSatisfiedPageData();
    }
    // 对齐时间序列
    else if (pageInfo instanceof AlignedPageInfo) {
      IPageInfo timePageInfo = ((AlignedPageInfo) pageInfo).getTimePageInfo();
      PageHeader timePageHeader = fetchPageHeader(timePageInfo);
      ByteBuffer timeByteBuffer =
          reader.readPage(timePageHeader, timePageInfo.getCompressionType());
      // done
      List<PageHeader> valuePageHeaders = new ArrayList<>();
      List<ByteBuffer> valueByteBuffers = new ArrayList<>();
      List<TSDataType> valueTSDataTypes = new ArrayList<>();
      List<Decoder> valueDecoders = new ArrayList<>();
      List<IPageInfo> valuePageInfoList = ((AlignedPageInfo)pageInfo).getValuePageInfoList();
      for (IPageInfo valuePageInfo: valuePageInfoList) {
        PageHeader valuePageHeader = fetchPageHeader(valuePageInfo);
        valuePageHeaders.add(valuePageHeader);
        valueByteBuffers.add(reader.readPage(valuePageHeader, valuePageInfo.getCompressionType()));
        valueTSDataTypes.add(valuePageInfo.getDataType());
        valueDecoders.add(
            Decoder.getDecoderByType(valuePageInfo.getEncodingType(), valuePageInfo.getDataType()));
      }

      AlignedPageReader alignedPageReader =
          new AlignedPageReader(
              timePageHeader,
              timeByteBuffer,
              timeDecoder,
              valuePageHeaders,
              valueByteBuffers,
              valueTSDataTypes,
              valueDecoders,
              null);

      batchData = alignedPageReader.getAllSatisfiedPageData();
    } else {
      batchData = null;
    }
    return batchData;
  }

  private PageHeader fetchPageHeader(IPageInfo pageInfo) throws IOException {
    // [uncompressedSize:int][compressedSize:int][statistics?][batchData]
    reader.position(pageInfo.getPosition());
    PageHeader pageHeader;
    if (pageInfo.getStatistics() != null) {
      pageHeader = reader.readPageHeader(pageInfo.getDataType(), true);
    } else {
      pageHeader = reader.readPageHeader(pageInfo.getDataType(), false);
    }
    return pageHeader;
  }

  /**
   * 根据条件查询tsfile，返回查询结果
   *
   * @param startTimestamp
   * @param endTimestamp
   * @param device
   * @param measurement
   * @param value
   * @param offset
   * @param limit
   * @return QueryDataSet
   * @throws IOException
   */
  public QueryDataSet queryResult(
      long startTimestamp,
      long endTimestamp,
      String device,
      String measurement,
      String value,
      int offset,
      int limit)
      throws IOException, InterruptedException {
    countDownLatch.await();
    if (device == "" || device == null || measurement == "" || measurement == null) {
      logger.warn(
          "device or measurement is empty, please check. device:[{}], measurement:[{}]",
          device,
          measurement);
      return null;
    }
    TsFileReader tsFileReader = new TsFileReader(reader);
    Path path = new Path(device, measurement);
    List<Path> paths = new ArrayList<>();
    paths.add(path);
    IExpression timeFilter;
    if (startTimestamp > 0 && endTimestamp > 0) {
      timeFilter =
          BinaryExpression.and(
              new GlobalTimeExpression(TimeFilter.gt(startTimestamp)),
              new GlobalTimeExpression(TimeFilter.ltEq(endTimestamp)));
    } else if (endTimestamp > 0) {
      timeFilter = new GlobalTimeExpression(TimeFilter.ltEq(endTimestamp));
    } else {
      timeFilter = new GlobalTimeExpression(TimeFilter.gt(startTimestamp));
    }

    QueryExpression queryExpression;
    if (!(value == "" || value == null)) {
      IExpression valueFilter = new SingleSeriesExpression(path, ValueFilter.eq(value));
      IExpression finalExpression = BinaryExpression.and(timeFilter, valueFilter);
      queryExpression = QueryExpression.create(paths, finalExpression);
    } else {
      queryExpression = QueryExpression.create(paths, timeFilter);
    }
    QueryDataSet result = tsFileReader.query(queryExpression);
    if (limit > 0) {
      result.setRowLimit(limit);
    }
    if (offset > 0) {
      result.setRowOffset(offset);
    }
    logger.info("QueryExpression is: {}", queryExpression);
    return result;
  }

  public long getFileSize() {
    return fileSize;
  }

  public boolean isSeq() {
    return seq;
  }

  public String getVersion() {
    return version;
  }

  public String getFilePath() {
    return filePath;
  }

  public long getAllCount() {
    return allCount;
  }

  /**
   * 获取timeSeriesMetadataNode
   *
   * @return TimeSeriesMetadataNode
   */
  public TimeSeriesMetadataNode getTimeSeriesMetadataNode() throws InterruptedException {
    countDownLatch.await();
    return timeSeriesMetadataNode;
  }

  public TsFileSequenceReader getReader() {
    return reader;
  }

  public double getRateOfProcess() {
    return rateOfProcess;
  }

  public List<ChunkGroupMetadataModel> getChunkGroupMetadataModelList() {
    return chunkGroupMetadataModelList;
  }

  private class TsFileAnalysedToolReader extends TsFileSequenceReader {
    public TsFileAnalysedToolReader(String file) throws IOException {
      super(file);
    }

    /**
     * Traverse the metadata index from MetadataIndexEntry to get TimeseriesMetadatas
     *
     * @param metadataIndex MetadataIndexEntry
     * @param buffer byte buffer
     * @param deviceId String
     * @param timeseriesMetadataMap map: deviceId -> timeseriesMetadata list
     * @param tsNode TimeSeriesMetadataNode
     * @param needChunkMetadata deserialize chunk metadata list or not
     */
    private void generateMetadataIndexWithOffset(
        long startOffset,
        MetadataIndexEntry metadataIndex,
        ByteBuffer buffer,
        String deviceId,
        MetadataIndexNodeType type,
        Map<Long, Pair<Path, TimeseriesMetadata>> timeseriesMetadataMap,
        TimeSeriesMetadataNode tsNode,
        boolean needChunkMetadata)
        throws IOException {
      try {
        if (type.equals(MetadataIndexNodeType.LEAF_MEASUREMENT)) {
          TimeseriesMetadata alignedTime = null;
          List<TimeseriesMetadata> alignedValues = new ArrayList<>();
          AlignedTimeSeriesMetadata alignedTimeSeriesMetadata = null;
          boolean aligned = true;
          while (buffer.hasRemaining()) {
            long pos = startOffset + buffer.position();
            TimeseriesMetadata timeseriesMetadata =
                TimeseriesMetadata.deserializeFrom(buffer, needChunkMetadata);
            // 判断是否对其时间序列
            if ((timeseriesMetadata.getTimeSeriesMetadataType() & TsFileConstant.TIME_COLUMN_MASK)
                == TsFileConstant.TIME_COLUMN_MASK) {
              if (aligned && alignedTime != null && alignedValues.size() > 0) {
                leafNodeWithAligned(
                    alignedTime,
                    alignedValues,
                    startOffset + buffer.position(),
                    deviceId,
                    tsNode,
                    type);
              }
              aligned = true;
              alignedTime = new TimeseriesMetadata(timeseriesMetadata);
            } else if ((timeseriesMetadata.getTimeSeriesMetadataType()
                    & TsFileConstant.VALUE_COLUMN_MASK)
                == TsFileConstant.VALUE_COLUMN_MASK) {
              alignedValues.add(timeseriesMetadata);
            } else {

              if (aligned && alignedTime != null && alignedValues.size() > 0) {
                leafNodeWithAligned(
                    alignedTime,
                    alignedValues,
                    startOffset + buffer.position(),
                    deviceId,
                    tsNode,
                    type);
              }

              timeseriesMetadataMap.put(
                  pos,
                  new Pair<>(
                      new Path(deviceId, timeseriesMetadata.getMeasurementId()),
                      timeseriesMetadata));
              TimeSeriesMetadataNode leafNode = new TimeSeriesMetadataNode();
              leafNode.setChildren(new ArrayList<>());
              leafNode.setPosition(pos);
              leafNode.setDeviceId(deviceId);
              leafNode.setMeasurementId(timeseriesMetadata.getMeasurementId());
              leafNode.setTimeseriesMetadata(timeseriesMetadata);
              allCount += timeseriesMetadata.getStatistics().getCount();
              leafNode.setNodeType(type);
              tsNode.getChildren().add(leafNode);
              aligned = false;
            }
          }
          if (aligned && alignedTime != null && alignedValues.size() > 0) {
            leafNodeWithAligned(
                alignedTime,
                alignedValues,
                startOffset + buffer.position(),
                deviceId,
                tsNode,
                type);
          }

          tsNode.setNodeType(type);
          tsNode.setPosition(startOffset + buffer.position());
          tsNode.setDeviceId(deviceId);
        } else {
          // deviceId should be determined by LEAF_DEVICE node
          if (type.equals(MetadataIndexNodeType.LEAF_DEVICE)) {
            deviceId = metadataIndex.getName();
          }
          MetadataIndexNode metadataIndexNode = MetadataIndexNode.deserializeFrom(buffer);
          int metadataIndexListSize = metadataIndexNode.getChildren().size();
          for (int i = 0; i < metadataIndexListSize; i++) {
            long endOffset = metadataIndexNode.getEndOffset();
            if (i != metadataIndexListSize - 1) {
              endOffset = metadataIndexNode.getChildren().get(i + 1).getOffset();
            }
            ByteBuffer nextBuffer =
                readData(metadataIndexNode.getChildren().get(i).getOffset(), endOffset);
            TimeSeriesMetadataNode tsChildNode = new TimeSeriesMetadataNode();
            generateMetadataIndexWithOffset(
                metadataIndexNode.getChildren().get(i).getOffset(),
                metadataIndexNode.getChildren().get(i),
                nextBuffer,
                deviceId,
                metadataIndexNode.getNodeType(),
                timeseriesMetadataMap,
                tsChildNode,
                needChunkMetadata);
            tsNode.getChildren().add(tsChildNode);
          }
          tsNode.setDeviceId(deviceId);
          tsNode.setPosition(startOffset + buffer.position());
          tsNode.setNodeType(type);
        }
      } catch (BufferOverflowException e) {
        logger.error("Unrecognized metadataIndexNode type, type:{}", type);
        throw e;
      }
    }

    private void leafNodeWithAligned(
        TimeseriesMetadata alignedTime,
        List<TimeseriesMetadata> alignedValues,
        long pos,
        String deviceId,
        TimeSeriesMetadataNode tsNode,
        MetadataIndexNodeType type) {
      if (alignedTime != null && alignedValues.size() > 0) {
        AlignedTimeSeriesMetadata alignedTimeSeriesMetadata =
            new AlignedTimeSeriesMetadata(alignedTime, new ArrayList<>(alignedValues));

        TimeSeriesMetadataNode leafNode = new TimeSeriesMetadataNode();
        leafNode.setChildren(new ArrayList<>());
        leafNode.setPosition(pos);
        leafNode.setDeviceId(deviceId);
        leafNode.setMeasurementId(alignedValues.get(0).getMeasurementId());
        leafNode.setTimeseriesMetadata(alignedTimeSeriesMetadata);
        leafNode.setAligned(true);
        for (TimeseriesMetadata value : alignedValues) {
          if (value != null && value.getStatistics() != null) {
            allCount += value.getStatistics().getCount();
          }
        }

        leafNode.setNodeType(type);
        tsNode.getChildren().add(leafNode);
        alignedValues.clear();
      }
    }

    public TimeSeriesMetadataNode getAllTimeseriesMetadataWithOffset() throws IOException {
      if (tsFileMetaData == null) {
        readFileMetadata();
        logger.info("Start reading TsFileMetadata, preparing to deserialized index.");
      }
      MetadataIndexNode metadataIndexNode = tsFileMetaData.getMetadataIndex();
      Map<Long, Pair<Path, TimeseriesMetadata>> timeseriesMetadataMap = new TreeMap<>();
      TimeSeriesMetadataNode node = new TimeSeriesMetadataNode();
      List<MetadataIndexEntry> metadataIndexEntryList = metadataIndexNode.getChildren();
      for (int i = 0; i < metadataIndexEntryList.size(); i++) {
        rateOfProcess =
            loadOfPercent
                + parseOfPercent
                + (i + 1) / metadataIndexEntryList.size() * indexOfPercent;
        TimeSeriesMetadataNode entry = new TimeSeriesMetadataNode();
        MetadataIndexEntry metadataIndexEntry = metadataIndexEntryList.get(i);
        long endOffset = tsFileMetaData.getMetadataIndex().getEndOffset();
        if (i != metadataIndexEntryList.size() - 1) {
          endOffset = metadataIndexEntryList.get(i + 1).getOffset();
        }
        ByteBuffer buffer = readData(metadataIndexEntry.getOffset(), endOffset);
        generateMetadataIndexWithOffset(
            metadataIndexEntry.getOffset(),
            metadataIndexEntry,
            buffer,
            null,
            metadataIndexNode.getNodeType(),
            timeseriesMetadataMap,
            entry,
            true);
        node.getChildren().add(entry);
      }
      node.setNodeType(metadataIndexNode.getNodeType());

      return node;
    }

    public ByteBuffer readDataFromReader(long start, int totalSize) throws IOException {
      return readData(start, totalSize);
    }
  }
}
