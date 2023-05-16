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

package org.apache.jena.shex.runner;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/** Shex test vocabulary. */
public class ShexT {
    /*
--------------------
| p                |
====================
| sht:shape        | **
| sht:data         | **
| sht:schema       | **
| sht:focus        | **
| sht:trait        |
| sht:map          |
| sht:shapeExterns |
| sht:semActs      |
--------------------

-------------------------
| classes               |
=========================
| sht:ValidationTest    |
| sht:ValidationFailure |
-------------------------

Also class:
    sht:RepresentationTest
    and sx:

     */

    public static final String BASE_URI = "http://www.w3.org/ns/shacl/test-suite";

    public static final String NS = BASE_URI + "#";

    // Classes
    public final static Resource cValidationTest        = ResourceFactory.createResource(NS + "ValidationTest");
    public final static Resource cValidationFailure     = ResourceFactory.createResource(NS + "ValidationFailure");
    public final static Resource cRepresentationTest    = ResourceFactory.createResource(NS + "RepresentationTest");

    // Traits
    public final static Resource tAbstract = ResourceFactory.createResource(NS + "Abstract"); // 7
    public final static Resource tAndShapeShapeession = ResourceFactory.createResource(NS + "AndShapeShapeession"); // 1
    public final static Resource tAndValueExpression = ResourceFactory.createResource(NS + "AndValueExpression"); // 24
    public final static Resource tAnnotation = ResourceFactory.createResource(NS + "Annotation"); // 15
    public final static Resource tBNodeShapeLabel = ResourceFactory.createResource(NS + "BNodeShapeLabel"); // 2
    public final static Resource tBooleanEquivalence = ResourceFactory.createResource(NS + "BooleanEquivalence"); // 6
    public final static Resource tClosed = ResourceFactory.createResource(NS + "Closed"); // 12
    public final static Resource tComparatorFacet = ResourceFactory.createResource(NS + "ComparatorFacet"); // 231
    public final static Resource tDatatypedLiteralEquivalence = ResourceFactory.createResource(NS + "DatatypedLiteralEquivalence"); // 4
    public final static Resource tCrossFileBNodeShapeLabel = ResourceFactory.createResource(NS + "CrossFileBNodeShapeLabel"); // 2
    public final static Resource tDatatype = ResourceFactory.createResource(NS + "Datatype"); // 6
    public final static Resource tDotCardinality = ResourceFactory.createResource(NS + "DotCardinality"); // 28
    public final static Resource tEachOf = ResourceFactory.createResource(NS + "EachOf"); // 65
    public final static Resource tEachOfUnvisited = ResourceFactory.createResource(NS + "EachOf-unvisited"); // 5
    public final static Resource tEmpty = ResourceFactory.createResource(NS + "Empty"); // 11
    public final static Resource tErrorReport = ResourceFactory.createResource(NS + "ErrorReport"); // 2
    public final static Resource tExhaustive = ResourceFactory.createResource(NS + "Exhaustive"); // 25
    public final static Resource tExtends = ResourceFactory.createResource(NS + "Extends"); // 61
    public final static Resource tExternalSemanticAction = ResourceFactory.createResource(NS + "ExternalSemanticAction"); // 4
    public final static Resource tExternalShape = ResourceFactory.createResource(NS + "ExternalShape"); // 4
    public final static Resource tExtra = ResourceFactory.createResource(NS + "Extra"); // 24
    public final static Resource tFocusConstraint = ResourceFactory.createResource(NS + "FocusConstraint"); // 47
    public final static Resource tFractionDigitsFacet = ResourceFactory.createResource(NS + "FractionDigitsFacet"); // 18
    public final static Resource tImport = ResourceFactory.createResource(NS + "Import"); // 33
    public final static Resource tInclude = ResourceFactory.createResource(NS + "Include"); // 17
    public final static Resource tIriEquivalence = ResourceFactory.createResource(NS + "IriEquivalence"); // 15
    public final static Resource tLanguageTagEquivalence = ResourceFactory.createResource(NS + "LanguageTagEquivalence"); // 5
    public final static Resource tLengthFacet = ResourceFactory.createResource(NS + "LengthFacet"); // 101
    public final static Resource tLexicalBNode = ResourceFactory.createResource(NS + "LexicalBNode"); // 42
    public final static Resource tMissedMatchables = ResourceFactory.createResource(NS + "MissedMatchables"); // 2
    public final static Resource tMultiExtends = ResourceFactory.createResource(NS + "MultiExtends"); // 7
    public final static Resource tNodeKind = ResourceFactory.createResource(NS + "NodeKind"); // 18
    public final static Resource tNonDotCardinality = ResourceFactory.createResource(NS + "NonDotCardinality"); // 1
    public final static Resource tNotValueExpression = ResourceFactory.createResource(NS + "NotValueExpression"); // 12
    public final static Resource tNumericEquivalence = ResourceFactory.createResource(NS + "NumericEquivalence"); // 43
    public final static Resource tOneOf = ResourceFactory.createResource(NS + "OneOf"); // 15
    public final static Resource tOrderedSemanticActions = ResourceFactory.createResource(NS + "OrderedSemanticActions"); // 1
    public final static Resource tOrValueExpression = ResourceFactory.createResource(NS + "OrValueExpression"); // 46
    public final static Resource tOutsideBMP = ResourceFactory.createResource(NS + "OutsideBMP"); // 22
    public final static Resource tPaternFacet = ResourceFactory.createResource(NS + "PaternFacet"); // 49
    public final static Resource tRecursiveData = ResourceFactory.createResource(NS + "RecursiveData"); // 2
    public final static Resource trelativeIRI = ResourceFactory.createResource(NS + "relativeIRI"); // 2
    public final static Resource tRefBNodeShapeLabel = ResourceFactory.createResource(NS + "RefBNodeShapeLabel"); // 2
    public final static Resource tRepeatedGroup = ResourceFactory.createResource(NS + "RepeatedGroup"); // 2
    public final static Resource tRepeatedOneOf = ResourceFactory.createResource(NS + "RepeatedOneOf"); // 17
    public final static Resource tSemanticAction = ResourceFactory.createResource(NS + "SemanticAction"); // 23
    public final static Resource tShapeMap = ResourceFactory.createResource(NS + "ShapeMap"); // 3
    public final static Resource tShapeReference = ResourceFactory.createResource(NS + "ShapeReference"); // 24
    public final static Resource tStart = ResourceFactory.createResource(NS + "Start"); // 11
    public final static Resource tStem = ResourceFactory.createResource(NS + "Stem"); // 82
    public final static Resource tToldBNode = ResourceFactory.createResource(NS + "ToldBNode"); // 20
    public final static Resource tTotalDigitsFacet = ResourceFactory.createResource(NS + "TotalDigitsFacet"); // 27
    public final static Resource tTriplePattern = ResourceFactory.createResource(NS + "TriplePattern"); // 45
    public final static Resource tUnsatisfiable = ResourceFactory.createResource(NS + "Unsatisfiable"); // 1
    public final static Resource tValidLexicalForm = ResourceFactory.createResource(NS + "ValidLexicalForm"); // 121
    public final static Resource tValueReference = ResourceFactory.createResource(NS + "ValueReference"); // 57
    public final static Resource tValueSet = ResourceFactory.createResource(NS + "ValueSet"); // 122
    public final static Resource tVapidExtra = ResourceFactory.createResource(NS + "VapidExtra"); // 8
    public final static Resource tWildcard = ResourceFactory.createResource(NS + "Wildcard"); // 1

