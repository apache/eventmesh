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

package org.apache.eventmesh.common.utils;

import org.apache.commons.lang3.math.NumberUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RandomStringUtilsTest {

    @Test
    public void testGenerateNum() {
        String result = RandomStringUtils.generateNum(2);
        Assertions.assertTrue(NumberUtils.isDigits(result));
        Assertions.assertEquals(2, result.length());
    }

    @Test
    public void testGenerateUUID() {
        String result = RandomStringUtils.generateUUID();
        Assertions.assertTrue(result.matches("^\\w{8}-\\w{4}-\\w{4}-\\w{4}-\\w{12}$"));
    }

}
