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
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit rules for issue #5298 / #5305.
 *
 * <p>Each rule documents which common sub-package is "internal" and which
 * downstream modules must not reach into it. Rules are exposed as
 * {@code public static} so the JUnit test class can pick them up and so
 * downstream contributors can extend the set.
 *
 * <p>Severity is WARN in 1.13.0; will be FAIL-on-violation from 1.14.0.
 */
public final class ArchitectureRules {

    private ArchitectureRules() {
    }

    public static JavaClasses loadProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath("eventmesh-common/build/classes/java/main")
                .importPath("eventmesh-runtime/build/classes/java/main")
                .importPath("eventmesh-spi/build/classes/java/main")
                .importPath("eventmesh-sdks/eventmesh-sdk-java/build/classes/java/main")
                .importPath("eventmesh-protocol-plugin/eventmesh-protocol-api/build/classes/java/main")
                .importPath("eventmesh-protocol-plugin/eventmesh-protocol-cloudevents/build/classes/java/main")
                .importPath("eventmesh-protocol-plugin/eventmesh-protocol-meshmessage/build/classes/java/main")
                .importPath("eventmesh-protocol-plugin/eventmesh-protocol-a2a/build/classes/java/main")
                .importPath("eventmesh-storage-plugin/eventmesh-storage-api/build/classes/java/main")
                .importPath("eventmesh-storage-plugin/eventmesh-storage-kafka/build/classes/java/main")
                .importPath("eventmesh-storage-plugin/eventmesh-storage-rocketmq/build/classes/java/main")
                .importPath("eventmesh-storage-plugin/eventmesh-storage-rocketmq5/build/classes/java/main")
                .importPath("eventmesh-connector-runtime/build/classes/java/main")
                .importPath("eventmesh-agent/build/classes/java/main")
                .importPath("eventmesh-examples/build/classes/java/main");
    }

    public static ArchRule ruleInternalHidden = noClasses()
            .that().resideOutsideOfPackage("org.apache.eventmesh.common..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.internal..");

    public static ArchRule ruleHttpProtocolHidden = noClasses()
            .that().resideOutsideOfPackage("org.apache.eventmesh.common.protocol.http..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.common..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.protocol.plugin.meshmessage..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.client..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.protocol.http..");

    public static ArchRule ruleGrpcProtocolHidden = noClasses()
            .that().resideOutsideOfPackage("org.apache.eventmesh.common.protocol.grpc..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.common..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.protocol.plugin.meshmessage..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.client..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.protocol.grpc..");

    public static ArchRule ruleTcpProtocolHidden = noClasses()
            .that().resideOutsideOfPackage("org.apache.eventmesh.common.protocol.tcp..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.common..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.protocol.plugin.meshmessage..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.client..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.runtime..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.protocol.tcp..");

    public static ArchRule ruleOldUtilsRenamed = noClasses()
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.utils..");
}
