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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/**
 * Test class for {@link ArchitectureRules}.
 *
 * <p>Each rule is asserted in WARN mode: violations are logged via
 * {@code System.out.println} but the test passes. From 1.14.0 the
 * {@code *_warn} test methods will be replaced with hard
 * {@code rule.check(classes)} assertions that fail on violation.
 */
class ArchitectureRulesTest {

    private final JavaClasses classes = ArchitectureRules.loadProductionClasses();

    @Test
    void ruleInternalHidden_warn() {
        EvaluationResult r = ArchitectureRules.ruleInternalHidden.evaluate(classes);
        System.out.println("ArchitectureRules.ruleInternalHidden violations:\n" + r.getFailureReport());
    }

    @Test
    void ruleHttpProtocolHidden_warn() {
        EvaluationResult r = ArchitectureRules.ruleHttpProtocolHidden.evaluate(classes);
        System.out.println("ArchitectureRules.ruleHttpProtocolHidden violations:\n" + r.getFailureReport());
    }

    @Test
    void ruleGrpcProtocolHidden_warn() {
        EvaluationResult r = ArchitectureRules.ruleGrpcProtocolHidden.evaluate(classes);
        System.out.println("ArchitectureRules.ruleGrpcProtocolHidden violations:\n" + r.getFailureReport());
    }

    @Test
    void ruleTcpProtocolHidden_warn() {
        EvaluationResult r = ArchitectureRules.ruleTcpProtocolHidden.evaluate(classes);
        System.out.println("ArchitectureRules.ruleTcpProtocolHidden violations:\n" + r.getFailureReport());
    }

    @Test
    void ruleOldUtilsRenamed_warn() {
        EvaluationResult r = ArchitectureRules.ruleOldUtilsRenamed.evaluate(classes);
        System.out.println("ArchitectureRules.ruleOldUtilsRenamed violations:\n" + r.getFailureReport());
    }
}
