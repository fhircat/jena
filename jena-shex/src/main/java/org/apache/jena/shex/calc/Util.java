/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jena.shex.calc;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shex.ShexSchemaStructureException;
import org.apache.jena.shex.expressions.*;
import org.apache.jena.shex.validation.ValidationContext;
import org.apache.jena.system.G;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

// TODO different utilities, waiting to be moved to an appropriate place
public class Util {

    /** Dereferences until a non reference is found. */
    public static ShapeExpr dereference (ShapeExpr shapeExpr, Function<Node, ShapeExpr> shapeExprRefsDefs) {
        ShapeExpr expr = shapeExpr;
        while (expr instanceof ShapeExprRef) {
            expr = shapeExprRefsDefs.apply(((ShapeExprRef) expr).getLabel());
        }
        return expr;
    }

    private static final int INDEX_MAIN = 0;
    public static Pair<Shape, List<ShapeExpr>> mainShapeAndConstraints (ShapeExpr shapeExpr,
                                                                        Function<Node, ShapeExpr> shapeExprRefsDefs) {
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
                                   Function<Node, ShapeExpr> shapeExprRefsDefs) {
        return mainShapeAndConstraints(shapeExpr, shapeExprRefsDefs).getLeft();
    }

    public static List<ShapeExpr> constraints (ShapeExpr shapeExpr,
                                               Function<Node, ShapeExpr> shapeExprRefsDefs) {
        return mainShapeAndConstraints(shapeExpr, shapeExprRefsDefs).getRight();
    }

    /** Partitions the triples of the neighbourhood of the node between those whose predicate appears in
     * the tripleExprs, and those whose predicate does not appear.*/
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

}
