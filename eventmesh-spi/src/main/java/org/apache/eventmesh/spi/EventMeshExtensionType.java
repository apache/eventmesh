package org.apache.eventmesh.spi;

/**
 * An Extension can be defined by extensionTypeName and extensionInstanceName
 */
public enum EventMeshExtensionType {
    UNKNOWN("unknown"),
    CONNECTOR("connector"),
    REGISTRY("registry"),
    SECURITY("security"),
    ;

    private final String extensionTypeName;

    EventMeshExtensionType(String extensionTypeName) {
        this.extensionTypeName = extensionTypeName;
    }

    public String getExtensionTypeName() {
        return extensionTypeName;
    }

}
