package org.apache.eventmesh.store.api.openschema.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompatibilityRequest {
    private String compatibility;

    @JsonProperty("compatibility")
    public String getCompatibility() {
        return compatibility;
    }

    @JsonProperty("compatibility")
    public void setCompatibility(String compatibility) {
        this.compatibility = compatibility;
    }
}
