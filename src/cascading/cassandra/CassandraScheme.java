package cascading.cassandra;

import cascading.scheme.Scheme;
import cascading.tap.Tap;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.cassandra.db.IColumn;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.TypeParser;
import org.apache.cassandra.hadoop.ColumnFamilyInputFormat2;
import org.apache.cassandra.hadoop.ConfigHelper;
import org.apache.cassandra.thrift.*;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class CassandraScheme extends Scheme
{
    private static final Logger LOG = LoggerFactory.getLogger(CassandraScheme.class);

    private boolean isFullyQualified = true;

    private String cassandraHost;
    private Integer cassandraPort;
    private String keyspace;
    private String columnFamily;

    private Fields[] fieldsMapping;

    private transient Cassandra.Client  _cassandraClient;
    private transient CfDef cfDef;
    private transient AbstractType keyType;
    private transient AbstractType defaultValidatorType;
    private transient Map<ByteBuffer, AbstractType> validatorsMap;

    public CassandraScheme(String keyspace, String columnFamily, Fields... fields)
    {
        this("127.0.0,1", keyspace, columnFamily, fields);
    }

    public CassandraScheme(String host, String keyspace, String columnFamily, Fields... fields)
    {
        this(host, 9160, keyspace, columnFamily, fields);
    }

    public CassandraScheme(String host, Integer port, String keyspace, String columnFamily, Fields... fields)
    {
        this.cassandraHost = host;
        this.cassandraPort = port;
        this.keyspace = keyspace;
        this.columnFamily = columnFamily;
        this.fieldsMapping = fields;
        this.computeSourceFields();
    }

    private void computeFieldsMappingFromCfDef(CfDef cfDef)
    {
        ArrayList<Fields> fieldsMap = new ArrayList<Fields>();

        try
        {
            for (ColumnDef cd : getCfDef().getColumn_metadata())
                if (cd.getValidation_class() != null && !cd.getValidation_class().isEmpty())
                    fieldsMap.add(new Fields(ByteBufferUtil.string(cd.name)));
            this.fieldsMapping = new Fields[fieldsMap.size()];
            fieldsMap.toArray(this.fieldsMapping);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void computeSourceFields()
    {
        Fields sourceFields = new Fields();

        if (this.fieldsMapping == null)
            this.computeFieldsMappingFromCfDef(this.getCfDef());

        for (Fields fields : this.fieldsMapping)
            sourceFields = Fields.join(sourceFields, new Fields((String)fields.get(0)));

        this.setSourceFields(sourceFields);
    }

    private Cassandra.Client cassandraClient()
    {
        try
        {
            if (this._cassandraClient == null)
                this._cassandraClient = createConnection(this.cassandraHost, this.cassandraPort, true);
            return this._cassandraClient;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Cassandra.Client createConnection(String host, Integer port, boolean framed) throws IOException
    {
        TSocket socket = new TSocket(host, port);
        TTransport trans = framed ? new TFramedTransport(socket) : socket;
        try
        {
            trans.open();
        }
        catch (TTransportException e)
        {
            throw new IOException("unable to connect to server", e);
        }
        return new Cassandra.Client(new TBinaryProtocol(trans));
    }

    private Map<ByteBuffer, AbstractType> makeValidatorMap(CfDef cfDef) throws IOException
    {
        TreeMap<ByteBuffer, AbstractType> validators = new TreeMap<ByteBuffer, AbstractType>();
        for (ColumnDef cd : getCfDef().getColumn_metadata())
        {
            if (cd.getValidation_class() != null && !cd.getValidation_class().isEmpty())
            {
                try
                {
                    validators.put(cd.name, TypeParser.parse(cd.getValidation_class()));
                }
                catch (ConfigurationException e)
                {
                    throw new IOException(e);
                }
            }
        }
        return validators;
    }

    private Map<ByteBuffer, AbstractType> getValidatorsMap()
    {
        try
        {
            if (this.validatorsMap == null)
                this.validatorsMap = this.makeValidatorMap(this.getCfDef());
            return this.validatorsMap;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private CfDef getCfDef()
    {
        if (this.cfDef == null)
        {
            try
            {
                Cassandra.Client client = this.cassandraClient();
                client.set_keyspace(this.keyspace);
                KsDef ksDef = client.describe_keyspace(this.keyspace);
                for (CfDef def : ksDef.getCf_defs())
                {
                    if (this.columnFamily.equals(def.getName()))
                    {
                        this.cfDef = def;
                        break;
                    }
                }
            }
            catch (TException e)
            {
                throw new RuntimeException(e);
            }
            catch (InvalidRequestException e)
            {
                throw new RuntimeException(e);
            }
            catch (NotFoundException e)
            {
                throw new RuntimeException(e);
            }
        }
        return this.cfDef;
    }

    private AbstractType getKeyType()
    {
        if (this.keyType == null)
        {
            try
            {
                this.keyType = TypeParser.parse(this.cfDef.getKey_validation_class());
            }
            catch (ConfigurationException e)
            {
                throw new RuntimeException(e);
            }
        }
        return this.keyType;
    }

    private AbstractType getDefaultValidatorType()
    {
        if (this.defaultValidatorType == null)
        {
            try
            {
                this.defaultValidatorType = TypeParser.parse(this.getCfDef().getDefault_validation_class());
            }
            catch (ConfigurationException e)
            {
                throw new RuntimeException(e);
            }
        }
        return this.defaultValidatorType;
    }

    private AbstractType getTypeForColumn(IColumn column)
    {
        AbstractType type = this.getValidatorsMap().get(column.name());
        if (type == null)
            type = this.getDefaultValidatorType();
        return type;
    }

    @Override
    public Tuple source(Object keyObject, Object valueObject)
    {
        Tuple result = new Tuple();
        ByteBuffer key = (ByteBuffer)keyObject;
        SortedMap<ByteBuffer, IColumn> columns = (SortedMap<ByteBuffer, IColumn>)valueObject;

        for (Fields entry : this.fieldsMapping)
        {
            String fieldName = (String)entry.get(0);
            String columnName = fieldName;
            if (entry.size() > 1)
                columnName = (String)entry.get(1);

            IColumn column = columns.get(ByteBufferUtil.bytes(columnName));
            result.add(this.getTypeForColumn(column).compose(column.value()));
        }

        return result;
    }

    @Override
    public void sink(TupleEntry tupleEntry, OutputCollector outputCollector) throws IOException
    {
        LOG.info("sink");
        throw new IOException();
    }

    @Override
    public void sinkInit(Tap tap, JobConf conf) throws IOException
    {
        LOG.info("sink");
    }

    @Override
    public void sourceInit(Tap tap, JobConf conf) throws IOException
    {
        conf.setInputFormat(ColumnFamilyInputFormat2.class);

        ConfigHelper.setInputColumnFamily(conf, this.keyspace, this.columnFamily);
        
        ConfigHelper.setInitialAddress(conf, this.cassandraHost);
        ConfigHelper.setRpcPort(conf, String.valueOf(this.cassandraPort));
        ConfigHelper.setPartitioner(conf, "org.apache.cassandra.dht.RandomPartitioner");


        if (this.fieldsMapping != null)
        {
            ArrayList<ByteBuffer> columns = new ArrayList<ByteBuffer>();

            for (Fields entry : this.fieldsMapping)
            {
                String fieldName = (String)entry.get(0);
                String columnName = fieldName;
                if (entry.size() > 1)
                    columnName = (String)entry.get(1);
                columns.add(ByteBufferUtil.bytes(columnName));
            }

            // Set the predicate that determines what columns will be selected from each row
            SlicePredicate predicate = new SlicePredicate().setColumn_names(columns);
            // The "get_slice" (see Cassandra's API) operation will be applied on each row of the ColumnFamily.
            // Each row will be handled by one Map job.
            ConfigHelper.setInputSlicePredicate(conf, predicate);
        }
    }
}
