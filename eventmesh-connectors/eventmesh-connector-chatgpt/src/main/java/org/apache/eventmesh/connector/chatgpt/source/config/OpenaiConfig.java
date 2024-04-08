package org.apache.eventmesh.connector.chatgpt.source.config;


import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class OpenaiConfig {

    private String token;
    private String model;
    private long timeout;
    private Double temperature;
    private Integer maxTokens;
    private Boolean logprob;
    private Double topLogprobs;
    private Map<String, Integer> logitBias;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private String user;
    private List<String> stop;

}
