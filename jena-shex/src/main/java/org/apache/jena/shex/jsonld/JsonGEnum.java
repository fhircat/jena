package org.apache.jena.shex.jsonld;

public enum JsonGEnum {
    TYPE("type"),
    LANGUAGE("language"),
    LANGUAGE_TAG("languageTag"),
    EXCLUSIONS("exclusions"),
    STEM("stem"),
    WILDCARD("Wildcard"),
    IMPORT("import"),
    ABSTRACT("abstract"),
    SCHEMA("Schema"),
    START("start"),
    SHAPES("shapes"),
    ID("id"),
    SHAPE_EXPRESSION("shapeExpr"),
    SHAPE_EXPRESSIONS("shapeExprs"),
    RESTRICTS("restricts"),

    SHAPE_DECL("ShapeDecl"),
    SHAPE("Shape"),
    SHAPE_OR("ShapeOr"),
    SHAPE_AND("ShapeAnd"),
    SHAPE_NOT("ShapeNot"),
    SHAPE_EXTERNAL("ShapeExternal"),
    EXPRESSION("expression"),
    EXPRESSIONS("expressions"),
    TRIPLE_CONSTRAINT("TripleConstraint"),
    PREDICATE("predicate"),
    VALUE_EXPRESSION("valueExpr"),
    NODE_CONSTRAINT("NodeConstraint"),
    VALUES("values"),
    VALUE("value"),
    EACH_OF("EachOf"),
    ONE_OF("OneOf"),
    CARDINALITY_MIN("min"),
    CARDINALITY_MAX("max"),
    DATATYPE("datatype"),
    SEM_ACTS("semActs"),
    START_ACTS("startActs"),
    NAME("name"),
    CODE("code");

    private String label;

    private JsonGEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
