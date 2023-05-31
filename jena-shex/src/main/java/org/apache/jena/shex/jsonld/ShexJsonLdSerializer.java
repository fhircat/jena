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

import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.shex.ShexSchema;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.HashMap;
import java.util.Map;

public class ShexJsonLdSerializer {

    public static final String SHEX_CONTEXT = "@context";
    public static final String SHEX_JSON_LD_CONTEXT = "http://www.w3.org/ns/shex.jsonld";

    public void serialize(ShexSchema shexSchema) {
        serialize(shexSchema, null, null);
    }

    public void serialize(ShexSchema shexSchema, JsonLdOptions options, Map context) {
        try {
            if (options == null) {
                options = new JsonLdOptions();
            }

            if (context == null || context.isEmpty() || !context.containsKey(SHEX_CONTEXT)) {
                context = createShexJsonLdContext();
            }


            Map schemaMap = serializeSchema(shexSchema);

            Object compact = JsonLdProcessor.compact(schemaMap, context, options);
// Print out the result (or don't, it's your call!)
            System.out.println(JsonUtils.toPrettyString(compact));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Map createShexJsonLdContext() {
        return Map.of(SHEX_CONTEXT, SHEX_JSON_LD_CONTEXT);
    }

    public static Map createRootShexJsonLdContext() {

        Map map = new HashMap();
        map.put(SHEX_CONTEXT, SHEX_JSON_LD_CONTEXT);

        return map;
    }

    protected Map serializeSchema(ShexSchema schema) {
        Map map = createRootShexJsonLdContext();
        map.put(JsonGEnum.START.getLabel(), schema.getStart().getLabel().getURI());
        enumerateAttributes(schema);
        return map;
    }

    /***************************************************************************
     * Helper methods
     ***************************************************************************/

    /**
     * Convenience method that handles either simple JSON attribute:
     * <ul>
     *     <li>If the attribute value is string, it will be used</li>
     *     <li>If the attribute value is a jena Node, the node label will be used</li>
     *     <li>If the attribute value is any other object, the toString() method will be invoked on that object</li>
     *     <li>If the attribute value is null, no entry will be added</li>
     * </ul>>
     *
     * @param map            The JSON object that will receive the new simple attribute
     * @param attributeName  The name of the attribute
     * @param attributeValue The value of the attribute
     */
    protected void addSimpleAttributeIfNotNull(Map map, JsonGEnum attributeName, Object attributeValue) {
        if (attributeValue != null) {
            if (attributeValue instanceof String) {
                map.put(attributeName.getLabel(), attributeValue);
            } else if (attributeValue instanceof Node) {
                Node attributeNodeValue = (Node) attributeValue;
                String attributeUriValue = attributeNodeValue.getURI();
            }
        }
    }

    protected void enumerateAttributes(Object o) {
        if (o == null) {
            return;
        }
        String className = o.getClass().getCanonicalName();
        if (className.startsWith("java.lang")) {//todo handle lists
            return;
        } else {
            System.out.println("PROCESSING " + className);
            try {
                Map<String, Object> classFields = new HashMap<>();
                Field[] fields = o.getClass().getDeclaredFields();
                for (Field field : fields) {
                    //System.out.println(field.getName() + ":" + field.getType());
                    try {
                        field.setAccessible(true);
                        if (field.get(o) != null) {
                            Object attr = field.get(o);
                            classFields.put(field.getName(), attr);
                            enumerateAttributes(attr);
                        }
                    } catch (InaccessibleObjectException ioe) {
                        System.out.println("Skipping field " + o.getClass() + "." + field.getName() + ". Field is not accessible");
                    }
                }
                classFields.forEach((k, v) -> {
                    //enumerateAttributes(v);
                });
            } catch (Exception e) {
                throw new RuntimeException("Error accessing fields: ", e);
            }
        }
    }
}
