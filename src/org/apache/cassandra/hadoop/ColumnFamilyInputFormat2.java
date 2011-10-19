package org.apache.cassandra.hadoop;

import org.apache.cassandra.db.IColumn;
import org.apache.hadoop.mapred.*;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.SortedMap;

public class ColumnFamilyInputFormat2 extends ColumnFamilyInputFormat implements InputFormat<ByteBuffer, SortedMap<ByteBuffer, IColumn>>
{
    private static final Logger LOG = LoggerFactory.getLogger(ColumnFamilyInputFormat2.class);

    public static final String MAPRED_TASK_ID = "mapred.task.id";

    // The simple fact that we need this is because the old Hadoop API wants us to "write"
    // to the key and value whereas the new asks for it.
    // I choose 8kb as the default max key size (instanciated only once), but you can
    // override it in your jobConf with this setting.
    public static final String CASSANDRA_HADOOP_MAX_KEY_SIZE = "cassandra.hadoop.max_key_size";
    public static final int    CASSANDRA_HADOOP_MAX_KEY_SIZE_DEFAULT = 8192;

    public InputSplit[] getSplits(JobConf jobConf, int numSplits) throws IOException
    {
        TaskAttemptContext tac = new TaskAttemptContext(jobConf, new TaskAttemptID());
        List<org.apache.hadoop.mapreduce.InputSplit> newInputSplits = super.getSplits(tac);
        InputSplit[] oldInputSplits = new InputSplit[newInputSplits.size()];
        for (int i = 0; i < newInputSplits.size(); i++)
            oldInputSplits[i] = new ColumnFamilySplit2((ColumnFamilySplit)newInputSplits.get(i));
        return oldInputSplits;
    }

    public RecordReader<ByteBuffer, SortedMap<ByteBuffer, IColumn>> getRecordReader(InputSplit split, JobConf jobConf, final Reporter reporter) throws IOException
    {
        TaskAttemptContext tac = new TaskAttemptContext(jobConf, TaskAttemptID.forName(jobConf.get(MAPRED_TASK_ID)))
        {
            @Override
            public void progress()
            {
                reporter.progress();
            }
        };

        ColumnFamilyRecordReader2 recordReader = new ColumnFamilyRecordReader2(jobConf.getInt(CASSANDRA_HADOOP_MAX_KEY_SIZE, CASSANDRA_HADOOP_MAX_KEY_SIZE_DEFAULT));
        recordReader.initialize((org.apache.hadoop.mapreduce.InputSplit)split, tac);
        return recordReader;
    }
}
