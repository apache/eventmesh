package org.apache.eventmesh.common.transform;

import org.apache.eventmesh.common.exception.EventMeshException;

public class WholeTransform implements Transform{
    public String process(String inputData){
        return inputData;
    }


}