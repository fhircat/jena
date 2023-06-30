package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.calc.AccumulationUtil;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.function.Function;
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
                    new THVertex(src, shapeDeclMap.get(src)),
                    new THVertex(tgt, shapeDeclMap.get(tgt)));
        }
    }

    /** Duplicates-free list of the non-abstract subtypes, including the given shape declaration. */
    public List<ShapeDecl> getNonAbstractSubtypes(ShapeDecl shapeDecl) {
        return nonAbstractAncestorsMap.computeIfAbsent(shapeDecl.getLabel(),
                label -> getAncestors(new THVertex(label, shapeDecl)).stream()
                        .map(thVertex -> thVertex.shapeDecl)
                        .filter(sd -> !sd.isAbstract())
                        .collect(Collectors.toList()));
    }
    private final Map<Node, List<ShapeDecl>> nonAbstractAncestorsMap = new HashMap<>();


    /* Duplicates-free list of the subtypes (ie extended shape declarations), including the given shape declaration. */
    // TODO should this return the shape declarations or only the labels ?
    public List<ShapeDecl> getSupertypes(ShapeDecl shapeDecl) {
        return supertypesMap.computeIfAbsent(shapeDecl.getLabel(),
                label -> getDescendants(new THVertex(label, shapeDecl)).stream()
                        .map(thVertex -> thVertex.shapeDecl)
                        .collect(Collectors.toList()));
    }
    private final Map<Node, List<ShapeDecl>> supertypesMap = new HashMap<>();

    /** Returns the ancestors of a vertex, including the vertex itself. */
    private Set<THVertex> getAncestors (THVertex vertex) {
        return getClosure(vertex, graph::incomingEdgesOf, graph::getEdgeSource);
    }

    /** Returns the descendants of a vertex, including the vertex itself. */
    private Set<THVertex> getDescendants (THVertex vertex) {
        return getClosure(vertex, graph::outgoingEdgesOf, graph::getEdgeTarget);
    }

    /** Computes ancestors or descendants of a node provided the appropriate functions */
    private Set<THVertex> getClosure (THVertex vertex,
                                       Function<THVertex, Set<DefaultEdge>> adjacent,
                                       Function<DefaultEdge, THVertex> opposite) {
        if (! graph.containsVertex(vertex))
            return Set.of(vertex);

        Set<THVertex> result = new LinkedHashSet<>();
        Deque<THVertex> fifo = new ArrayDeque<>();

        result.add(vertex);
        fifo.addLast(vertex);
        while (! fifo.isEmpty()) {
            THVertex current = fifo.removeFirst();
            for (DefaultEdge adjEdge : adjacent.apply(current)) {
                THVertex other = opposite.apply(adjEdge);
                result.add(other);
                fifo.addLast(other);
            }
        }
        return result;
    }

    private static class THVertex {

        public final Node label;
        public final ShapeDecl shapeDecl;

        THVertex(Node label, ShapeDecl shapeDecl) {
            this.label = label;
            this.shapeDecl = shapeDecl;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof THVertex)) return false;

            THVertex thNode = (THVertex) o;

            return label.equals(thNode.label);
        }

        @Override
        public int hashCode() {
            return label.hashCode();
        }

        @Override
        public String toString() {
            return THVertex.class.getSimpleName() + "[" + label + "]";
        }
    }


}
