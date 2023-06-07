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

package org.apache.jena.shex.validation;

import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.TripleConstraint;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// A multiset over org.apache.jena.shex.expressions.TripleConstraint
/*package*/ class Bag {

    private Map<Integer, Integer> cardMap;

    /*package*/ static Bag fromMatching(Map<Triple, TripleConstraint> matching, List<TripleConstraint> base) {
        Bag bag = new Bag();
        bag.cardMap = base.stream().collect(Collectors.toMap(tc -> tc.id, x->0));
        for (TripleConstraint tc : matching.values())
            bag.cardMap.computeIfPresent(tc.id, (k, old) -> old+1);
        return bag;
    }

    public final int getCard (TripleConstraint tripleConstraint) {
        return cardMap.get(tripleConstraint.id);
    }
}
