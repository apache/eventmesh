package org.apache.eventmesh.common.transform;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Variable {

    private String name;
    private String jsonPath;

}
