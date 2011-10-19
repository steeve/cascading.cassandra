This is an effort to implement a Cassandra Tap for Cascading.

At the moment, only Source is supported, although the tap does automatic
type detection from the CF metadata.

Here's the cascading Wordcount example running on Cassandra:

        Tap source = new CassandraTap("127.0.0.1", 9160, "wordcount", "input_words",
          new Fields("line", "bible"));

        Scheme sinkScheme = new TextLine(new Fields("word", "count"));
        Tap sink = new StdoutTap();

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
