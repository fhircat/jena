package org.apache.jena.shex.manifest;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shex.*;
import org.apache.jena.sparql.graph.GraphFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class ValidationParms {
    public Graph graph;
    public ShexSchema schema;
    public ShapeMap smap;

    public ValidationParms(ManifestEntry manifestEntry, String base) {
        schema = Shex.schemaFromString(manifestEntry.getSchema(), base);

        InputStream dataInputStream = new ByteArrayInputStream(manifestEntry.getData().getBytes());
        graph = GraphFactory.createDefaultGraph();
        RDFDataMgr.read(graph, dataInputStream, base, Lang.TTL);

        InputStream queryMap = new ByteArrayInputStream(manifestEntry.getQueryMap().getBytes());
        smap = Shex.readShapeMap(queryMap, base);
    }

    public ShexReport validate() {
        return ShexValidator.get().validate(graph, schema, smap);
    }
}
