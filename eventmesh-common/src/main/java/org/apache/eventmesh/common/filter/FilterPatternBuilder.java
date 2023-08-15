package org.apache.eventmesh.common.filter;

import com.fasterxml.jackson.core.JsonParseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.utils.JsonPathUtils;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;


public class FilterPatternBuilder {
    private static ObjectMapper objectMapper = new ObjectMapper();
    public static FilterPattern build(String eventPattern) {
        FilterPattern patternEvaluator = new FilterPattern();
        if (JsonPathUtils.isEmptyJsonObject(eventPattern)) {
            return patternEvaluator;
        }


        JsonNode rootElement;

        try {
            rootElement = objectMapper.readTree(eventPattern);
        } catch (JsonParseException e) {
            throw new EventMeshException("Pattern Error: INVALID JSON STRING");
        } catch (IOException e) {
            throw new RuntimeException("Error reading JSON", e);
        }

        if (!rootElement.isObject()) {
            throw new EventMeshException("Pattern Error: NON SUPPORTED JSON");
        }

        Iterator<Map.Entry<String, JsonNode>> elements = rootElement.fields();

        while (elements.hasNext()) {
            Map.Entry<String, JsonNode> elementEntry = elements.next();
            String key = elementEntry.getKey();
            JsonNode jsonElement = elementEntry.getValue();


            if (!jsonElement.isArray()) {
                throw new EventMeshException(
                        "Pattern Errors: INVALID_PATTERN_VALUE" + jsonElement.asText());
            }

            PatternEntry patternEntry = parsePatternEntry(key, "$." + key, jsonElement);

            patternEvaluator.addSpecAttrPatternEntry(patternEntry);

            // Unrecognized rule key
        }


        return patternEvaluator;
    }



    private static PatternEntry parsePatternEntry(String ruleName, String rulePath, JsonNode  jsonElements) {
        if (jsonElements.size() == 0) {
            // Empty array
            throw new EventMeshException(
                    "Pattern Error: INVALID PATTERN VALUE" + jsonElements.toString());
        }

        PatternEntry patternEntry = new PatternEntry(ruleName, rulePath);

        for (final JsonNode element : jsonElements) {
            if (element.isNull() || element.isValueNode()) {
                // Equal condition
                EqualCondition equalCondition = new EqualCondition(element);
                patternEntry.addRuleCondition(equalCondition);
                continue;
            }

            if (element.isObject()) {
                // May throw RuleParseException when building RuleCondition
                patternEntry.addRuleCondition(parseCondition(element));
                continue;
            }
            // JsonArray isn't acceptable
            throw new EventMeshException("Pattern Error: NESTED PATTERN VALUE" + ruleName);
        }

        return patternEntry;
    }

    static RuleCondition parseCondition(JsonNode jsonNode) {
        // Complex condition
        final Iterator<Map.Entry<String, JsonNode>> iterator = jsonNode.fields();

        if (!iterator.hasNext()) {
            throw new EventMeshException("Pattern Error: INVALID PATTERN CONDITION");
        }

        final Map.Entry<String, JsonNode> elementEntry = iterator.next();
        String key = elementEntry.getKey();

        RuleConditionBuilder builder = ComplexConditionBuilders.getConditionBuilderByName(key);
        if (builder == null) {
            throw new EventMeshException("Pattern Error: UNRECOGNIZED PATTERN CONDITION");
        }

        return builder.build(elementEntry.getValue());
    }

}
