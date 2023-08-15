package org.apache.eventmesh.common.transform;

import com.google.common.collect.Lists;
import org.apache.eventmesh.common.exception.EventMeshException;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public interface Transform {

    /**
     * transform main function
     *
     * @param json
     * @returnw
     * @throws EventMeshException
     */
    String process(String json) throws EventMeshException;


    default List<Variable> match(String json, Parser parser){
        if(json == null){
            return new ArrayList<>();
        }
        return parser.process(json);
    }


    default String transform(List<Variable> variableList, Template template) throws EventMeshException{
        if (template == null) {
            return "";
        }
        return template.substitute(variableList);
    }
}
