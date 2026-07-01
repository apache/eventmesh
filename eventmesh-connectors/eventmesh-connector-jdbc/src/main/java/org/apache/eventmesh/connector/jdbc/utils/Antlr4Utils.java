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

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Antlr4Utils {

    public static String getText(ParserRuleContext ctx) {
        return getText(ctx, ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex());
    }

    public static String getText(ParserRuleContext ctx, int start, int stop) {
        Interval interval = new Interval(start, stop);
        return ctx.getStart().getInputStream().getText(interval);
    }
}