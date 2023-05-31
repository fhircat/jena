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

import org.apache.jena.shex.ShexSchema;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

public class ShexJsonLdSerializerTest {

    @Test
    public void testSerialize() {
        InputStream inputStream = ShexJsonldDeserializerTest.class.getResourceAsStream("/shexj_schema1.json");
        ShexJsonldDeserializer shexJson = new ShexJsonldDeserializer();
        ShexSchema schema = null;
        try {
            schema = shexJson.deserializeSchema(inputStream);
            assertNotNull(schema);
        } catch(Exception e) {
            fail(e.getMessage());
        }
        ShexJsonLdSerializer serializer = new ShexJsonLdSerializer();
        serializer.serialize(schema);
    }

}
