package org.apache.eventmesh.common.transform;

import java.util.List;

public interface Parser {

        List<Variable> process(String json);


}
