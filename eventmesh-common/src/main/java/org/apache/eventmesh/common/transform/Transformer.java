package org.apache.eventmesh.common.transform;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * EventMesh transformer interface, specified transformer implementation includes:
 * 1. Constant
 * 2. Original
 * 3. Template
 */
public interface Transformer {

    String transform(String json) throws JsonProcessingException;

}


