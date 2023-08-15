package org.apache.eventmesh.common.transform;

import org.apache.eventmesh.common.exception.EventMeshException;

import java.util.List;

public interface Template {

    /**
     * convert variables to template style
     * @param variables
     * @return
     * @throws EventMeshException
     */
    String substitute(List<Variable> variables) throws EventMeshException;
}
