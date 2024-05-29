package org.apache.eventmesh.common.enums;

public enum ComponentType {
    CONNECTOR("connector"),
    FUNCTION("function"),
    MESH("mesh");

    public String name;

    ComponentType(String name) {
        this.name = name;
    }

    public String componentTypeName() {
        return this.name;
    }
}
