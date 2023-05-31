package org.apache.jena.shex.jsonld;

import org.apache.jena.shex.ShexSchema;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

public class ShexJsonldDeserializerTest {

    @Test
    public void testSchemaDerialization1() {
        InputStream inputStream = ShexJsonldDeserializerTest.class.getResourceAsStream("/shexj_schema1.json");
        ShexJsonldDeserializer shexJson = new ShexJsonldDeserializer();
        try {
            ShexSchema schema = shexJson.deserializeSchema(inputStream);
            assertNotNull(schema);
            assertEquals("http://shex.io/webapps/shex.js/examples/ObservationShape", schema.getStart().getLabel().toString());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testSchemaDerialization2() {
        InputStream inputStream = ShexJsonldDeserializerTest.class.getResourceAsStream("/shexj_schema2.json");
        ShexJsonldDeserializer shexJson = new ShexJsonldDeserializer();
        try {
            ShexSchema schema = shexJson.deserializeSchema(inputStream);
            assertNotNull(schema);
            assertEquals("http://ex.example/S1", schema.getStart().getLabel().toString());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testSchemaDerialization3() {
        InputStream inputStream = ShexJsonldDeserializerTest.class.getResourceAsStream("/shexj_schema3.json");
        ShexJsonldDeserializer shexJson = new ShexJsonldDeserializer();
        try {
            ShexSchema schema = shexJson.deserializeSchema(inputStream);
            assertNotNull(schema);
            assertEquals("http://all.example/S3", schema.getStart().getLabel().toString());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

}
