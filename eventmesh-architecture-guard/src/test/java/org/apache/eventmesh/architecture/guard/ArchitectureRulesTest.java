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

package org.apache.eventmesh.architecture.guard;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;

/**
 * Test class for {@link ArchitectureRules}.
 *
 * <p>Each rule is asserted in FAIL mode: {@code rule.check(classes)}
 * throws {@code AssertionError} listing every violating location, so
 * any architecture violation breaks the build.
 *
 * <p>This module intentionally carries no SLF4J binding, so the
 * earlier WARN mode discarded every violation report (SLF4J falls back
 * to the NOP logger). FAIL mode does not depend on logging at all.
 */
class ArchitectureRulesTest {

    private final JavaClasses classes = ArchitectureRules.loadProductionClasses();

    @Test
    void ruleInternalHidden() {
        ArchitectureRules.ruleInternalHidden.check(classes);
    }

    @Test
    void ruleHttpProtocolHidden() {
        ArchitectureRules.ruleHttpProtocolHidden.check(classes);
    }

    @Test
    void ruleGrpcProtocolHidden() {
        ArchitectureRules.ruleGrpcProtocolHidden.check(classes);
    }

    @Test
    void ruleTcpProtocolHidden() {
        ArchitectureRules.ruleTcpProtocolHidden.check(classes);
    }

    @Test
    void ruleOldUtilsRenamed() {
        ArchitectureRules.ruleOldUtilsRenamed.check(classes);
    }

    @Test
    void ruleRuntimeTcpInternalHidden() {
        ArchitectureRules.ruleRuntimeTcpInternalHidden.check(classes);
    }

    @Test
    void ruleRuntimeTcpInternalNoReverse() {
        ArchitectureRules.ruleRuntimeTcpInternalNoReverse.check(classes);
    }

    @Test
    void ruleRuntimeEngineIsolatedFromInfra() {
        ArchitectureRules.ruleRuntimeEngineIsolatedFromInfra.check(classes);
    }

    @Test
    void ruleRuntimePushDoesNotImportCodec() {
        ArchitectureRules.ruleRuntimePushDoesNotImportCodec.check(classes);
    }

    @Test
    void ruleRuntimeSubscriptionStateIsolated() {
        ArchitectureRules.ruleRuntimeSubscriptionStateIsolated.check(classes);
    }
}
