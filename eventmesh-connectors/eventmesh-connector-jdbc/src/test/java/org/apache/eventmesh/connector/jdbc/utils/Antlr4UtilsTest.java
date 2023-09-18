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

package org.apache.eventmesh.connector.jdbc.utils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.junit.Test;

public class Antlr4UtilsTest {

    @Test
    public void testGetTextWithContext() {
        // Mock the ParserRuleContext and related objects
        ParserRuleContext context = mock(ParserRuleContext.class);
        Token startToken = mock(Token.class);
        Token stopToken = mock(Token.class);
        when(context.getStart()).thenReturn(startToken);
        when(context.getStop()).thenReturn(stopToken);
        when(startToken.getStartIndex()).thenReturn(0);
        when(stopToken.getStopIndex()).thenReturn(11);

        // Mock the InputStream
        CharStream inputStream = CharStreams.fromString("Hello, World!");
        when(startToken.getInputStream()).thenReturn(inputStream);

        // Call the method being tested
        String result = Antlr4Utils.getText(context);

        // Assertions
        assertEquals("Hello, World", result);
    }

    @Test
    public void testGetTextWithParameters() {
        // Create a mock ParserRuleContext
        ParserRuleContext context = mock(ParserRuleContext.class);

        // Mock the InputStream
        String st = "Hello, Universe!";
        CharStream inputStream = CharStreams.fromString(st);
        when(context.getStart()).thenReturn(mock(Token.class));
        when(context.getStop()).thenReturn(mock(Token.class));
        when(context.getStart().getInputStream()).thenReturn(inputStream);

        // Call the method being tested
        String result = Antlr4Utils.getText(context, 0, st.length() - 2);

        // Assertions
        assertEquals("Hello, Universe", result);
    }
}
