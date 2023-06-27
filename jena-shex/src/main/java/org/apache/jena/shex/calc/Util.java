package org.apache.jena.shex.calc;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.other.G;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.ShexSchemaStructureException;
import org.apache.jena.shex.expressions.Shape;
import org.apache.jena.shex.expressions.ShapeAnd;
import org.apache.jena.shex.expressions.ShapeExpr;
import org.apache.jena.shex.expressions.ShapeExprRef;
import org.apache.jena.shex.validation.ESet;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

// different utilities, waiting to be moved to an appropriate place
public class Util {

    /** Dereferences until a non reference is found. */
    public static ShapeExpr dereference (ShapeExpr shapeExpr, Function<Node, ShapeDecl> shapeExprRefsDefs) {
        ShapeExpr expr = shapeExpr;
        while (expr instanceof ShapeExprRef) {
            expr = shapeExprRefsDefs.apply(((ShapeExprRef) expr).getLabel()).getShapeExpr();
        }
        return expr;
    }

    /** Returns the main shapes of all the extended shapes, including the main shape of the given extendable shape expression. */
    public static List<Shape> mainShapesOfBases(ShapeExpr extendableShape, Function<Node, ShapeDecl> shapeExprRefsDefs) {
        List<Shape> result = new ArrayList<>();
        Deque<Node> extendedFifo = new ArrayDeque<>();

        Consumer<ShapeExpr> step = (se) -> {
            Shape mainShape = mainShapeAndConstraints(se, shapeExprRefsDefs).getLeft();
            result.add(mainShape);
            mainShape.getExtends().forEach( e -> extendedFifo.addLast(e.getLabel()));
        };

        ShapeExpr current = extendableShape;
        step.accept(current);
        while (!extendedFifo.isEmpty()) {
            current = shapeExprRefsDefs.apply(extendedFifo.removeFirst()).getShapeExpr();
            step.accept(current);
        }
        return result;
    }

    /** Returns the base (ie extended) shape expressions of the given shape expression, including the shape expression itself.
     * @param extendableShape Must be an extendable shape, otherwise an error may occur.
     * @param shapeExprRefsDefs Allows to dereference shape expression references.
     * @return
     */
    public static ESet<ShapeExpr> extendedBases(ShapeExpr extendableShape, Function<Node, ShapeDecl> shapeExprRefsDefs) {
        // TODO add test with diamond extension and verify that the shape that is extended several times is satisfied only once
        ESet<ShapeExpr> result = new ESet<>(true);
        Deque<Node> extendedFifo = new ArrayDeque<>();

        Consumer<ShapeExpr> step = (se) -> {
            result.add(se);
            Shape mainShape = mainShapeAndConstraints(se, shapeExprRefsDefs).getLeft();
            mainShape.getExtends().forEach(e -> extendedFifo.addLast(e.getLabel()));
        };

        ShapeExpr current = extendableShape;
        step.accept(current);
        while (!extendedFifo.isEmpty()) {
            current = shapeExprRefsDefs.apply(extendedFifo.removeFirst()).getShapeExpr();
            step.accept(current);
        }
        return result;
    }



    private static final int INDEX_MAIN = 0;
    public static Pair<Shape, List<ShapeExpr>> mainShapeAndConstraints (ShapeExpr shapeExpr,
                                                                        Function<Node, ShapeDecl> shapeExprRefsDefs) {
        Shape mainShape;
        List<ShapeExpr> constraints;

        if (shapeExpr instanceof Shape) {
            mainShape = (Shape) shapeExpr;
            constraints = Collections.emptyList();
        } else if (! (shapeExpr instanceof ShapeAnd))
            throw new ShexSchemaStructureException("Extendable shape is not a ShapeAnd");
        else {
            ShapeAnd shapeAnd = (ShapeAnd) shapeExpr;
            ShapeExpr first = Util.dereference(shapeAnd.getShapeExprs().get(INDEX_MAIN), shapeExprRefsDefs);
            if (!(first instanceof Shape))
                throw new ShexSchemaStructureException("Extendable shape does not have a main shape");

            mainShape = (Shape) first;
            constraints = shapeAnd.getShapeExprs().subList(1, shapeAnd.getShapeExprs().size());
        }
        return Pair.of(mainShape, constraints);
    }

    public static DefaultDirectedGraph<Node, DefaultEdge> computeExtendsReferencesGraph(Map<Node, ShapeDecl> shapeDeclMap) {
        DefaultDirectedGraph<Node, DefaultEdge> typeHierarchyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        shapeDeclMap.keySet().forEach(typeHierarchyGraph::addVertex);
        shapeDeclMap.forEach((label, decl) -> {
            List<Shape> accShapes = new ArrayList<>();
            AccumulationUtil.accumulateShapesFollowShapeExprRefs(decl.getShapeExpr(), shapeDeclMap::get, accShapes);
            for (Shape shape : accShapes)
                for (ShapeExprRef extended : shape.getExtends())
                    typeHierarchyGraph.addEdge(label, extended.getLabel());
        });
        return typeHierarchyGraph;
    }

    public static void collectRelevantNeighbourhood(Graph graph, Node dataNode,
                                                    Set<Node> fwdPredicates, Set<Node> invPredicates,
                                                    Set<Triple> accMatchables, Set<Triple> accNonMatchables) {

        // outgoing
        ExtendedIterator<Triple> x = G.find(graph, dataNode, null, null);
        x.forEach(t -> {
            if (fwdPredicates.contains(t.getPredicate()))
                accMatchables.add(t);
            else
                accNonMatchables.add(t);
        });

        // incoming
        ExtendedIterator<Triple> y = G.find(graph, null, null, dataNode);
        y.filterKeep(t -> invPredicates.contains(t.getPredicate())).forEach(accMatchables::add);
    }

    public static boolean hasExtends(ShapeExpr shapeExpr, Function<Node, ShapeDecl> shapeExprRefsDefs) {
        Pair<Shape, List<ShapeExpr>> mainAndConstr;
        try {
             mainAndConstr = mainShapeAndConstraints(shapeExpr, shapeExprRefsDefs);
        } catch (ShexSchemaStructureException e) {
            return false;
        }
        return ! mainAndConstr.getLeft().getExtends().isEmpty();
    }
}
