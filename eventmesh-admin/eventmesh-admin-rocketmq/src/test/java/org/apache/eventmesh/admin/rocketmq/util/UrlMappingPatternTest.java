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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

public class UrlMappingPatternTest {

    private static final String TEST_URL_MAPPING_PATTERN = "/test/{param1}/path/{param2}";

    private TestUrlMappingPattern urlMappingPattern;

    @Before
    public void setUp() {
        urlMappingPattern = new TestUrlMappingPattern(TEST_URL_MAPPING_PATTERN);
    }

    @Test
    public void testGetMappingPattern() {
        assertEquals("/test/{param1}/path/{param2}", urlMappingPattern.getMappingPattern());
    }

    @Test
    public void testExtractPathParameterValues() {
        String testUrl = "/test/123/path/456";
        Matcher mockMatcher = mock(Matcher.class);
        when(mockMatcher.matches()).thenReturn(true);
        when(mockMatcher.groupCount()).thenReturn(2);
        when(mockMatcher.group(1)).thenReturn("123");
        when(mockMatcher.group(2)).thenReturn("456");
        when(urlMappingPattern.compiledUrlMappingPattern.matcher(testUrl)).thenReturn(mockMatcher);
        Map<String, String> parameterValues = urlMappingPattern.extractPathParameterValues(testUrl);
        assertEquals("123", parameterValues.get("param1"));
        assertEquals("456", parameterValues.get("param2"));
    }

    @Test
    public void testExtractPathParameterValuesWithNoMatch() {
        String testUrl = "/test/123/456";
        Matcher mockMatcher = mock(Matcher.class);
        when(mockMatcher.matches()).thenReturn(false);
        when(urlMappingPattern.compiledUrlMappingPattern.matcher(testUrl)).thenReturn(mockMatcher);
        assertNull(urlMappingPattern.extractPathParameterValues(testUrl));
    }

    @Test
    public void testMatches() {
        String testUrl = "/test/123/path/456";
        Matcher mockMatcher = mock(Matcher.class);
        when(mockMatcher.matches()).thenReturn(true);
        when(urlMappingPattern.compiledUrlMappingPattern.matcher(testUrl)).thenReturn(mockMatcher);
        assertTrue(urlMappingPattern.matches(testUrl));
    }

    @Test
    public void testGetParamNames() {
        assertEquals(2, urlMappingPattern.getParamNames().size());
        assertEquals("param1", urlMappingPattern.getParamNames().get(0));
        assertEquals("param2", urlMappingPattern.getParamNames().get(1));
    }

    @Test
    public void testCompile() {
        //TODO : Fix me to test the method compile(). It is better using Mockito not PowerMockito.
    }

    class TestUrlMappingPattern extends UrlMappingPattern {

        private Pattern compiledUrlMappingPattern;

        public TestUrlMappingPattern(String pattern) {
            super(pattern);
            compiledUrlMappingPattern = mock(Pattern.class);
        }
    }
}
