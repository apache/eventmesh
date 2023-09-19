package org.apache.eventmesh.common.transform;

class OriginalTransformer implements Transformer {

    @Override
    public String transform(String json) {
        return json;
    }
}
