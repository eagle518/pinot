package com.linkedin.thirdeye.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.Reader;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.linkedin.thirdeye.api.MetricSchema;
import com.linkedin.thirdeye.api.MetricTimeSeries;
import com.linkedin.thirdeye.api.MetricType;
import com.linkedin.thirdeye.bootstrap.startree.bootstrap.phase1.BootstrapPhaseMapOutputKey;
import com.linkedin.thirdeye.bootstrap.startree.bootstrap.phase1.BootstrapPhaseMapOutputValue;
import com.linkedin.thirdeye.bootstrap.startree.bootstrap.phase1.StarTreeBootstrapPhaseOneConfig;

/**
 * Utility to read the output of Bootstrap Phase One
 * 
 * @author kgopalak
 * 
 */
public class BootstrapPhaseOneOutputDumpTool {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final StarTreeBootstrapPhaseOneConfig config;

  public BootstrapPhaseOneOutputDumpTool(StarTreeBootstrapPhaseOneConfig config) {
    this.config = config;
  }

  public static void main(String[] args) throws IOException,
      InstantiationException, IllegalAccessException {
    // data output directory generated by BootstrapPhaseOneJob
    Path dataPath = new Path(args[0]);
    // config file used by BootstrapPhaseOneJob
    Path configPath = new Path(args[1]);
    FileSystem fs = FileSystem.get(new Configuration());

    // read the config file
    FSDataInputStream is = fs.open(configPath);
    StarTreeBootstrapPhaseOneConfig config;
    config = OBJECT_MAPPER.readValue(is, StarTreeBootstrapPhaseOneConfig.class);
    // instantiate BootstrapPhaseOneOutputDumpTool instance
    BootstrapPhaseOneOutputDumpTool tool;
    tool = new BootstrapPhaseOneOutputDumpTool(config);
    if (fs.isFile(dataPath)) {
      tool.process(dataPath);
    } else {
      FileStatus[] listStatus = fs.listStatus(dataPath);
      for (FileStatus fileStatus : listStatus) {
        tool.process(fileStatus.getPath());
      }
    }
  }

  public void process(Path path) throws IOException, InstantiationException,
      IllegalAccessException {
    SequenceFile.Reader reader = new SequenceFile.Reader(new Configuration(),
        Reader.file(path));
    System.out.println(reader.getKeyClass());
    System.out.println(reader.getValueClassName());
    WritableComparable<?> key = (WritableComparable<?>) reader.getKeyClass()
        .newInstance();
    Writable val = (Writable) reader.getValueClass().newInstance();
    ArrayList<String> names = Lists.newArrayList("m1", "m2", "m3", "m4", "m5");
    ArrayList<MetricType> types = Lists.newArrayList(MetricType.INT,
        MetricType.INT, MetricType.INT, MetricType.INT, MetricType.INT);

    MetricSchema schema = new MetricSchema(names, types);
    int[] metrics = new int[names.size()];
    while (reader.next(key, val)) {
      BytesWritable keyWritable = (BytesWritable) key;
      BootstrapPhaseMapOutputKey outputKey = BootstrapPhaseMapOutputKey
          .fromBytes(keyWritable.getBytes());
      BytesWritable valWritable = (BytesWritable) val;
      BootstrapPhaseMapOutputValue outputVal = BootstrapPhaseMapOutputValue
          .fromBytes(valWritable.getBytes(), schema);

      System.out.println(outputVal.getDimensionKey());
      MetricTimeSeries metricTimeSeries = outputVal.getMetricTimeSeries();
      for (long timeWindow : metricTimeSeries.getTimeWindowSet()) {
        boolean nonZeroMetric = false;
        for (int i = 0; i < names.size(); i++) {
          String name = names.get(i);
          metrics[i] = metricTimeSeries.get(timeWindow, name).intValue();
          if (metrics[i] > 0) {
            nonZeroMetric = true;
          }
        }
        // print only if any of the metric is non zero
        if (nonZeroMetric) {
          System.out.println(timeWindow + ":" + Arrays.toString(metrics));
        }
      }
    }
  }
}
