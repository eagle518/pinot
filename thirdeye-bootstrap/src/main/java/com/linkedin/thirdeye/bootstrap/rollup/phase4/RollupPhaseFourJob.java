package com.linkedin.thirdeye.bootstrap.rollup.phase4;

import static com.linkedin.thirdeye.bootstrap.rollup.phase4.RollupPhaseFourConstants.ROLLUP_PHASE4_CONFIG_PATH;
import static com.linkedin.thirdeye.bootstrap.rollup.phase4.RollupPhaseFourConstants.ROLLUP_PHASE4_INPUT_PATH;
import static com.linkedin.thirdeye.bootstrap.rollup.phase4.RollupPhaseFourConstants.ROLLUP_PHASE4_OUTPUT_PATH;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.linkedin.thirdeye.api.MetricSchema;
import com.linkedin.thirdeye.api.MetricTimeSeries;
import com.linkedin.thirdeye.api.MetricType;

/**
 * 
 * @author kgopalak
 * 
 */
public class RollupPhaseFourJob extends Configured {
  private static final Logger LOG = LoggerFactory
      .getLogger(RollupPhaseFourJob.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private String name;
  private Properties props;

  public RollupPhaseFourJob(String name, Properties props) {
    super(new Configuration());
    this.name = name;
    this.props = props;
  }

  public static class RollupPhaseThreeMapper extends
      Mapper<BytesWritable, BytesWritable, BytesWritable, BytesWritable> {
    private RollupPhaseFourConfig config;
    private List<String> dimensionNames;
    private List<String> metricNames;
    private List<MetricType> metricTypes;
    private MetricSchema metricSchema;
    private List<String> rollupOrder;
    MultipleOutputs<BytesWritable, BytesWritable> mos;
    Map<String, Integer> dimensionNameToIndexMapping;

    @Override
    public void setup(Context context) throws IOException, InterruptedException {
      LOG.info("RollupPhaseOneJob.RollupPhaseOneMapper.setup()");
      mos = new MultipleOutputs<BytesWritable, BytesWritable>(context);
      Configuration configuration = context.getConfiguration();
      FileSystem fileSystem = FileSystem.get(configuration);
      Path configPath = new Path(configuration.get(ROLLUP_PHASE4_CONFIG_PATH
          .toString()));
      try {
        config = OBJECT_MAPPER.readValue(fileSystem.open(configPath),
            RollupPhaseFourConfig.class);
        dimensionNames = config.getDimensionNames();
        dimensionNameToIndexMapping = new HashMap<String, Integer>();

        for (int i = 0; i < dimensionNames.size(); i++) {
          dimensionNameToIndexMapping.put(dimensionNames.get(i), i);
        }
        metricNames = config.getMetricNames();
        metricTypes = Lists.newArrayList();
        for (String type : config.getMetricTypes()) {
          metricTypes.add(MetricType.valueOf(type));
        }
        metricSchema = new MetricSchema(config.getMetricNames(), metricTypes);
        rollupOrder = config.getRollupOrder();
      } catch (Exception e) {
        throw new IOException(e);
      }
    }

    @Override
    public void map(BytesWritable rawDimensionKeyWritable,
        BytesWritable rollupReduceOutputWritable, Context context)
        throws IOException, InterruptedException {
      // pass through, in the reduce we gather all possible roll up for a given
      // rawDimensionKey
      context.write(rawDimensionKeyWritable, rollupReduceOutputWritable);
    }

    @Override
    public void cleanup(Context context) throws IOException,
        InterruptedException {
      mos.close();
    }

  }

  public static class RollupPhaseThreeReducer extends
      Reducer<BytesWritable, BytesWritable, BytesWritable, BytesWritable> {
    private RollupPhaseFourConfig config;
    private List<String> dimensionNames;
    private List<String> metricNames;
    private List<MetricType> metricTypes;
    private MetricSchema metricSchema;

    @Override
    public void setup(Context context) throws IOException, InterruptedException {
      Configuration configuration = context.getConfiguration();
      FileSystem fileSystem = FileSystem.get(configuration);
      Path configPath = new Path(configuration.get(ROLLUP_PHASE4_CONFIG_PATH
          .toString()));
      try {
        config = OBJECT_MAPPER.readValue(fileSystem.open(configPath),
            RollupPhaseFourConfig.class);
        dimensionNames = config.getDimensionNames();
        metricNames = config.getMetricNames();
        metricTypes = Lists.newArrayList();
        for (String type : config.getMetricTypes()) {
          metricTypes.add(MetricType.valueOf(type));
        }
        metricSchema = new MetricSchema(config.getMetricNames(), metricTypes);
      } catch (Exception e) {
        throw new IOException(e);
      }
    }

    @Override
    public void reduce(BytesWritable rawDimensionKeyWritable,
        Iterable<BytesWritable> rollupMetricTimeSeriesWritable, Context context)
        throws IOException, InterruptedException {
      MetricTimeSeries aggMetricTimeSeries = new MetricTimeSeries(metricSchema);
      for (BytesWritable writable : rollupMetricTimeSeriesWritable) {
        MetricTimeSeries timeSeries;
        timeSeries = MetricTimeSeries.fromBytes(writable.copyBytes(),
            metricSchema);
        aggMetricTimeSeries.aggregate(timeSeries);
      }
      BytesWritable aggMetricTimeSeriesWritable = new BytesWritable(
          aggMetricTimeSeries.toBytes());
      context.write(rawDimensionKeyWritable, aggMetricTimeSeriesWritable);

    }
  }

  public void run() throws Exception {
    Job job = Job.getInstance(getConf());
    job.setJobName(name);
    job.setJarByClass(RollupPhaseFourJob.class);

    // Map config
    job.setMapperClass(RollupPhaseThreeMapper.class);
    job.setInputFormatClass(SequenceFileInputFormat.class);
    job.setMapOutputKeyClass(BytesWritable.class);
    job.setMapOutputValueClass(BytesWritable.class);

    // Reduce config
    job.setCombinerClass(RollupPhaseThreeReducer.class);
    job.setReducerClass(RollupPhaseThreeReducer.class);
    job.setOutputKeyClass(BytesWritable.class);
    job.setOutputValueClass(BytesWritable.class);
    job.setOutputFormatClass(SequenceFileOutputFormat.class);
    // rollup phase 2 config
    Configuration configuration = job.getConfiguration();
    String inputPathDir = getAndSetConfiguration(configuration,
        ROLLUP_PHASE4_INPUT_PATH);
    getAndSetConfiguration(configuration, ROLLUP_PHASE4_CONFIG_PATH);
    getAndSetConfiguration(configuration, ROLLUP_PHASE4_OUTPUT_PATH);
    LOG.info("Input path dir: " + inputPathDir);
    for (String inputPath : inputPathDir.split(",")) {
      LOG.info("Adding input:" + inputPath);
      Path input = new Path(inputPath);
      FileInputFormat.addInputPath(job, input);
    }

    FileOutputFormat.setOutputPath(job, new Path(
        getAndCheck(ROLLUP_PHASE4_OUTPUT_PATH.toString())));

    job.waitForCompletion(true);
  }

  private String getAndSetConfiguration(Configuration configuration,
      RollupPhaseFourConstants constant) {
    String value = getAndCheck(constant.toString());
    configuration.set(constant.toString(), value);
    return value;
  }

  private String getAndCheck(String propName) {
    String propValue = props.getProperty(propName);
    if (propValue == null) {
      throw new IllegalArgumentException(propName + " required property");
    }
    return propValue;
  }
}
