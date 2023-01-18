package org.apache.jena.shex.manifest.yaml;

import org.apache.jena.shex.manifest.Manifest;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.util.List;

public abstract class AbstractManifestConstructor<T> extends Constructor {
    private final Class<T> clazz;

    public AbstractManifestConstructor(final Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    protected Object constructObject(final Node node) {
        if (node instanceof SequenceNode && isRootNode(node)) {
            ((SequenceNode) node).setListType(clazz);
            List<T> entries = (List<T>) super.constructObject(node);
            return constructManifest(entries);
        } else {
            return super.constructObject(node);
        }
    }

    private boolean isRootNode(final Node node) {
        return node.getStartMark().getIndex() == 0;
    }

    protected abstract Manifest constructManifest(List<T> entries);
}
