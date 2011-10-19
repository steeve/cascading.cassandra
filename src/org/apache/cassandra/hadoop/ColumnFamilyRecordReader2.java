package org.apache.cassandra.hadoop;

import org.apache.cassandra.db.IColumn;
import org.apache.hadoop.mapred.RecordReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.*;
import java.util.SortedMap;
import java.util.TreeMap;

public class ColumnFamilyRecordReader2 extends ColumnFamilyRecordReader implements RecordReader<ByteBuffer, SortedMap<ByteBuffer, IColumn>>
{
    private static final Logger LOG = LoggerFactory.getLogger(ColumnFamilyRecordReader2.class);

    private int keyBufferSize;
    private long currentPosition = 0;

    public ColumnFamilyRecordReader2(int keyBufferSize)
    {
        super();
        this.keyBufferSize = keyBufferSize;
    }

    // Because the old Hadoop API wants us to write to the key and value
    // and the new asks for them, we need to copy the output of the new API
    // to the old. Thus, expect a small performance hit.
    // And obviously this wouldn't work for wide rows. But since ColumnFamilyInputFormat
    // and ColumnFamilyRecordReader don't support them, it should be fine for now.
    public boolean next(ByteBuffer key, SortedMap<ByteBuffer, IColumn> value) throws IOException
    {
        if (super.nextKeyValue())
        {
            currentPosition += 1;

            key.clear();
            key.put(super.getCurrentKey());
            key.rewind();

            value.clear();
            value.putAll(super.getCurrentValue());

            return true;
        }
        return false;
    }

    public ByteBuffer createKey()
    {
        return ByteBuffer.wrap(new byte[keyBufferSize]);
    }

    public SortedMap<ByteBuffer, IColumn> createValue()
    {
        return new TreeMap<ByteBuffer, IColumn>();
    }

    public long getPos() throws IOException
    {
        return currentPosition;
    }
}
