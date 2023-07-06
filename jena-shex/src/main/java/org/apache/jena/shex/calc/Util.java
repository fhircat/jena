package org.apache.jena.shex.calc;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.other.G;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.ShexSchemaStructureException;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.validation.ValidationContext;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            Shape mainShape = mainShape(se, shapeExprRefsDefs);
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

    private static final int INDEX_MAIN = 0;
    public static Pair<Shape, List<ShapeExpr>> mainShapeAndConstraints (ShapeExpr shapeExpr,
                                                                        Function<Node, ShapeDecl> shapeExprRefsDefs) {
        Shape mainShape;
        List<ShapeExpr> constraints;

        if (shapeExpr instanceof Shape) {
            mainShape = (Shape) shapeExpr;
            constraints = List.of();
        } else if (! (shapeExpr instanceof ShapeAnd))
            throw new ShexSchemaStructureException("Extendable shape is neither a ShapeAnd nor a Shape");
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

    public static Shape mainShape (ShapeExpr shapeExpr,
                                   Function<Node, ShapeDecl> shapeExprRefsDefs) {
        return mainShapeAndConstraints(shapeExpr, shapeExprRefsDefs).getLeft();
    }

    public static List<ShapeExpr> constraints (ShapeExpr shapeExpr,
                                               Function<Node, ShapeDecl> shapeExprRefsDefs) {
        return mainShapeAndConstraints(shapeExpr, shapeExprRefsDefs).getRight();
    }

    public static void retrieveRelevantNeighbourhood(Graph graph, Node dataNode,
                                                     Collection<TripleExpr> tripleExprs,
                                                     Set<Triple> accMatchables, Set<Triple> accNonMatchables,
                                                     ValidationContext vCxt) {

        Set<Node> fwdPredicates = new HashSet<>();
        Set<Node> invPredicates = new HashSet<>();
        AccumulationUtil.accumulatePredicates(tripleExprs,
                vCxt::getTripleExpr, fwdPredicates, invPredicates);

        // outgoing
        ExtendedIterator<Triple> outNeighbourhood = G.find(graph, dataNode, null, null);
        outNeighbourhood.forEach(t -> {
            if (fwdPredicates.contains(t.getPredicate()))
                accMatchables.add(t);
            else
                accNonMatchables.add(t);
        });

        // incoming
        ExtendedIterator<Triple> inNeighbourhood = G.find(graph, null, null, dataNode);
        inNeighbourhood.filterKeep(t -> invPredicates.contains(t.getPredicate())).forEach(accMatchables::add);
    }

    public static Set<Triple> filterRelevantNeighbourhood(Set<Triple> neighbourhood,
                                                          Node dataNode,
                                                          TripleExpr tripleExpr,
                                                          ValidationContext vCxt) {

        Set<Node> fwdPredicates = new HashSet<>();
        Set<Node> invPredicates = new HashSet<>();
        AccumulationUtil.accumulatePredicates(List.of(tripleExpr),
                vCxt::getTripleExpr, fwdPredicates, invPredicates);

        return neighbourhood.stream()
                .filter(triple ->
                        triple.getSubject().equals(dataNode) && fwdPredicates.contains(triple.getPredicate())
                                ||
                                triple.getObject().equals(dataNode) && invPredicates.contains(triple.getPredicate()))
                .collect(Collectors.toSet());

    }

    public static void filterNeighbourhoodForPredicates (Set<Triple> neighbourhood, Node dataNode,
                                                         Set<Node> fwdPredicates, Set<Node> invPredicates,
                                                         Set<Triple> accMatchables) {
            neighbourhood.forEach(triple -> {
                if (triple.getSubject().equals(dataNode) && fwdPredicates.contains(triple.getPredicate())
                    ||
                    triple.getObject().equals(dataNode) && invPredicates.contains(triple.getPredicate()))
                    accMatchables.add(triple);
            });
    }



    public static boolean hasExtends(ShapeExpr shapeExpr, Function<Node, ShapeDecl> shapeExprRefsDefs) {
        Shape mainShape;
        try {
             mainShape = mainShape(shapeExpr, shapeExprRefsDefs);
        } catch (ShexSchemaStructureException e) {
            return false;
        }
        return ! mainShape.getExtends().isEmpty();
    }

}
