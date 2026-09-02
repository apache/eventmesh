package org.apache.eventmesh.connector.fakeplugin;

/**
 * Test canary for {@code ruleConnectorPluginsDependOnlyOnSpi}: a fake plugin class that
 * reaches into connector-runtime internals. The rule must flag it. Lives in test sources
 * so production code stays clean; ArchUnit imports it only when the guard test asks for
 * a classpath import that includes tests — the production rule uses
 * DO_NOT_INCLUDE_TESTS, so this canary is exercised via the focused unit test below.
 */
public class FakePluginCanary {
    public static void touch() {
        Class<?> c = org.apache.eventmesh.connector.ConnectorRuntime.class;
    }
}
