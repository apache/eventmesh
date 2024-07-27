package org.apache.eventmesh.common.remote.task;

import java.util.HashMap;
import java.util.Map;

public enum TaskState {
    CREATE,DELETE,PAUSE,COMPLETE;
    private static final TaskState[] STATES_NUM_INDEX = TaskState.values();
    private static final Map<String, TaskState> STATES_NAME_INDEX = new HashMap<>();
    static {

        for (TaskState taskState : STATES_NUM_INDEX) {
            STATES_NAME_INDEX.put(taskState.name(), taskState);
        }
    }

    public static TaskState fromIndex(Integer index) {
        if (index == null || index < 0 || index > STATES_NUM_INDEX.length) {
            return null;
        }

        return STATES_NUM_INDEX[index];
    }

    public static TaskState fromIndex(String index) {
        if (index == null || index.isEmpty()) {
            return null;
        }

        return STATES_NAME_INDEX.get(index);
    }
}
