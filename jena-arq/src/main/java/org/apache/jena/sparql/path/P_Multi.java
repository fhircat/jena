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

package org.apache.jena.sparql.path;

import org.apache.jena.sparql.util.NodeIsomorphismMap;

/** A path element that, on evaluation, switches to multi-cardinality semantics. */
public class P_Multi extends P_Path1 {
    public P_Multi(Path p) {
        super(p);
    }

    @Override
    public void visit(PathVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public boolean equalTo(Path path2, NodeIsomorphismMap isoMap) {
        if ( path2 instanceof P_Multi other )
            return getSubPath().equalTo(other.getSubPath(), isoMap);
        return false;
    }

    @Override
    public int hashCode() {
        return getSubPath().hashCode() ^ hashMulti;
    }

}
