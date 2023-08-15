package org.apache.eventmesh.common.transform;

import lombok.AllArgsConstructor;
import org.apache.eventmesh.common.exception.EventMeshException;

import java.util.List;

@AllArgsConstructor
public class ConstantTransform implements Transform{

    private String constant;
    @Override
    public String process(String json)  {
        return constant;
    }


}
