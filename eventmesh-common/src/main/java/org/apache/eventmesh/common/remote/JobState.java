package org.apache.eventmesh.common.remote;

public enum JobState {
    INIT,
    STARTED,
    RUNNING,
    PAUSE,
    COMPLETE,
    DELETE,
    FAIL;
    private static final JobState[] STATES = JobState.values();

    public static JobState fromIndex(Integer index) {
        if (index == null || index < 0 || index >= STATES.length) {
            return null;
        }

        return STATES[index];
    }
}
