package cascading.cassandra;

import cascading.flow.Flow;
import cascading.flow.FlowConnector;
import cascading.operation.Aggregator;
import cascading.operation.BaseOperation;
import cascading.operation.Function;
import cascading.operation.aggregator.Count;
import cascading.operation.regex.RegexGenerator;
import cascading.pipe.Each;
import cascading.pipe.Every;
import cascading.pipe.GroupBy;
import cascading.pipe.Pipe;
import cascading.scheme.Scheme;
import cascading.scheme.TextLine;
import cascading.tap.Tap;
import cascading.tuple.Fields;
import org.apache.hadoop.conf.Configured;

import java.util.Properties;

public class Main extends Configured
{
    public static abstract class AnonymousFunction extends BaseOperation implements Function
    {
    }
    
    public static void main(String[] args) throws Exception
    {
        String[] typeMapping = {
//          "key", ":key", null,
          "line", "bible", "AsciiType",
        };
        Tap source = new CassandraTap("127.0.0.1", 9160, "wordcount", "input_words",
          new Fields("line", "bible"));

        Scheme sinkScheme = new TextLine(new Fields("word", "count"));
        Tap sink = new StdoutTap();
//        Tap sink = new Hfs( sinkScheme, "/cascade", SinkMode.REPLACE );

        // the 'head' of the pipe assembly
        Pipe assembly = new Pipe("wordcount");

        // For each input Tuple
        // parse out each word into a new Tuple with the field name "word"
        // regular expressions are optional in Cascading
        String regex = "(?<!\\pL)(?=\\pL)[^ ]*(?<=\\pL)(?!\\pL)";
        Function function = new RegexGenerator(new Fields("word"), regex);
        assembly = new Each(assembly, new Fields("line"), function);

        // group the Tuple stream by the "word" value
        assembly = new GroupBy(assembly, new Fields("word"));

        // For every Tuple group
        // count the number of occurrences of "word" and store result in
        // a field named "count"
        Aggregator count = new Count(new Fields("count"));
        assembly = new Every(assembly, count);

        // initialize app properties, tell Hadoop which jar file to use
        Properties properties = new Properties();
        FlowConnector.setApplicationJarClass(properties, Main.class);

        // plan a new Flow from the assembly using the source and sink Taps
        // with the above properties
        FlowConnector flowConnector = new FlowConnector(properties);
        Flow flow = flowConnector.connect("word-count", source, sink, assembly);

        // execute the flow, block until complete
        flow.complete();
    }
}
