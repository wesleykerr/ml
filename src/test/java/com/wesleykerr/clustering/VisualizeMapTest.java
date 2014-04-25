package com.wesleykerr.clustering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.wesleykerr.clustering.VisualizeMap.Item;

public class VisualizeMapTest {
	
	List<String> items;
	List<Double> values;
	Set<String> keep;
	
	@Before
	public void setup() { 
    	items = Lists.newArrayList("a", "b", "c");
    	values = Lists.newArrayList(3.0, 3.5, 1.1);
    	keep = Sets.newHashSet("a", "b", "c");
	}
	

    @Test
    public void testProcessLine() { 
    	VisualizeMap vm = new VisualizeMap();
    	List<Item> kept = vm.processLine("d", items, values, keep, 2);

    	assertEquals("b", kept.get(0).getItemId());
    	assertEquals("a", kept.get(1).getItemId());
    	
    	assertEquals(Double.valueOf(1.0 / (1.0+3.5)), 
    			Double.valueOf(kept.get(0).getValue()));
    	assertEquals(Double.valueOf(1.0 / (1.0+3.0)), 
    			Double.valueOf(kept.get(1).getValue()));
    	
    }
    
    @Test
    public void testProcessLineError() {
    	VisualizeMap vm = new VisualizeMap();
    	
    	List<String> items = Lists.newArrayList("a", "b", "c");
    	List<Double> values = Lists.newArrayList(3.0, 3.5);
    	
    	try { 
    		vm.processLine("d", items, values, keep, 2);
    		assertTrue(false);
    	} catch (Exception e) { 
    		assertTrue(true);
    	}
    }
    
    public void testProcessLineFilter() { 
    	VisualizeMap vm = new VisualizeMap();
    	List<Item> kept = vm.processLine("d", items, values, Sets.newHashSet("a", "c"), 2);

    	assertEquals("a", kept.get(0).getItemId());
    	assertEquals("c", kept.get(1).getItemId());
    	
    	assertEquals(Double.valueOf(1.0 / (1.0+3.0)), 
    			Double.valueOf(kept.get(0).getValue()));
    	assertEquals(Double.valueOf(1.0 / (1.0+1.1)), 
    			Double.valueOf(kept.get(1).getValue()));
    }
    
    @Test
    public void writeEdgesTest() throws Exception { 
    	VisualizeMap vm = new VisualizeMap();
    	List<Item> kept = vm.processLine("d", items, values, keep, 3);
    	StringWriter out = new StringWriter();
    	vm.writeEdges("d", kept, out);

    	StringReader actual = new StringReader(out.getBuffer().toString());
    	BufferedReader in = new BufferedReader(actual);
    	
    	assertEquals(" \"d\" -- \"b\" [len=\"0.2222222222222222\"]; ", in.readLine());
    	assertEquals(" \"d\" -- \"a\" [len=\"0.25\"]; ", in.readLine());
    	assertEquals(" \"d\" -- \"c\" [len=\"0.47619047619047616\"]; ", in.readLine());
    }
    
    @Test
    public void writeNodeTest() throws Exception { 
    	VisualizeMap vm = new VisualizeMap();
    	StringWriter out = new StringWriter();
    	vm.writeNode("a\ttest", out);

    	StringReader actual = new StringReader(out.getBuffer().toString());
    	BufferedReader in = new BufferedReader(actual);
    	
    	assertEquals(" \"a\" [label=\"test\"]; ", in.readLine());
    	
    }
}
