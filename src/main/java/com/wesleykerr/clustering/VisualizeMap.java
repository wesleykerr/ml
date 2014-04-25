package com.wesleykerr.clustering;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class VisualizeMap {
	public static int K = 5;
	
	public class Item { 
		private String itemId;
		private double value;
		
		public Item(String itemId, double value) { 
			this.itemId = itemId;
			this.value = value;
		}
		
		public String getItemId() { 
			return itemId;
		}
		
		public double getValue() { 
			return value;
		}
	}
	
	/**
	 * 
	 * @param items
	 * @param values
	 * @param keep
	 * @param k
	 * @return
	 */
	public List<Item> processLine(
			String item, List<String> items, 
			List<Double> values, Set<String> keep,
			int k)  { 		
		Preconditions.checkArgument(items.size() == values.size());

		List<Item> itemObjs = Lists.newArrayList();
		for (int i = 0; i < items.size(); ++i) { 
			String itemObj = items.get(i);
			if (itemObj.equals(item) || !keep.contains(itemObj))
				continue;
			
			if (values.get(i) > 0) { 
				// convert the similarity score into a distance.
				double value = 1 / (1.0 + values.get(i));
				itemObjs.add(new Item(itemObj, value));
			}
		}

		Collections.sort(itemObjs, new Comparator<Item>() {
			@Override
			public int compare(Item item1, Item item2) {
				int compareValue = Double.compare(item1.getValue(), item2.getValue());
				if (compareValue == 0) 
					return item1.getItemId().compareTo(item2.getItemId());
				return compareValue;
			} 
		});
		
		if (itemObjs.isEmpty())
			return Lists.newArrayList();
		
		double value = itemObjs.get(itemObjs.size()-1).value;
		if (k < itemObjs.size())
			value = itemObjs.get(k-1).value;

		List<Item> copy = Lists.newArrayList();
		for (Item itemObj : itemObjs) 
			if (itemObj.value <= value)
				copy.add(itemObj);

		return copy;
	}
	
	/**
	 * 
	 * @param line
	 * @throws Exception
	 */
	public void writeNode(String line, Writer out) throws Exception { 
		String[] tokens = line.split("\t");
		out.write(" \"");
		out.write(tokens[0]);
		out.write("\" [label=\"");
		out.write(tokens[1]);
		out.write("\"]; \n");
	}
	
	/**
	 * 
	 * @param itemId
	 * @param itemObjs
	 * @param out
	 */
	public void writeEdges(String itemId, List<Item> itemObjs, Writer out) 
			throws Exception { 
		for (Item itemObj : itemObjs) { 
			out.write(" \"" + itemId + "\" -- \"" + itemObj.itemId + "\"");
			out.write(" [len=\"" + itemObj.value + "\"]; ");
			out.write("\n");
		}
	}
	
	/**
	 * 
	 * @param inputFile
	 * @param mappingFile
	 * @throws Exception
	 */
	public void run(File inputFile, File mappingFile, File outputFile) 
			throws Exception { 
		
		try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFile))) { 
			Set<String> keep = Sets.newHashSet();
			out.write("graph { \n");
			try (BufferedReader in = new BufferedReader(new FileReader(mappingFile))) { 
				while (in.ready()) { 
					String line = in.readLine();
					String[] tokens = line.split("\t");
					keep.add(tokens[0]);
					writeNode(line, out);
				}
			}

			try (BufferedReader in = new BufferedReader(new FileReader(inputFile))) { 
				String[] items = in.readLine().split(",");
				List<String> itemIds = Lists.newArrayList();
				for (int i = 1; i < items.length; ++i) 
					itemIds.add(items[i]);

				while (in.ready()) { 
					String[] tokens = in.readLine().split(",");
					String itemId = tokens[0];
					if (keep.contains(itemId)) {
						List<Double> values = Lists.newArrayList();
						for (int i = 1; i < tokens.length; ++i) 
							values.add(Double.parseDouble(tokens[i]));
						List<Item> itemObjs = processLine(itemId, itemIds, values, keep, K);
						writeEdges(itemId, itemObjs, out);
					}
				}
			}

			out.write("} \n");
		}
		
	}

	public static void main(String[] args) throws Exception { 
		File inputFile = new File("/Users/wkerr/data-analysis/2014-04-11/model.csv");
		File mappingFile = new File("/Users/wkerr/data-analysis/2014-04-11/mapping.tsv");
		File outputFile = new File("/Users/wkerr/data-analysis/2014-04-11/output.dot");

		VisualizeMap map = new VisualizeMap();
		map.run(inputFile, mappingFile, outputFile);
	}
}
