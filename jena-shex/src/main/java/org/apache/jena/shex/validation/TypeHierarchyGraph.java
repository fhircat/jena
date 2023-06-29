package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeDecl;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.stream.Collectors;

public class TypeHierarchyGraph {

    private final DefaultDirectedGraph<THVertex, DefaultEdge> graph;

    public TypeHierarchyGraph (DefaultDirectedGraph<Node, DefaultEdge> extendsReferencesGraph,
                               Map<Node, ShapeDecl> shapeDeclMap) {
        graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (DefaultEdge extendsRef : extendsReferencesGraph.edgeSet()) {
            Node src = extendsReferencesGraph.getEdgeSource(extendsRef);
            Node tgt = extendsReferencesGraph.getEdgeTarget(extendsRef);
            Graphs.addEdgeWithVertices(graph,
                    new THVertex(src, shapeDeclMap.get(src)), new THVertex(tgt, shapeDeclMap.get(tgt)));
        }
    }

    public List<ShapeDecl> getNonAbstractSubtypes(ShapeDecl shapeDecl) {
        return nonAbstractAncestorsMap.computeIfAbsent(shapeDecl.getLabel(),
                label -> getAncestors(new THVertex(label, shapeDecl)).stream()
                        .map(thnode -> thnode.shapeDecl)
                        .filter(sd -> !sd.isAbstract())
                        .collect(Collectors.toList()));
    }
    private final Map<Node, List<ShapeDecl>> nonAbstractAncestorsMap = new HashMap<>();

    private List<THVertex> getAncestors (THVertex vertex) {
        if (! graph.containsVertex(vertex))
            return List.of(vertex);

        List<THVertex> result = new ArrayList<>();
        Deque<THVertex> fifo = new ArrayDeque<>();

        result.add(vertex);
        fifo.addLast(vertex);
        while (! fifo.isEmpty()) {
            THVertex current = fifo.removeFirst();
            for (DefaultEdge predEdge : graph.incomingEdgesOf(current)) {
                THVertex pred = graph.getEdgeSource(predEdge);
                result.add(pred);
                fifo.addLast(pred);
            }
        }
        return result;
    }
}
