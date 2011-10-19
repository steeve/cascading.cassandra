package cascading.cassandra;

import cascading.tap.Tap;
import cascading.tap.TapException;
import cascading.tap.hadoop.TapCollector;
import cascading.tap.hadoop.TapIterator;
import cascading.tuple.Fields;
import cascading.tuple.TupleEntryCollector;
import cascading.tuple.TupleEntryIterator;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.JobConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class CassandraTap extends Tap
{
    private static final Logger LOG = LoggerFactory.getLogger(CassandraTap.class);

    public static final String SCHEME = "cassandra";

    private String tableName;

    public CassandraTap(String host, int port, String keyspace, String columnFamily, Fields... fields)
    {
        super(new CassandraScheme(host, port, keyspace, columnFamily, fields));
        this.tableName = "bible";
    }

    private URI getURI()
    {
        LOG.info("getURI");

        try
        {
            return new URI("cassandra", "localhost", "/wordcount/input_words");
        }
        catch (URISyntaxException exception)
        {
            throw new TapException("unable to create uri", exception);
        }
     }

    public Path getPath()
    {
        LOG.info("COUCOU ???");
        return new Path(getURI().toString());
    }

    public TupleEntryIterator openForRead(JobConf conf) throws IOException
    {
        LOG.info("openForRead");
        return new TupleEntryIterator(getSourceFields(), new TapIterator(this, conf));
    }

    public boolean makeDirs( JobConf conf ) throws IOException
    {
        LOG.info("makeDirs");

        return true;
    }

    public boolean deletePath( JobConf conf ) throws IOException
    {
        LOG.info("deletePath");
        return true;
    }
    
    public boolean pathExists( JobConf conf ) throws IOException
    {
        LOG.info("pathExists");
        return true;
    }

    public long getPathModified( JobConf conf ) throws IOException
    {
        return System.currentTimeMillis(); // currently unable to find last mod time on a table
    }

    @Override
    public boolean isSource()
    {
        LOG.info("isSource");
        return true;
    }

    @Override
    public boolean isSink()
    {
        return false;
    }

    @Override
    public void sourceInit(JobConf conf) throws IOException
    {
//        LOG.debug("sourcing from table: {}", tableName);
        FileInputFormat.addInputPaths(conf, tableName);
        super.sourceInit(conf);
    }

    public TupleEntryCollector openForWrite(JobConf conf) throws IOException
    {
        LOG.info("openForWrite");
        return new TapCollector(this, conf);
    }

}
