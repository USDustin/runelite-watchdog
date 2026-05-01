package com.adamk33n3r.runelite.watchdog.nodegraph;

import com.adamk33n3r.nodegraph.Graph;
import com.adamk33n3r.nodegraph.nodes.ActionNode;
import com.adamk33n3r.nodegraph.nodes.logic.BooleanGate;
import com.adamk33n3r.nodegraph.nodes.logic.Equality;
import com.adamk33n3r.nodegraph.nodes.flow.Branch;
import com.adamk33n3r.nodegraph.nodes.math.*;
import com.adamk33n3r.runelite.watchdog.notifications.Notification;
import com.adamk33n3r.runelite.watchdog.serialization.WatchdogGsonFactory;

import com.google.gson.Gson;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class NodeSerializationRoundTripTest {

    private Gson gson;

    @Before
    public void setup() {
        this.gson = new WatchdogGsonFactory().create(RuneLiteAPI.GSON);
    }

    @Test
    public void booleanGate_preservesOpAfterRoundTrip() {
        Graph graph = new Graph();
        BooleanGate gate = new BooleanGate();
        gate.getOp().setValue(BooleanGate.Op.OR);
        graph.add(gate);

        Graph loaded = roundTrip(graph);
        BooleanGate loadedGate = (BooleanGate) loaded.getNodes().get(0);
        assertEquals(BooleanGate.Op.OR, loadedGate.getOp().getValue());
        assertTrue("op must be in inputs map", loadedGate.getInputs().containsKey("Op"));
    }

    @Test
    public void equality_preservesOpAfterRoundTrip() {
        Graph graph = new Graph();
        Equality eq = new Equality();
        eq.getOp().setValue(Equality.Op.GREATER_EQUAL);
        graph.add(eq);

        Graph loaded = roundTrip(graph);
        Equality loadedEq = (Equality) loaded.getNodes().get(0);
        assertEquals(Equality.Op.GREATER_EQUAL, loadedEq.getOp().getValue());
        assertTrue("op must be in inputs map", loadedEq.getInputs().containsKey("Op"));
    }

    @Test
    public void actionNode_fireWhenAfkAndAfkSecondsBothPreserved() {
        Notification mockNotif = Mockito.mock(Notification.class);
        ActionNode node = new ActionNode(mockNotif);
        // Both "Fire When AFK" and "Fire When AFK Seconds" must be distinct keys
        assertTrue("Fire When AFK must be registered", node.getInputs().containsKey("Fire When AFK"));
        assertTrue("Fire When AFK Seconds must be registered", node.getInputs().containsKey("Fire When AFK Seconds"));
    }

    @Test
    public void branch_serializesAndDeserializes() {
        Graph graph = new Graph();
        graph.add(new Branch());
        Graph loaded = roundTrip(graph);
        assertTrue(loaded.getNodes().get(0) instanceof Branch);
    }

    @Test
    public void subtract_serializesAndDeserializes() {
        Graph graph = new Graph();
        graph.add(new Subtract());
        Graph loaded = roundTrip(graph);
        assertTrue(loaded.getNodes().get(0) instanceof Subtract);
    }

    @Test
    public void multiply_serializesAndDeserializes() {
        Graph graph = new Graph();
        graph.add(new Multiply());
        Graph loaded = roundTrip(graph);
        assertTrue(loaded.getNodes().get(0) instanceof Multiply);
    }

    @Test
    public void divide_serializesAndDeserializes() {
        Graph graph = new Graph();
        graph.add(new Divide());
        Graph loaded = roundTrip(graph);
        assertTrue(loaded.getNodes().get(0) instanceof Divide);
    }

    @Test
    public void min_serializesAndDeserializes() {
        Graph graph = new Graph();
        graph.add(new Min());
        Graph loaded = roundTrip(graph);
        assertTrue(loaded.getNodes().get(0) instanceof Min);
    }

    @Test
    public void max_serializesAndDeserializes() {
        Graph graph = new Graph();
        graph.add(new Max());
        Graph loaded = roundTrip(graph);
        assertTrue(loaded.getNodes().get(0) instanceof Max);
    }

    @Test
    public void clamp_serializesAndDeserializes() {
        Graph graph = new Graph();
        graph.add(new Clamp());
        Graph loaded = roundTrip(graph);
        assertTrue(loaded.getNodes().get(0) instanceof Clamp);
    }

    private Graph roundTrip(Graph graph) {
        String json = gson.toJson(graph, Graph.class);
        return gson.fromJson(json, Graph.class);
    }
}
