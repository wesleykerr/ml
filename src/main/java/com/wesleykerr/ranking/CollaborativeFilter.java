package com.wesleykerr.ranking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wesleykerr.ranking.utils.TableUtils;

/**
 * The assumption is that the data that goes through this training code is
 * "clean" in the sense that inactive items have been removed and only things
 * that can be recommended remain.
 * 
 * @author wkerr
 *
 */
public class CollaborativeFilter {
    private static final Logger LOGGER = Logger
            .getLogger(CollaborativeFilter.class);

    private Emitter emitter;

    private boolean rowNorm;
    private double threshold;
    private long minFreq;

    public CollaborativeFilter() {
        rowNorm = false;
        minFreq = 100;
    }

    /**
     * The reader is a connection to a list of players that we need to process
     * in order to get values for our matrix.
     * 
     * @param reader
     * @return
     * @throws Exception
     */
    public Table<Long, Long, Double> processPlayers(Reader reader)
            throws Exception {
        JsonParser parser = new JsonParser();

        Map<Long, Long> itemCounts = Maps.newHashMap();
        Table<Long, Long, Double> table = TreeBasedTable.create();
        int lineCount = 0;
        try (BufferedReader in = new BufferedReader(reader)) {
            for (String line = in.readLine(); in.ready() && line != null;
                    line = in.readLine()) {
                JsonObject obj = parser.parse(line).getAsJsonObject();

                List<Long> items = getItems(obj);
                TableUtils.mergeInto(table, emitter.emit(items));

                for (Long item : items) {
                    Long count = itemCounts.get(item);
                    if (count == null)
                        count = 0L;
                    itemCounts.put(item, count + 1);
                }

                lineCount++;
                if (lineCount % 100000 == 0)
                    LOGGER.info("processed " + lineCount + " players");
            }
        }
        LOGGER.info("procsssed " + lineCount + " players");
        return finalizeMatrix(table, itemCounts, minFreq);
    }

    /**
     * Retrieve a single player's contribution to the item-item matrix. We
     * expect a JSON object with the following template -- { "userId": "xxx",
     * ratings: [ {"item": 123, "rating": 2.3 }, ... ]
     * 
     * @param obj
     * @return
     */
    public List<Long> getItems(JsonObject obj) {
        JsonArray ratingsArray = obj.get("ratings").getAsJsonArray();
        List<Long> items = Lists.newArrayList();
        for (JsonElement node : ratingsArray) {
            JsonObject nodeObj = node.getAsJsonObject();
            if (nodeObj.get("rating").getAsDouble() >= threshold) {
                items.add(nodeObj.get("item").getAsLong());
            }
        }
        return items;
    }

    /**
     * Update the values in the matrix to represent cosine similarities instead
     * of just counts. If this collaborative filter uses row normalization,
     * perform that as well.
     * 
     * @param matrix
     * @return
     */
    public Table<Long, Long, Double> finalizeMatrix(
            Table<Long, Long, Double> table, Map<Long, Long> itemMap,
            long minFreq) {
        // At this point the matrix should be a upper diagonal matrix.
        LOGGER.info("finalizeMatrix start" + table.rowKeySet().size()
                + " rows x " + table.columnKeySet().size() + " cols");

        Table<Long, Long, Double> result = HashBasedTable.create();
        for (Table.Cell<Long, Long, Double> cell : table.cellSet()) {
            Long row = cell.getRowKey();
            Long col = cell.getColumnKey();
            if (itemMap.get(row) < minFreq || itemMap.get(col) < minFreq)
                continue;

            result.put(row, col, cell.getValue());
            if (!row.equals(col))
                result.put(col, row, cell.getValue());
        }

        result = TableUtils.computeCosine(result);
        if (rowNorm)
            result = TableUtils.rowNormalize(result);

        LOGGER.info("finalizeMatrix end " + result.rowKeySet().size()
                + " rows x " + result.columnKeySet().size() + " cols");
        return result;
    }

    /**
     * run the actual collaborative filter with the given details.
     * 
     * @param in
     * @param out
     * @param cf
     */
    public static void run(String in, String out, CollaborativeFilter cf)
            throws Exception {
        Table<Long, Long, Double> results = null;

        // Create a reader that will process the data.
        if (in.endsWith(".gz")) {
            try (InputStream inStream = new FileInputStream(in);
                    InputStream gzipInputStream = new GZIPInputStream(inStream);
                    Reader reader = new InputStreamReader(gzipInputStream,
                            "UTF-8")) {
                results = cf.processPlayers(reader);
            }
        } else {
            try (InputStream inStream = new FileInputStream(in);
                    Reader reader = new InputStreamReader(inStream, "UTF-8")) {
                results = cf.processPlayers(reader);
            }
        }

        // Create a writer that we we can write the data out to.
        if (out.endsWith(".gz")) {
            try (OutputStream outStream = new FileOutputStream(out);
                    OutputStream gzipOutStream = new GZIPOutputStream(outStream);
                    Writer write = new OutputStreamWriter(gzipOutStream,
                            "UTF-8")) {
                TableUtils.writeCSVMatrix(results, write);
            }
        } else {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(out))) {
                TableUtils.writeCSVMatrix(results, bw);
            }
        }
    }

    public static Options getOptions() {
        Options options = new Options();

        @SuppressWarnings("static-access")
        Option input = OptionBuilder.withLongOpt("input").withArgName("input")
                .hasArg().isRequired().create("i");
        options.addOption(input);

        @SuppressWarnings("static-access")
        Option output = OptionBuilder.withLongOpt("output")
                .withArgName("output").hasArg().isRequired().create("o");
        options.addOption(output);

        @SuppressWarnings("static-access")
        Option help = OptionBuilder.withLongOpt("help").withArgName("help")
                .create("h");
        options.addOption(help);

        return options;
    }

    public static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("CollaborativeFilter", options);
    }

    public static void main(String[] args) throws Exception {
        Options options = getOptions();
        CommandLineParser parser = new BasicParser();

        try {
            CommandLine line = parser.parse(options, args);
            String input = line.getOptionValue("i");
            String output = line.getOptionValue("o");

            if (line.hasOption("h")) {
                printHelp(options);
                System.exit(1);
            }

            CollaborativeFilter cf = CollaborativeFilter.Builder.create()
                    .withEmitter(Emitter.cosineWeighted)
                    .withRowNorm(false)
                    .withThreshold(0.5)
                    .build();
            CollaborativeFilter.run(input, output, cf);

        } catch (ParseException exp) {
            printHelp(options);
            System.exit(1);
        }

    }

    /**
     * 
     * @author wkerr
     *
     */
    public static class Builder {
        CollaborativeFilter cf;

        private Builder() {
            cf = new CollaborativeFilter();
        }

        public Builder withEmitter(Emitter emitter) {
            cf.emitter = emitter;
            return this;
        }

        public Builder withRowNorm(boolean rowNorm) {
            cf.rowNorm = rowNorm;
            return this;
        }

        public Builder withThreshold(double threshold) {
            cf.threshold = threshold;
            return this;
        }

        public Builder withMinFreq(long minFreq) {
            cf.minFreq = minFreq;
            return this;
        }

        public CollaborativeFilter build() {
            Preconditions.checkNotNull(cf);
            CollaborativeFilter tmp = cf;
            cf = null;
            return tmp;
        }

        public static Builder create() {
            return new Builder();
        }
    }

}
