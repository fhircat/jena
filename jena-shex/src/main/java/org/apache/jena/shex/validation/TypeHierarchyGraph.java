package org.apache.jena.shex.validation;

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.calc.AccumulationUtil;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeHierarchyGraph {

    private DefaultDirectedGraph<THVertex, DefaultEdge> graph;

    private TypeHierarchyGraph(){}

    public static TypeHierarchyGraph create (Map<Node, ShapeDecl> shapeDeclMap) {

        TypeHierarchyGraph result = new TypeHierarchyGraph();
        result.graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        shapeDeclMap.forEach((label, decl) -> {
            result.graph.addVertex(new THVertex(decl));
            List<Shape> accShapes = new ArrayList<>();
            AccumulationUtil.accumulateShapesFollowShapeExprRefs(decl.getShapeExpr(), shapeDeclMap::get, accShapes);
            for (Shape shape : accShapes)
                for (ShapeExprRef extended : shape.getExtends())
                    result.graph.addEdge(new THVertex(decl), new THVertex(shapeDeclMap.get(extended.getLabel())));
        });
        // Remove the isolated vertices, ie those that do not participate in the type hierarchy
        List<THVertex> toBeRemoved = result.graph.vertexSet().stream()
                .filter(v -> result.graph.degreeOf(v) == 0)
                .collect(Collectors.toList());
        result.graph.removeAllVertices(toBeRemoved);
        return result;
    }

    public boolean hasCycles () {
        CycleDetector<THVertex, DefaultEdge> cycleDetector = new CycleDetector<>(graph);
        return cycleDetector.detectCycles();
    }

    /** All shape declarations that participate in the type hierarchy, i.e. extend something or are extended. */
    public Stream<ShapeDecl> extendableShapeDecls () {
        return graph.vertexSet().stream().map(v -> v.shapeDecl);
    }

    /** Duplicates-free list of the non-abstract subtypes, including the given shape declaration. */
    public List<ShapeDecl> getNonAbstractSubtypes(ShapeDecl shapeDecl) {
        return nonAbstractAncestorsMap.computeIfAbsent(shapeDecl.getLabel(),
                label -> getAncestors(new THVertex(shapeDecl)).stream()
                        .map(thVertex -> thVertex.shapeDecl)
                        .filter(sd -> !sd.isAbstract())
                        .collect(Collectors.toList()));
    }
    private final Map<Node, List<ShapeDecl>> nonAbstractAncestorsMap = new HashMap<>();


    /* Duplicates-free list of the subtypes (ie extended shape declarations), including the given shape declaration. */
    // TODO should this return the shape declarations or only the labels ?
    public List<ShapeDecl> getSupertypes(ShapeDecl shapeDecl) {
        return supertypesMap.computeIfAbsent(shapeDecl.getLabel(),
                label -> getDescendants(new THVertex(shapeDecl))
                        .stream()
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

    /** A vertex in the graph is basically a ShapeDecl.
     * We however use this internal representation in order to avoid unnecessary hashCode and equals
     * computations on graph vertices.
     * This vertex class relies on the fact that the vertices are ShapeDecl with distinct labels,
     * so hashCode is based only on labels.
     * */
    private static class THVertex {

       public final ShapeDecl shapeDecl;

        THVertex(ShapeDecl shapeDecl) {
            this.shapeDecl = shapeDecl;
        }

        @Override
        public String toString() {
            return THVertex.class.getSimpleName() + "[" + shapeDecl.getLabel() + "]";
        }

        @Override
        public int hashCode() {
            return shapeDecl.getLabel().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof THVertex) && (((THVertex)obj).shapeDecl.getLabel() == shapeDecl.getLabel());
        }
    }


}
