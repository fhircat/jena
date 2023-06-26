package org.apache.jena.shex.validation;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.calc.Util;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.TripleConstraint;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TypeHierarchyGraph {

    private final ValidationContext.SorbeFactory sorbeFactory;

    private final DirectedAcyclicGraph<THNode, DefaultEdge> graph;

    public TypeHierarchyGraph(ValidationContext.SorbeFactory sorbeFactory,
                              DefaultDirectedGraph<Node, DefaultEdge> schemaTypeHierarchyGraph,
                              Function<Node, ShapeDecl> shapeExprRefsDefs) {
        this.sorbeFactory = sorbeFactory;
        this.graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        Map<Node, THNode> labelToThNodeMap = new HashMap<>();
        schemaTypeHierarchyGraph.vertexSet().forEach(label -> {
            ShapeExpr extendableShape = shapeExprRefsDefs.apply(label).getShapeExpr();
            Pair<Shape, List<ShapeExpr>> mainShapeAndConstraints = Util.mainShapeAndConstraints(extendableShape, shapeExprRefsDefs);
            THNode thNode = new THNode(
                    extendableShape,
                    mainShapeAndConstraints.getLeft(),
                    mainShapeAndConstraints.getRight(),
                    sorbeFactory.getSorbe(mainShapeAndConstraints.getLeft().getTripleExpr()));
            graph.addVertex(thNode);
            labelToThNodeMap.put(label, thNode);
        });
        schemaTypeHierarchyGraph.edgeSet().forEach(e -> {
            Node src = schemaTypeHierarchyGraph.getEdgeSource(e);
            Node tgt = schemaTypeHierarchyGraph.getEdgeTarget(e);
            graph.addEdge(labelToThNodeMap.get(src), labelToThNodeMap.get(tgt));
        });
    }

    // The result map contains all ShapeExpr (ids) that are supertypes of this node (including the node) and
    // for each of them, the triple constraints of its main shape
    Map<Integer, List<TripleConstraint>> getTCs (ShapeExpr extendableShapeExpr) {
        throw new UnsupportedOperationException("TODO");
        // use graph accessibility to collect all the TCs
    }

    /*
    Set<Triple> getMatchedTriples (THNode node, Map<Triple, TripleConstraint> matching, Set<Triple> among) {
        Set<Integer> tripleConstraints = getTCs(node).stream().map(tc -> tc.id).collect(Collectors.toSet());
        return matching.entrySet().stream()
                .filter(e -> tripleConstraints.contains(e.getValue().id))
                .map(e -> e.getKey())
                .collect(Collectors.toSet());
    }
     */


}
