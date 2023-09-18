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

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

public class ReflectUtilsTest {

    public static class TestParent {

        private String age;

        public String tel;

    }

    public static class TestObj extends TestParent {

        private String name;

        private String password;
    }

    @Test
    public void testLookUpFieldByParentClass() {
        Field fieldName = ReflectUtils.lookUpFieldByParentClass(TestObj.class, "name");
        Field fieldAge = ReflectUtils.lookUpFieldByParentClass(TestObj.class, "age");
        Field fieldTel = ReflectUtils.lookUpFieldByParentClass(TestObj.class, "tel");
        Assert.assertNull(fieldName);
        Assert.assertNull(fieldAge);
        Assert.assertNotNull(fieldTel);
        Assert.assertEquals("tel", fieldTel.getName());
    }

}
