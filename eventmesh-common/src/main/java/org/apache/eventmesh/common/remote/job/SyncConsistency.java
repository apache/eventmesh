package org.apache.eventmesh.common.remote.job;

public enum SyncConsistency {
    /** 基于当前介质最新数据 */
    MEDIA("M"),
    /** 基于当前的store记录的数据 */
    STORE("S"),
    /** 基于当前的变更value，最终一致性 */
    BASE("B");

    private String value;

    SyncConsistency(String value){
        this.value = value;
    }

    public static SyncConsistency valuesOf(String value) {
        SyncConsistency[] modes = values();
        for (SyncConsistency mode : modes) {
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

    public boolean isMedia() {
        return this.equals(SyncConsistency.MEDIA);
    }

    public boolean isStore() {
        return this.equals(SyncConsistency.STORE);
    }

    public boolean isBase() {
        return this.equals(SyncConsistency.BASE);
    }
}
