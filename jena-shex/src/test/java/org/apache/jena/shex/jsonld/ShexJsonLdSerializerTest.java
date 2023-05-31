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
