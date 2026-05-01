package com.adamk33n3r.runelite.watchdog.nodegraph;

import com.adamk33n3r.nodegraph.Graph;
import com.adamk33n3r.nodegraph.nodes.utility.DisplayNode;
import com.adamk33n3r.runelite.watchdog.serialization.WatchdogGsonFactory;

import com.google.gson.Gson;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DisplayNodeTest {

    private Gson gson;

    @Before
    public void setup() {
        this.gson = new WatchdogGsonFactory().create(RuneLiteAPI.GSON);
    }

    @Test
    public void displayNode_rendersPrimitive_asToString() {
        DisplayNode node = new DisplayNode();
        node.getValue().setValue(42);
        assertEquals("42", node.getStringRepresentation());
    }

    @Test
    public void displayNode_rendersArray_asBracketedList() {
        DisplayNode node = new DisplayNode();
        node.getValue().setValue(new String[]{"a", "b"});
        String repr = node.getStringRepresentation();
        assertTrue(repr.startsWith("[") && repr.contains("2"));
    }

    @Test
    public void displayNode_rendersBoolean_asCheckmark() {
        DisplayNode node = new DisplayNode();
        node.getValue().setValue(true);
        assertEquals("✔", node.getStringRepresentation());

        node.getValue().setValue(false);
        assertEquals("✖", node.getStringRepresentation());
    }

    @Test
    public void displayNode_serializesAndDeserializes() {
        Graph graph = new Graph();
        graph.add(new DisplayNode());
        String json = gson.toJson(graph, Graph.class);
        Graph loaded = gson.fromJson(json, Graph.class);
        assertEquals(1, loaded.getNodes().size());
        assertTrue(loaded.getNodes().get(0) instanceof DisplayNode);
    }
}