    // Properties
    public final static Property shape = ResourceFactory.createProperty(NS + "shape");
    public final static Property data = ResourceFactory.createProperty(NS + "data");
    public final static Property schema = ResourceFactory.createProperty(NS + "schema");
    public final static Property focus = ResourceFactory.createProperty(NS + "focus");

    public final static Property trait = ResourceFactory.createProperty(NS + "trait");
    public final static Property map = ResourceFactory.createProperty(NS + "map");
    public final static Property shapeExterns = ResourceFactory.createProperty(NS + "shapeExterns");
    public final static Property semActs = ResourceFactory.createProperty(NS + "semActs");

    public static final String NS_SX = "https://shexspec.github.io/shexTest/ns#";
    public final static Property sx_shex = ResourceFactory.createProperty(NS_SX + "shex");
    public final static Property sx_json = ResourceFactory.createProperty(NS_SX + "json");
    public final static Property sx_ttl  = ResourceFactory.createProperty(NS_SX + "ttl");


    /** <p>Structure describing invocation of the Test semantic action</p> */
    public static final Property extensionResults = ResourceFactory.createProperty( "http://www.w3.org/2001/sw/DataAccess/tests/test-manifest#extensionResults" );
    /** <p>The extension name of a semantic action</p> */
    public static final Property extension = ResourceFactory.createProperty( "http://www.w3.org/2001/sw/DataAccess/tests/test-manifest#extension" );
    /** <p>The text output of the Test semantic action</p> */
    public static final Property prints = ResourceFactory.createProperty( "http://www.w3.org/2001/sw/DataAccess/tests/test-manifest#prints" );

}
