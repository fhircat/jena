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

package org.apache.jena.shex.jsonld;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shex.expressions.Cardinality;
import org.apache.jena.shex.expressions.ValueSetItem;
import org.apache.jena.shex.expressions.ValueSetRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShexJsonldDeserializationUtils {

    public static String getStartJsonField(Map map) {
        return (String) map.get(JsonGEnum.START.getLabel());
    }

    public static String getTypeJsonField(Map map) {
        return (String) map.get(JsonGEnum.TYPE.getLabel());
    }

    public static String getIdJsonField(Map map) {
        return (String) map.get(JsonGEnum.ID.getLabel());
    }

    public static String getNameJsonField(Map map) {
        return (String) map.get(JsonGEnum.NAME.getLabel());
    }

    public static String getCodeJsonField(Map map) {
        return (String) map.get(JsonGEnum.CODE.getLabel());
    }

    public static String getPredicateJsonField(Map map) {
        return (String) map.get(JsonGEnum.PREDICATE.getLabel());
    }

    public static Integer getMinJsonField(Map map) {
        return (Integer) map.get(JsonGEnum.CARDINALITY_MIN.getLabel());
    }

    public static Integer getMaxJsonField(Map map) {
        return (Integer) map.get(JsonGEnum.CARDINALITY_MAX.getLabel());
    }

    public static String getDatatypeJsonField(Map map) {
        return (String) map.get(JsonGEnum.DATATYPE.getLabel());
    }

    public static Boolean getAbstractJsonField(Map map) {
        Boolean isAbstract = (Boolean) map.get(JsonGEnum.ABSTRACT.getLabel());
        return (isAbstract == null) ? false : isAbstract;
    }

    public static Object getValueExpressionJsonObject(Map map) {
        return map.get(JsonGEnum.VALUE_EXPRESSION.getLabel());
    }

    public static List<Map> getShapesJsonObject(Map map) {
        return (List<Map>) map.get(JsonGEnum.SHAPES.getLabel());
    }

    public static List<Map> getValuesJsonArray(Map map) {
        return (List<Map>) map.get(JsonGEnum.VALUES.getLabel());
    }

    public static ValueSetRange getValueJsonObject(Model model, Map map) { //TODO I recommend changing the implementation of the NodeConstraint model and possibly the JSON-LD. It is not intuitive.
        ValueSetRange valueSetRange = null;

        //Build an object literal
        String value = (String) map.get(JsonGEnum.VALUE.getLabel());
        String language = (String) map.get(JsonGEnum.LANGUAGE.getLabel());
        String type = getTypeJsonField(map);

        Object stem = map.get(JsonGEnum.STEM.getLabel());
        String languageTag = (String) map.get(JsonGEnum.LANGUAGE_TAG.getLabel());
        List<Object> exclusions = (List<Object>) map.get(JsonGEnum.EXCLUSIONS.getLabel());

        if (value != null) {
            Node literal = null;
            if (type != null) {
                literal = ShexJsonldDeserializationUtils.createTypedLiteral(model, value, type);
            } else {
                literal = ShexJsonldDeserializationUtils.createLiteral(model, value, language);
            }
            valueSetRange = new ValueSetRange(null, null, literal, false);
        } else if (stem != null) {
            if (stem instanceof String) {
                valueSetRange = new ValueSetRange((String) stem, null, null, true);
            } else {
                Map wildcard = (Map) stem;
                String stemType = getTypeJsonField(wildcard);
                if (stemType.equalsIgnoreCase(JsonGEnum.WILDCARD.getLabel())) {
                    valueSetRange = new ValueSetRange(".", null, null, true); //TODO Check with Eric. I am pretty sure I am doing this wrong. Format does not seem to match JsonG {type: "Wildcard"} != {}
                } else {
                    throw new RuntimeException("Unrecognized type " + type);
                }
            }

            if (exclusions != null) {
                valueSetRange.setExclusions(getExclusions(exclusions));
            }
        } else if (languageTag != null) {
            valueSetRange = new ValueSetRange(null, languageTag, null, false);
            if (exclusions != null) {
                valueSetRange.setExclusions(getExclusions(exclusions));
            }
        } else {
            throw new RuntimeException("Unrecognized value set range object " + map);
        }
        return valueSetRange;
    }

    private static List<ValueSetItem> getExclusions(List<Object> exclusions) {
        List<ValueSetItem> exclusionList = new ArrayList<>();
        for (Object exclusion : exclusions) {
            if (exclusion instanceof String) {
                exclusionList.add(new ValueSetItem((String) exclusion, null, null, false));
            } else { //It is a stem
                Map exclusionMap = (Map) exclusion;
                String exclusionStem = (String) exclusionMap.get(JsonGEnum.STEM.getLabel());
                exclusionList.add(new ValueSetItem(exclusionStem, null, null, true));
            }
        }
        return exclusionList;
    }

    public static Map getShapeExpressionJsonObject(Map map) {
        return (Map) map.get(JsonGEnum.SHAPE_EXPRESSION.getLabel());
    }

    public static List<Object> getShapeExpressionsJsonArray(Map map) {
        return (List<Object>) map.get(JsonGEnum.SHAPE_EXPRESSIONS.getLabel());
    }

    public static Object getExpressionJsonObject(Map map) {
        return map.get(JsonGEnum.EXPRESSION.getLabel());
    }

    public static List<Map> getExpressionsJsonArray(Map map) {
        return (ArrayList<Map>) map.get(JsonGEnum.EXPRESSIONS.getLabel());
    }

    public static List<String> getRestrictsJsonArray(Map map) {
        return (ArrayList<String>) map.get(JsonGEnum.RESTRICTS.getLabel());
    }

    public static List<String> getImportsJsonArray(Map map) {
        return (ArrayList<String>) map.get(JsonGEnum.IMPORT.getLabel());
    }

    public static List<Map> getSemActJsonArray(Map map) {
        return (ArrayList<Map>) map.get(JsonGEnum.SEM_ACTS.getLabel());
    }

    public static List<Map> getStartActsJsonArray(Map map) {
        return (ArrayList<Map>) map.get(JsonGEnum.START_ACTS.getLabel());
    }

    public static Cardinality handleCardinality(Map map) {
        Integer min = ShexJsonldDeserializationUtils.getMinJsonField(map);
        Integer max = ShexJsonldDeserializationUtils.getMaxJsonField(map);
        Cardinality cardinality = null;
        if (min != null && max != null) { //TODO ask Eric - do they always come in pairs - Handle case when min and max is 1 (elided)
            cardinality = new Cardinality(min, max >= 0 ? max : Cardinality.UNBOUNDED);
        }
        return cardinality;
    }

    /**
     * Returns a node for the given URI. If a node already exists in the model, it will be returned.
     * If the node does not already exist, it will be created first.
     * <p>
     * This method is used to convert a URI into a Node when required by the Jena API.
     *
     * @param uri The URI of the node to return.
     * @return
     */
    public static Node resolveOrCreateNode(Model model, String uri) {
        Resource resource = model.getResource(uri);
        if (resource == null) {
            resource = model.createResource(uri);
        }
        return resource.asNode();
    }

    public static Node createLiteral(Model model, String value, String language) {
        return model.createLiteral(value, language).asNode();
    }

    public static Node createTypedLiteral(Model model, String value, String type) {
        RDFDatatype rdfType = TypeMapper.getInstance().getSafeTypeByName(type);
        return model.createTypedLiteral(value, rdfType).asNode();
    }
}
