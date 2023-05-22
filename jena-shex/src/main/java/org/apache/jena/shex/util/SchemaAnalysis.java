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

import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShapeDecl;
import org.apache.jena.shex.expressions.*;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;

public class SchemaAnalysis {

    private final Map<Node, ShapeDecl> shapeMap;
    private final Map<Node, TripleExpr> tripleRefs;


    public SchemaAnalysis(Map<Node, ShapeDecl> shapeMap, Map<Node, TripleExpr> tripleRefs) {
        this.shapeMap = shapeMap;
        this.tripleRefs = tripleRefs;
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
        shapeMap.values().forEach(decl -> accumulateDirectShapeExprRefsInShapeExpr(decl.getShapeExpr(), allShapeExprRefs));
        shapeMap.values().forEach(decl -> accumulateDirectTripleExprRefsInShapeExpr(decl.getShapeExpr(), allTripleExprRefs));
        allShapeExprRefs.removeAll(shapeMap.keySet());
        allTripleExprRefs.removeAll(tripleRefs.keySet());
        // TODO can raise an error with message : the two sets contain the undefined references
        return allShapeExprRefs.isEmpty() && allTripleExprRefs.isEmpty();
    }

    private boolean checkNoCyclicReferences() {
        Set<Node> acc = new HashSet<>();

        DefaultDirectedGraph<Node, DefaultEdge> shapeRefDependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        shapeMap.forEach((label, decl) -> {
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
        tripleRefs.forEach((label, tripleExpr) -> {
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

    private boolean checkStratifiedNegation() {
        // FIXME : implement it
        return true;
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

}
