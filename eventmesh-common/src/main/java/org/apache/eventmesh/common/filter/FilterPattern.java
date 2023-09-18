package org.apache.eventmesh.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterPattern {
    Configuration jsonPathConf = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .build();

    private List<PatternEntry> specAttrPatternList = new ArrayList<>();
    private List<PatternEntry> extensionsAttrPatternList = new ArrayList<>();
    private List<PatternEntry> dataPatternList = new ArrayList<>();

    /**
     * Evaluates the provided json string whether matches the event pattern
     *
     * @param jsonData the specific data in json format
     * @return true if jsonData matches rule, false otherwise
     */
    public boolean evaluateData(String jsonData) {
        if (Strings.isNullOrEmpty(jsonData)) {
            return false;
        }
        final ReadContext jsonContext = JsonPath.using(jsonPathConf)
                .parse(jsonData);

        for (final PatternEntry patternEntry : dataPatternList) {
            JsonNode jsonElement = null;
            try {
                jsonElement = jsonContext.read(patternEntry.getPatternPath());
            } catch (PathNotFoundException ignored) {
            }

//            if (!patternEntry.match(jsonElement)) {
//                return false;
//            }
        }
        return true;
    }

    /**
     * Evaluates the provided spec attributes whether matches the event pattern
     *
     * @param specAttrs the provided spec attributes in map format
     * @return true if matches the pattern, false otherwise
     */
    public boolean evaluateSpecAttr(Map<String, JsonNode> specAttrs) {
        return evaluateAttrMap(specAttrs, specAttrPatternList);
    }

    /**
     * Evaluates the provided extensions attributes whether matches the event pattern
     *
     * @param extensionsAttrs the provided extensions attributes in map format
     * @return true if matches the pattern, false otherwise
     */
    public boolean evaluateExtensionAttr(Map<String, JsonNode> extensionsAttrs) {
        return evaluateAttrMap(extensionsAttrs, extensionsAttrPatternList);
    }


    /**
     * Tests whether the evaluator has data patterns
     *
     * @return true if {@link #dataPatternList} is not empty, false otherwise
     */
    public boolean hasDataPattern() {
        return !this.dataPatternList.isEmpty();
    }

    /**
     * Tests whether the evaluator has no data patter
     *
     * @return true if the patterns of evaluator is empty, false otherwise
     */
    public boolean isEmpty() {
        return this.extensionsAttrPatternList.isEmpty() && this.specAttrPatternList.isEmpty()
                && this.dataPatternList.isEmpty();
    }

    public void addSpecAttrPatternEntry(PatternEntry patternEntry) {
        this.specAttrPatternList.add(patternEntry);
    }

    public void addExtensionsAttrPatternEntry(PatternEntry patternEntry) {
        this.extensionsAttrPatternList.add(patternEntry);
    }

    public void addDataPatternEntry(PatternEntry patternEntry) {
        this.dataPatternList.add(patternEntry);
    }

    private boolean evaluateAttrMap(Map<String, JsonNode> attr, List<PatternEntry> ruleEntries) {
        for (final PatternEntry patternEntry : ruleEntries) {
            JsonNode val = attr.get(patternEntry.getPatternName());
//            if (!patternEntry.match(val)) {
//                return false;
//            }
        }
        return true;
    }

    // Below three getters only for test

    List<PatternEntry> getSpecAttrPatternList() {
        return specAttrPatternList;
    }

    List<PatternEntry> getExtensionsAttrPatternList() {
        return extensionsAttrPatternList;
    }

    List<PatternEntry> getDataPatternList() {
        return dataPatternList;
    }
}
