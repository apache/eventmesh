/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.admin.rocketmq.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Represents a URL mapping pattern for routing purposes.
 * The pattern can include variable path parameters or query strings.
 */

@UtilityClass
public class UrlMappingPattern {

    private static final String URL_PARAMETER_REGEX = "\\{(\\w*?)\\}";

    private static final String URL_PARAMETER_MATCH_REGEX =
        "\\([%\\\\w-.\\\\~!\\$&'\\\\(\\\\)\\\\*\\\\+,;=:\\\\[\\\\]@]+?\\)";

    private static final Pattern URL_PARAMETER_PATTERN = Pattern.compile(URL_PARAMETER_REGEX);

    private static final String URL_FORMAT_REGEX = "(?:\\.\\{format\\})$";

    private static final String URL_FORMAT_MATCH_REGEX = "(?:\\\\.\\([\\\\w%]+?\\))?";

    private static final String URL_QUERY_STRING_REGEX = "(?:\\?.*?)?$";

    private String urlMappingPattern;

    private Pattern compiledUrlMappingPattern;

    private List<String> paramNames = new ArrayList<>();

    public UrlMappingPattern(String pattern) {
        this.urlMappingPattern = pattern;
        compile();
    }

    public String getMappingPattern() {
        return urlMappingPattern.replaceFirst(URL_FORMAT_REGEX, "");
    }

    /**
     * Extracts path parameters from the given URL and returns a {@link Map} of parameter names to values.
     *
     * @param url the URL from which to extract path parameters
     * @return a {@link Map} containing path parameter names and their corresponding values,
     *         or null if the URL does not match the defined URL mapping pattern
     */
    public Map<String, String> extractPathParameterValues(String url) {
        Matcher matcher = compiledUrlMappingPattern.matcher(url);
        if (matcher.matches()) {
            return extractParameters(matcher);
        }
        return null;
    }

    public boolean matches(String url) {
        return (extractPathParameterValues(url) != null);
    }

    public void compile() {
        acquireParamNames();
        String parsedPattern = urlMappingPattern.replaceFirst(URL_FORMAT_REGEX, URL_FORMAT_MATCH_REGEX);
        parsedPattern = parsedPattern.replaceAll(URL_PARAMETER_REGEX, URL_PARAMETER_MATCH_REGEX);
        this.compiledUrlMappingPattern = Pattern.compile(parsedPattern + URL_QUERY_STRING_REGEX);
    }

    private void acquireParamNames() {
        Matcher m = URL_PARAMETER_PATTERN.matcher(urlMappingPattern);
        while (m.find()) {
            paramNames.add(m.group(1));
        }
    }

    /**
     * Extracts parameters from the provided {@link Matcher} object and returns a {@link Map} of parameter names to values.
     *
     * @param matcher the Matcher object used to match and capture parameter values
     * @return a {@link Map} containing parameter names and their corresponding values
     */
    private Map<String, String> extractParameters(Matcher matcher) {
        Map<String, String> values = new HashMap<>((int) (matcher.groupCount() / 0.75f + 1));
        for (int i = 0; i < matcher.groupCount(); i++) {
            String value = matcher.group(i + 1);

            if (value != null) {
                values.put(paramNames.get(i), value);
            }
        }
        return values;
    }

    public List<String> getParamNames() {
        return Collections.unmodifiableList(paramNames);
    }
}
