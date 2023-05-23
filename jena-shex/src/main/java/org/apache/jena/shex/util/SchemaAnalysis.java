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


package org.apache.jena.shex.util;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.expressions.*;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.*;
import java.util.function.Predicate;

public class SchemaAnalysis {

    private final Map<Node, ShapeDecl> shapeDeclMap;
    private final Map<Node, TripleExpr> tripleRefsMap;


    public SchemaAnalysis(Map<Node, ShapeDecl> shapeDeclMap, Map<Node, TripleExpr> tripleRefsMap) {
        this.shapeDeclMap = shapeDeclMap;
        this.tripleRefsMap = tripleRefsMap;
    }

    public boolean isCorrect () {
        // All references are defined
        if (! checkAllReferencesDefined())
            return false;

        // No cyclic references
        if (! checkNoCyclicReferences())
            return false;

        // Stratified negation
        if (! checkStratifiedNegation())
            return false;

        return true;
    }

    private boolean checkAllReferencesDefined() {
        Set<Node> allTripleExprRefs = new HashSet<>();
        Set<Node> allShapeExprRefs = new HashSet<>();
        shapeDeclMap.values().forEach(decl ->
                accumulateDirectShapeExprRefsInShapeExpr(decl.getShapeExpr(), allShapeExprRefs));
        shapeDeclMap.values().forEach(decl -> accumulateDirectTripleExprRefsInShapeExpr(decl.getShapeExpr(), allTripleExprRefs));
        allShapeExprRefs.removeAll(shapeDeclMap.keySet());
        allTripleExprRefs.removeAll(tripleRefsMap.keySet());
        // TODO can raise an error with message : the two sets contain the undefined references
        return allShapeExprRefs.isEmpty() && allTripleExprRefs.isEmpty();
    }

    private boolean checkNoCyclicReferences() {
        Set<Node> acc = new HashSet<>();

        DefaultDirectedGraph<Node, DefaultEdge> shapeRefDependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        shapeDeclMap.forEach((label, decl) -> {
            shapeRefDependencyGraph.addVertex(label);
            acc.clear();
            accumulateDirectShapeExprRefsInShapeExpr(decl.getShapeExpr(), acc);
            acc.forEach(referencedLabel -> {
                shapeRefDependencyGraph.addVertex(referencedLabel);
                shapeRefDependencyGraph.addEdge(label, referencedLabel);
            });
        });
        CycleDetector<Node, DefaultEdge> cycleDetector = new CycleDetector<>(shapeRefDependencyGraph);
        if (cycleDetector.detectCycles())
            return false;

        DefaultDirectedGraph<Node, DefaultEdge> texprRedDependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        tripleRefsMap.forEach((label, tripleExpr) -> {
            texprRedDependencyGraph.addVertex(label);
            acc.clear();
            accumulateDirectTripleExprRefsInTripleExpr(tripleExpr, acc);
            acc.forEach(referencedLabel -> {
                texprRedDependencyGraph.addVertex(referencedLabel);
                texprRedDependencyGraph.addEdge(label, referencedLabel);
            });
        });
        cycleDetector = new CycleDetector<>(texprRedDependencyGraph);
        if (cycleDetector.detectCycles())
            return false;

        return true;
    }

    private static final double NEGDEP = -1.0;
    private static final double POSDEP = 1.0;

    private boolean checkStratifiedNegation() {

        DefaultDirectedWeightedGraph<Node, DefaultWeightedEdge> dependencyGraph
                = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        Predicate<List<Node>> isCycleWithNegation = cycle -> {
            Iterator<Node> it = cycle.iterator();
            Node edgeSrc = it.next();
            Node edgeTgt;
            while (it.hasNext()) {
                edgeTgt = it.next();
                if (dependencyGraph.getEdgeWeight(dependencyGraph.getEdge(edgeSrc, edgeTgt)) == NEGDEP)
                    return true;
                edgeSrc = edgeTgt;
            }
            return false;
        };

        shapeDeclMap.forEach((label, decl) -> {
            dependencyGraph.addVertex(label);
            List<Pair<Node, Boolean>> references = collectReferencesWithSign(decl.getShapeExpr());
            references.forEach(ref -> {
                dependencyGraph.addVertex(ref.getLeft());
                DefaultWeightedEdge edge = dependencyGraph.addEdge(label, ref.getLeft());
                dependencyGraph.setEdgeWeight(edge, ref.getRight() ? NEGDEP : POSDEP);
            });
        });

        TarjanSimpleCycles<Node, DefaultWeightedEdge> cycleEnumerationAlgorithm = new TarjanSimpleCycles<>(dependencyGraph);
        return cycleEnumerationAlgorithm.findSimpleCycles().stream().noneMatch(isCycleWithNegation);
    }

