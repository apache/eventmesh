package org.apache.eventmesh.openschemaregistry.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubjectAndVersionResponse {
    public String subject;

    public int version;
}
