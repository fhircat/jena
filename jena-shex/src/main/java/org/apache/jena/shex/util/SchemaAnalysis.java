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
import org.jgrapht.Graphs;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.alg.cycle.SzwarcfiterLauerSimpleCycles;
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

        if (! checkNoDuplicatedRefLabels())
            return false;

        if (! checkAllReferencesDefined())
            return false;

        if (! checkNoCyclicReferences())
            return false;

        if (! checkStratifiedNegation())
            return false;

        if (! checkExtendsCorrect())
            return false;

        return true;
    }

    private boolean checkNoDuplicatedRefLabels () {
        Set<Node> tripleExprLabels = new HashSet<>(tripleRefsMap.keySet());
        return shapeDeclMap.keySet().stream().noneMatch(tripleExprLabels::contains);
    }


    private boolean checkAllReferencesDefined() {
        Set<Node> allTripleExprRefs = new HashSet<>();
        Set<Node> allShapeExprRefs = new HashSet<>();
        try {
            shapeDeclMap.values().forEach(decl ->
                    accumulateAllShapeExprRefsInShapeExpr(decl.getShapeExpr(), allShapeExprRefs));
            shapeDeclMap.values().forEach(decl ->
                    accumulateAllTripleExprRefsInShapeExpr(decl.getShapeExpr(), allTripleExprRefs));
        } catch (UndefinedReferenceException e) {
            return false;
        }
        allShapeExprRefs.removeAll(shapeDeclMap.keySet());
        allTripleExprRefs.removeAll(tripleRefsMap.keySet());
        return allShapeExprRefs.isEmpty() && allTripleExprRefs.isEmpty();
    }

    private boolean checkNoCyclicReferences() {
        Set<Node> acc = new HashSet<>();

        DefaultDirectedGraph<Node, DefaultEdge> shapeRefDependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        shapeDeclMap.keySet().forEach(shapeRefDependencyGraph::addVertex);
        shapeDeclMap.forEach((label, decl) -> {
            acc.clear();
            accumulateDirectShapeExprRefsInShapeExpr(decl.getShapeExpr(), acc);
            acc.forEach(referencedLabel -> shapeRefDependencyGraph.addEdge(label, referencedLabel));
        });
        CycleDetector<Node, DefaultEdge> cycleDetector = new CycleDetector<>(shapeRefDependencyGraph);
        if (cycleDetector.detectCycles())
            return false;

        DefaultDirectedGraph<Node, DefaultEdge> texprRefDependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        tripleRefsMap.keySet().forEach(texprRefDependencyGraph::addVertex);
        tripleRefsMap.forEach((label, tripleExpr) -> {
            acc.clear();
            accumulateDirectTripleExprRefsInTripleExpr(tripleExpr, acc);
            acc.forEach(referencedLabel -> texprRefDependencyGraph.addEdge(label, referencedLabel));
        });
        cycleDetector = new CycleDetector<>(texprRefDependencyGraph);
        if (cycleDetector.detectCycles())
            return false;

        return true;
    }

    private static final double NEGDEP = -1.0;
    private static final double POSDEP = 1.0;

    private boolean checkStratifiedNegation() {

        DefaultDirectedWeightedGraph<Node, DefaultWeightedEdge> dependencyGraph
                = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        shapeDeclMap.keySet().forEach(dependencyGraph::addVertex);
        shapeDeclMap.forEach((label, decl) -> {
            List<Pair<Node, Boolean>> references = collectReferencesWithSign(decl.getShapeExpr());
            references.forEach(ref -> {
                Node referencedLabel = ref.getLeft();
                boolean isNegated = ref.getRight();
                DefaultWeightedEdge edge = dependencyGraph.getEdge(label, referencedLabel);
                if (edge == null)
                    Graphs.addEdge(dependencyGraph, label, referencedLabel, isNegated ? NEGDEP : POSDEP);
                else if (dependencyGraph.getEdgeWeight(edge) == POSDEP && isNegated)
                    dependencyGraph.setEdgeWeight(edge, NEGDEP);
            });
        });

        Predicate<List<Node>> isCycleWithNegation = cycle -> {
            cycle.add(cycle.get(0));
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

        SzwarcfiterLauerSimpleCycles<Node, DefaultWeightedEdge> cycleEnumerationAlgorithm
                = new SzwarcfiterLauerSimpleCycles<>(dependencyGraph);
        return cycleEnumerationAlgorithm.findSimpleCycles().stream().noneMatch(isCycleWithNegation);
    }

    private boolean checkExtendsCorrect () {
        DefaultDirectedGraph<Node, DefaultEdge> typeHierarchyGraph = computeTypeHierarchyGraph();
        CycleDetector<Node, DefaultEdge> cycleDetector = new CycleDetector<>(typeHierarchyGraph);
        if (cycleDetector.detectCycles())
            return false;

        return typeHierarchyGraph.vertexSet().stream()
                // every label that extend another label or is extended
                .filter(label -> typeHierarchyGraph.degreeOf(label) > 0)
                // must be of the form mainShape AND constraints
                .allMatch(label -> isMainShapeAndConstraints(shapeDeclMap.get(label).getShapeExpr()));
    }

    private DefaultDirectedGraph<Node, DefaultEdge> computeTypeHierarchyGraph () {
        DefaultDirectedGraph<Node, DefaultEdge> typeHierarchyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        shapeDeclMap.keySet().forEach(typeHierarchyGraph::addVertex);
        shapeDeclMap.forEach((label, decl) -> {
            List<Shape> accShapes = new ArrayList<>();
            accumulateShapes(decl.getShapeExpr(), accShapes);
            for (Shape shape : accShapes)
                for (ShapeExprRef extended : shape.getExtends())
                    typeHierarchyGraph.addEdge(label, extended.getLabel());
        });
        return typeHierarchyGraph;
    }

    private boolean isMainShapeAndConstraints (ShapeExpr shapeExpr) {
        Shape mainShape;
        List<ShapeExpr> constraints;

        if (shapeExpr instanceof Shape) {
            mainShape = (Shape) shapeExpr;
            constraints = Collections.emptyList();
        } else if (! (shapeExpr instanceof ShapeAnd))
            return false;
        else {
            ShapeAnd shapeAnd = (ShapeAnd) shapeExpr;

            if (!(shapeAnd.getShapeExprs().get(0) instanceof Shape))
                return false;
            mainShape = (Shape) (shapeAnd.getShapeExprs().get(0));
            constraints = shapeAnd.getShapeExprs().subList(1, shapeAnd.getShapeExprs().size());
        }
        List<Shape> shapesInConstraints = new ArrayList<>();
        constraints.forEach(se -> accumulateShapes(se, shapesInConstraints));
        if (shapesInConstraints.stream().anyMatch(shape -> ! shape.getExtends().isEmpty()))
            return false;
        Pair<Set<Node>, Set<Node>> mainShapePredicates = AccumulationUtil.collectPredicates(mainShape.getTripleExpr(),
                tripleRefsMap::get);
        Set<Node> mainShapeFwdPredicates = mainShapePredicates.getLeft();
        Set<Node> mainShapeInvPredicates = mainShapePredicates.getRight();
        if (shapesInConstraints.stream().anyMatch(shape -> {
            Pair<Set<Node>, Set<Node>> predicates = AccumulationUtil.collectPredicates(shape.getTripleExpr(),
                    tripleRefsMap::get);
            return mainShapeFwdPredicates.containsAll(predicates.getLeft())
                    && mainShapeInvPredicates.containsAll(predicates.getRight());
        }))
            return false;

        return true;
    }


    // ------------------------------------------------------------------------------------------------
    // Collectors, based on visitors
    // ------------------------------------------------------------------------------------------------


    private static class ShapeExprRefAccumulationVisitor extends ShapeExprAccumulationVisitor<Node> {
        public ShapeExprRefAccumulationVisitor(Collection<Node> acc) {
            super(acc);
        }
        @Override
        public void visit(ShapeExprRef shapeExprRef) {
            accumulate(shapeExprRef.getLabel());
        }
    }

    private static class TripleExprRefAccumulationVisitor extends TripleExprAccumulationVisitor<Node> {
        public TripleExprRefAccumulationVisitor(Collection<Node> acc) {
            super(acc);
        }
        @Override
        public void visit(TripleExprRef tripleExprRef) {
            accumulate(tripleExprRef.getLabel());
        }
    };

    private void accumulateDirectShapeExprRefsInShapeExpr (ShapeExpr shapeExpr, Collection<Node> acc) {
        ShapeExprAccumulationVisitor<Node> seAccVisitor = new ShapeExprRefAccumulationVisitor(acc);
        VoidWalker walker = VoidWalker.builder()
                .processShapeExprsWith(seAccVisitor)
                .build();
        shapeExpr.visit(walker);
    }

    private void accumulateAllShapeExprRefsInShapeExpr (ShapeExpr shapeExpr, Collection<Node> acc) {
        ShapeExprAccumulationVisitor<Node> seAccVisitor = new ShapeExprRefAccumulationVisitor(acc);
        VoidWalker walker = VoidWalker.builder()
                .processShapeExprsWith(seAccVisitor)
                .traverseTripleConstraints()
                .traverseShapes()
                .build();
        shapeExpr.visit(walker);
    }

    private void accumulateAllTripleExprRefsInShapeExpr(ShapeExpr shapeExpr, Collection<Node> acc) {
        TripleExprRefAccumulationVisitor teAccVisitor = new TripleExprRefAccumulationVisitor(acc);
        VoidWalker walker = VoidWalker.builder()
                .processTripleExprsWith(teAccVisitor)
                .traverseShapes()
                .traverseTripleConstraints()
                .build();
        shapeExpr.visit(walker);
    }

    private void accumulateDirectTripleExprRefsInTripleExpr (TripleExpr tripleExpr, Collection<Node> acc) {
        TripleExprAccumulationVisitor<Node> teAccVisitor = new TripleExprRefAccumulationVisitor(acc);
        VoidWalker walker = VoidWalker.builder()
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

    private void accumulateShapes (ShapeExpr shapeExpr, Collection<Shape> acc) {
        ShapeExprAccumulationVisitor<Shape> seVisitor = new ShapeExprAccumulationVisitor<>(acc) {
            @Override
            public void visit(Shape shape) {
                accumulate(shape);
            }
        };
        VoidWalker walker = VoidWalker.builder()
                .processShapeExprsWith(seVisitor)
                .followShapeExprRefs(shapeDeclMap::get)
                .build();
        shapeExpr.visit(walker);
    }

    private static final Object SHAPE = "SHAPE";
    private static final Object NOT = "NOT";
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
                else if (o == SHAPE) {
                    if (numberNegations % 2 != 0)
                        return true;
                    numberNegations = 0;
                }
            }
            return numberNegations % 2 != 0;
        }

        @Override
        public void visit(ShapeExprRef shapeExprRef) {
            if (isInShape())
                acc.add(new ImmutablePair<>(shapeExprRef.getLabel(), isInNegatedContext()));
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
            context.addLast(shape.getExtras());
            shape.getTripleExpr().visit(this);
            context.removeLast();
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
            Set<Node> extras = (Set<Node>) context.getLast();
            boolean predicateIsExtra = extras.contains(tripleConstraint.getPredicate());
            if (predicateIsExtra)
                context.addLast("NOT");
            tripleConstraint.getValueExpr().visit(this);
            if (predicateIsExtra)
                context.removeLast();
        }
    }
}
