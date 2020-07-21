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

package cn.webank.defibus.common;

public class DeFiBusVersion {
    public static final int CURRENT_VERSION = Version.V1_0_0.ordinal();

    public static String getVersionDesc(int value) {
        try {
            DeFiBusVersion.Version v = DeFiBusVersion.Version.values()[value];
            return v.name();
        } catch (Exception e) {
        }

        return "HigherVersion";
    }

    public static DeFiBusVersion.Version value2Version(int value) {
        return DeFiBusVersion.Version.values()[value];
    }

    public enum Version {
        V1_0_0_SNAPSHOT,
        V1_0_0,
        V1_0_1_SNAPSHOT,
        V1_0_1,
        V1_1_0_SNAPSHOT,
        V1_1_0,
        V1_2_0_SNAPSHOT,
        V1_2_0,
        V1_3_0_SNAPSHOT,
        V1_3_0,
        V1_3_1_SNAPSHOT,
        V1_3_1,
        V1_3_2_SNAPSHOT,
        V1_3_2,
        V1_3_3_SNAPSHOT,
        V1_3_3,
        V1_3_4_SNAPSHOT,
        V1_3_4,
        V1_4_0_SNAPSHOT,
        V1_4_0,
        V1_4_1_SNAPSHOT,
        V1_4_1,
        V1_4_2_SNAPSHOT,
        V1_4_2,
        V1_4_3_SNAPSHOT,
        V1_4_3,
        V1_4_4_SNAPSHOT,
        V1_4_4,
        V1_4_5_SNAPSHOT,
        V1_4_5,
        V1_4_6_SNAPSHOT,
        V1_4_6,
        V1_4_7_SNAPSHOT,
        V1_4_7,
        V1_5_0_SNAPSHOT,
        V1_5_0,
        V1_5_1_SNAPSHOT,
        V1_5_1,
        V1_5_2_SNAPSHOT,
        V1_5_2,
        V1_6_0_SNAPSHOT,
        V1_6_0,
        V1_6_1_SNAPSHOT,
        V1_6_1,
        V1_6_2_SNAPSHOT,
        V1_6_2,
        V1_6_3_SNAPSHOT,
        V1_6_3,
        V1_6_4_SNAPSHOT,
        V1_6_4,
        V1_6_5_SNAPSHOT,
        V1_6_5,
        V1_7_0_SNAPSHOT,
        V1_7_0
    }

    public static void main(String[] args) {
        System.out.println(CURRENT_VERSION);
    }
}
