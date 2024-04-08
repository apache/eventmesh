package org.apache.eventmesh.connector.chatgpt.source.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatGPTRequestDTO {

    private String source;

    private String subject;

    @JsonProperty("datacontenttype")
    private String dataContentType;

    private String type;

    private String prompt;

}