    // ------------------------------------------------------------------------------------------------
    // Collectors, based on visitors
    // ------------------------------------------------------------------------------------------------
    private void accumulateDirectShapeExprRefsInShapeExpr (ShapeExpr shapeExpr, Collection<Node> acc) {
        ShapeExprAccumulationVisitor<Node> seAccVisitor = new ShapeExprAccumulationVisitor<>(acc) {
            @Override
            public void visit(ShapeExprRef shapeExprRef) {
                accumulate(shapeExprRef.getLabel());
            }
        };
        VoidWalker walker = new VoidWalker.Builder()
                .processShapeExprsWith(seAccVisitor)
                .build();
        shapeExpr.visit(walker);
    }

    private void accumulateDirectTripleExprRefsInShapeExpr (ShapeExpr shapeExpr, Collection<Node> acc) {
        TripleExprAccumulationVisitor<Node> teAccVisitor = new TripleExprAccumulationVisitor<>(acc) {
            @Override
            public void visit(TripleExprRef tripleExprRef) {
                accumulate(tripleExprRef.getLabel());
            }
        };
        VoidWalker walker = new VoidWalker.Builder()
                .traverseShapes()
                .processTripleExprsWith(teAccVisitor)
                .build();
        shapeExpr.visit(walker);
    }

    private void accumulateDirectTripleExprRefsInTripleExpr (TripleExpr tripleExpr, Collection<Node> acc) {
        TripleExprAccumulationVisitor<Node> teAccVisitor = new TripleExprAccumulationVisitor<>(acc) {
            @Override
            public void visit(TripleExprRef tripleExprRef) {
                accumulate(tripleExprRef.getLabel());
            }
        };
        VoidWalker walker = new VoidWalker.Builder()
                .processTripleExprsWith(teAccVisitor)
                .build();
        tripleExpr.visit(walker);
    }

    private List<Pair<Node, Boolean>> collectReferencesWithSign (ShapeExpr shapeExpr) {
        List<Pair<Node, Boolean>> acc = new ArrayList<>();
        ReferencesCollector rc = new ReferencesCollector(acc);
        shapeExpr.visit(rc);
        return acc;
    }

    private static final Object SHAPE = new Object();
    private static final Object NOT = new Object();
    private class ReferencesCollector implements VoidTripleExprVisitor, VoidShapeExprVisitor {

        private final Deque<Object> context = new ArrayDeque<>();
        private final List<Pair<Node, Boolean>> acc;

        private ReferencesCollector(List<Pair<Node, Boolean>> acc) {
            this.acc = acc;
        }

        private boolean isInShape () {
            return context.contains(SHAPE);
        }

        private boolean isInNegatedContext () {
            // context is negated if there is an odd number of NOT between two SHAPE (or before the first SHAPE)
            int numberNegations = 0;
            for (Object o : context) {
                if (o == NOT)
                    numberNegations++;
                if (o == SHAPE) {
                    if (numberNegations % 2 != 0)
                        return true;
                    numberNegations = 0;
                }
            }
            return false;
        }

        @Override
        public void visit(ShapeExprRef shapeExprRef) {
            if (isInShape())
                acc.add(new ImmutablePair<>(shapeExprRef.getLabel(), !isInNegatedContext()));
            else
                shapeDeclMap.get(shapeExprRef.getLabel()).getShapeExpr().visit(this);
        }

        @Override
        public void visit(ShapeNot shapeNot) {
            context.addLast(NOT);
            shapeNot.getShapeExpr().visit(this);
            context.removeLast();
        }

        @Override
        public void visit(Shape shape) {
            context.addLast(SHAPE);
            shape.getTripleExpr().visit(this);
            context.removeLast();
        }

        @Override
        public void visit(ShapeAnd shapeAnd) {
            shapeAnd.getShapeExprs().forEach(e -> e.visit(this));
        }

        @Override
        public void visit(ShapeOr shapeOr) {
            shapeOr.getShapeExprs().forEach(e -> e.visit(this));
        }

        @Override
        public void visit(ShapeExternal shapeExternal) {
            // do nothing
        }

        @Override
        public void visit(NodeConstraint nodeConstraint) {
            // do nothing
        }

        @Override
        public void visit(TripleExprCardinality tripleExprCardinality) {
            tripleExprCardinality.getSubExpr().visit(this);
        }

        @Override
        public void visit(EachOf eachOf) {
            eachOf.getTripleExprs().forEach(e -> e.visit(this));
        }

        @Override
        public void visit(OneOf oneOf) {
            oneOf.getTripleExprs().forEach(e -> e.visit(this));
        }

        @Override
        public void visit(TripleExprEmpty tripleExprEmpty) {
            // do nothing
        }

        @Override
        public void visit(TripleExprRef tripleExprRef) {
            tripleRefsMap.get(tripleExprRef.getLabel()).visit(this);
        }

        @Override
        public void visit(TripleConstraint tripleConstraint) {
            tripleConstraint.getValueExpr().visit(this);
        }
    }

}
