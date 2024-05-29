package org.apache.eventmesh.common.remote.job;

public enum SyncMode {
    /** 行记录 */
    ROW("R"),
    /** 字段记录 */
    FIELD("F");

    private String value;

    SyncMode(String value){
        this.value = value;
    }

    public static SyncMode valuesOf(String value) {
        SyncMode[] modes = values();
        for (SyncMode mode : modes) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isRow() {
        return this.equals(SyncMode.ROW);
    }

    public boolean isField() {
        return this.equals(SyncMode.FIELD);
    }
}
