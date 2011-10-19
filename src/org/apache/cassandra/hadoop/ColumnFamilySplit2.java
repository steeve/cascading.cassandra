package org.apache.cassandra.hadoop;

import org.apache.hadoop.mapred.InputSplit;

import java.io.DataInput;
import java.io.IOException;

public class ColumnFamilySplit2 extends ColumnFamilySplit implements InputSplit
{
    protected ColumnFamilySplit2()
    {
        super();
    }

    public ColumnFamilySplit2(String startToken, String endToken, String[] dataNodes)
    {
        super(startToken, endToken, dataNodes);
    }

    public ColumnFamilySplit2(ColumnFamilySplit split)
    {
        super(split.getStartToken(), split.getEndToken(), split.getLocations());
    }

    public static ColumnFamilySplit2 read(DataInput in) throws IOException
    {
        return new ColumnFamilySplit2(ColumnFamilySplit.read(in));
    }
}
