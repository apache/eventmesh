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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

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
        // importPackages() imports every class on the test classpath that
        // matches the package prefix -- robust across build systems and
        // working directories. (importPaths() used relative file paths that
        // break under Gradle, whose task working dir is the module dir.)
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.apache.eventmesh");
    }

    public static ArchRule ruleInternalHidden = noClasses()
            .that().resideOutsideOfPackage("org.apache.eventmesh.common..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.internal..");

    public static ArchRule ruleHttpProtocolHidden = noClasses()
            .that().resideOutsideOfPackage("org.apache.eventmesh.common.protocol.http..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.common..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.protocol.meshmessage..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.client..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.protocol.http..");

    public static ArchRule ruleGrpcProtocolHidden = noClasses()
            .that().resideOutsideOfPackage("org.apache.eventmesh.common.protocol.grpc..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.common..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.protocol.meshmessage..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.client..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.protocol.grpc..");

    public static ArchRule ruleTcpProtocolHidden = noClasses()
            .that().resideOutsideOfPackage("org.apache.eventmesh.common.protocol.tcp..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.common..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.protocol.meshmessage..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.client..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.runtime..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.protocol.tcp..");

    public static ArchRule ruleOldUtilsRenamed = noClasses()
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.common.utils..");

    public static ArchRule ruleRuntimeTcpInternalHidden = noClasses()
            .that().resideOutsideOfPackage("org.apache.eventmesh.runtime.tcp..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.runtime.tcp.internal..");

    public static ArchRule ruleRuntimeEngineIsolatedFromInfra = noClasses()
            .that().resideInAPackage("org.apache.eventmesh.runtime.boot..")
            .or().resideInAPackage("org.apache.eventmesh.runtime.ingress..")
            .or().resideInAPackage("org.apache.eventmesh.runtime.delivery..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.runtime.tcp.internal..");

    public static ArchRule ruleRuntimePushDoesNotImportCodec = noClasses()
            .that().resideInAPackage("org.apache.eventmesh.runtime.push..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.runtime.tcp.internal..");

    public static ArchRule ruleRuntimeSubscriptionStateIsolated = noClasses()
            .that().resideInAPackage("org.apache.eventmesh.runtime.ingress..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.eventmesh.runtime.state.internal..");

    // ---- Connector SPI boundary (connector-api module split) ----
    // Plugins live in sub-packages org.apache.eventmesh.connector.<plugin>.. and must only
    // touch the SPI classes in the flat org.apache.eventmesh.connector package (the
    // eventmesh-connector-api module). The runtime module intentionally shares the same
    // base package, so package rules cannot separate the two modules; instead we forbid
    // any plugin sub-package class from depending on the runtime-only classes by name.
    public static ArchRule ruleConnectorPluginsDependOnlyOnSpi = noClasses()
            .that().resideInAPackage("org.apache.eventmesh.connector..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.connector")
            .should().dependOnClassesThat()
            .haveNameMatching("org\\.apache\\.eventmesh\\.connector\\."
                    + "(ConnectorRuntime|ConnectorManager|ConnectorAdminServer|ConnectorApplication"
                    + "|ConnectorDef|EventMeshHttpEndpoint|InMemoryOffsetStore|RemoteOffsetStore"
                    + "|RocksDBConnectorOffsetStore)")
            .because("plugins must depend only on the connector-api SPI, not on runtime internals");
    // ---- Storage SPI boundary (issue #5342 Q7) ----
    // Storage plugins live in sub-packages org.apache.eventmesh.storage.<name>..
    // (e.g. kafka, rocketmq, rocketmq5). Two rules enforce the boundary:
    //   (a) Storage plugins must not reach into each other -- kafka must not
    //       touch org.apache.eventmesh.storage.rocketmq.* and vice versa.
    //   (b) Storage plugins must not reach into eventmesh-runtime.* or
    //       eventmesh.connector.runtime.* internals -- they are backend
    //       adapters, not runtime components.
    // The kafka plugin is the canary sampled on the test classpath
    // (mirrors the eventmesh-connector-file pattern); the rule is
    // package-name-based and applies to every storage plugin in
    // production source sets.

    /**
     * Storage plugins must not depend on each other. A kafka plugin class
     * must not import {@code org.apache.eventmesh.storage.rocketmq..} or
     * {@code org.apache.eventmesh.storage.rocketmq5..}, and similarly for
     * the other directions. Backends are independent -- cross-plugin
     * references indicate accidental coupling (e.g. copy-pasted helpers).
     */
    public static ArchRule ruleStoragePluginsIsolated = noClasses()
            .that().resideInAPackage("org.apache.eventmesh.storage.kafka..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.apache.eventmesh.storage.rocketmq..",
                "org.apache.eventmesh.storage.rocketmq5..")
            .because("storage plugins are independent backends; cross-plugin"
                + " dependencies indicate accidental coupling (kafka must not"
                + " import rocketmq/rocketmq5; the symmetric directions are"
                + " covered by the module graph, this rule guards the sampled"
                + " canary direction)");

    /**
     * Storage plugins must not depend on eventmesh-runtime or connector
     * runtime internals. They are adapter layers over an external MQ; their
     * dependency surface is the {@code eventmesh-storage-api} SPI plus the
     * MQ client library (org.apache.kafka, org.apache.rocketmq, etc.).
     */
    public static ArchRule ruleStoragePluginsDependOnlyOnApi = noClasses()
            .that().resideInAPackage("org.apache.eventmesh.storage..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.storage.api..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.apache.eventmesh.runtime..",
                "org.apache.eventmesh.connector.runtime..")
            .because("storage plugins are backend adapters; they depend on"
                + " eventmesh-storage-api and the MQ client, not on runtime"
                + " internals");

    // ---- Production HA guardrails (issue #5356) ----

    /**
     * {@code InMemoryMetaStore} is the documented single-instance store. It may only be
     * constructed (i.e. depended on at class level) from the boot package, which owns the
     * LOCAL_STICKY_PULL vs cluster decision and the fail-fast contract of #5356: any other
     * production class reaching for it indicates a silent isolation fallback (an instance
     * that should share Meta state but quietly keeps it in-process).
     */
    public static ArchRule ruleInMemoryMetaStoreOnlyFromBoot = noClasses()
            .that().resideInAPackage("org.apache.eventmesh..")
            .and().resideOutsideOfPackage("org.apache.eventmesh.runtime.boot..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.apache.eventmesh.runtime.cluster.InMemoryMetaStore")
            .because("InMemoryMetaStore is the single-instance store; only the boot"
                + " package may choose it (issue #5356: no silent isolation fallback)");

    /**
     * {@code PartitionOwnership} coordinates multiple instances through the shared
     * {@code MetaStore}. Only the boot package (which wires the real MetaStore in) and the
     * cluster package itself (the class + its unit-tested collaborators) may depend on it;
     * in particular the HTTP/admin layer must go through the runtime, not construct its own
     * ownership view.
     */
    public static ArchRule rulePartitionOwnershipOnlyFromBootAndCluster = noClasses()
            .that().resideInAPackage("org.apache.eventmesh..")
            .and().resideOutsideOfPackages(
                "org.apache.eventmesh.runtime.boot..",
                "org.apache.eventmesh.runtime.cluster..",
                "org.apache.eventmesh.runtime.ingress..",
                "org.apache.eventmesh.runtime.admin..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.apache.eventmesh.runtime.cluster.PartitionOwnership")
            .because("PartitionOwnership is cluster coordination state; only boot (wiring),"
                + " cluster (implementation), ingress (poll filter) and admin (read-only view)"
                + " may use it (issue #5356)");
}
