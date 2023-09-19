package org.apache.eventmesh.common.transform;

import org.apache.eventmesh.common.exception.EventMeshException;

class ConstantTransformer implements Transformer {

    private final String jsonpath;

    ConstantTransformer(String jsonpath) {
        this.jsonpath = jsonpath;
    }

    @Override
    public String transform(String json) throws EventMeshException {
        return this.jsonpath;
    }
}
