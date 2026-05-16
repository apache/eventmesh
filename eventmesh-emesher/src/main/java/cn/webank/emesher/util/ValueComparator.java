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

<<<<<<<< HEAD:eventmesh-emesher/src/main/java/cn/webank/emesher/util/ValueComparator.java
package cn.webank.emesher.util;
========
package com.webank.runtime.util;
>>>>>>>> 43bf39791 (event mesh project architecture adjustment):eventmesh-runtime/src/main/java/com/webank/runtime/util/ValueComparator.java

import java.util.Comparator;
import java.util.Map;

public class ValueComparator implements Comparator<Map.Entry<String, Integer>> {
    @Override
    public int compare(Map.Entry<String, Integer> x, Map.Entry<String, Integer> y) {
        return x.getValue().intValue() - y.getValue().intValue();
    }
}
