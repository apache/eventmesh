package org.apache.eventmesh.common.transform;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.text.StringSubstitutor;
import org.apache.eventmesh.common.exception.EventMeshException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class Template{

    private String template;


    public String substitute(List<Variable> variables) throws EventMeshException {


        Map<String, String> valuesMap = variables.stream()
                .filter(variable -> variable.getJsonPath() != null)
                .collect(Collectors.toMap(Variable::getName, Variable::getJsonPath));
        StringSubstitutor sub = new StringSubstitutor(valuesMap);

        return sub.replace(template);

    }
}