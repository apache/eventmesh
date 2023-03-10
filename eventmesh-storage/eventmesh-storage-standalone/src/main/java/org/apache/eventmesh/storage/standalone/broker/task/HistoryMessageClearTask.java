package org.apache.eventmesh.storage.standalone.broker.task;

import org.apache.eventmesh.common.utils.ThreadUtils;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HistoryMessageClearTask implements Runnable {

    private HistoryMessageClear historyMessageClear;

    public HistoryMessageClearTask(HistoryMessageClear historyMessageClear) {
        this.historyMessageClear = historyMessageClear;
    }

    @Override
    public void run() {
        while (true) {
            historyMessageClear.clearMessages();
            try {
                ThreadUtils.sleepWithThrowException(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Thread is interrupted, thread name: {}", Thread.currentThread().getName(), e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
