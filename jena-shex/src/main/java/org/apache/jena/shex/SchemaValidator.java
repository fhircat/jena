package org.apache.jena.shex;

/** Allows to validate against the shapes of a schema.
 * The schema should not be modified after it is passed to the validator.
 */
public class SchemaValidator {

    private ShexSchema schema;

    public SchemaValidator(ShexSchema schema) {
        this.schema = schema;
    }

    private void initialize() {
        // TODO Check stratification, compute extension hierarchy and so on.

    }

}
