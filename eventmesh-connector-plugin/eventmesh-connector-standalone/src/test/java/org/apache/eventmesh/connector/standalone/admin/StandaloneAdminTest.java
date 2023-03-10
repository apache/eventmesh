package org.apache.eventmesh.connector.standalone.admin;

import org.apache.eventmesh.connector.standalone.broker.MessageQueue;
import org.apache.eventmesh.connector.standalone.broker.StandaloneBroker;
import org.apache.eventmesh.connector.standalone.broker.model.TopicMetadata;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import io.cloudevents.CloudEvent;

@RunWith(MockitoJUnitRunner.class)
public class StandaloneAdminTest {

    private static final String TEST_TOPIC = "test_topic";
    private static final int OFF_SET = 0;
    private static final int LENGTH = 5;
    private static final boolean TOPIC_EXISTS = true;
    private static final boolean TOPIC_DO_NOT_EXISTS = false;

    @Mock
    private StandaloneBroker standaloneBroker;

    private StandaloneAdmin standaloneAdmin;

    @Before
    public void setUp() {
        initStaticInstance();
    }

    @Test
    public void testIsStarted() {
        standaloneAdmin.start();
        Assert.assertTrue(standaloneAdmin.isStarted());
    }

    @Test
    public void testIsClosed() {
        standaloneAdmin.shutdown();
        Assert.assertTrue(standaloneAdmin.isClosed());
    }

    @Test
    public void testGetTopic() throws Exception {
        Assert.assertNotNull(standaloneAdmin.getTopic());
        Assert.assertFalse(standaloneAdmin.getTopic().isEmpty());
    }

    @Test
    public void testCreateTopic() {
        standaloneAdmin.createTopic(TEST_TOPIC);
        Mockito.verify(standaloneBroker).createTopicIfAbsent(TEST_TOPIC);
    }

    @Test
    public void testDeleteTopic() {
        standaloneAdmin.deleteTopic(TEST_TOPIC);
        Mockito.verify(standaloneBroker).deleteTopicIfExist(TEST_TOPIC);
    }

    @Test
    public void testGetEvent() throws Exception {
        Mockito.when(standaloneBroker.checkTopicExist(TEST_TOPIC)).thenReturn(TOPIC_EXISTS);
        Mockito.when(standaloneBroker.getMessage(TEST_TOPIC, OFF_SET)).thenReturn(new MockCloudEvent());
        List<CloudEvent> events = standaloneAdmin.getEvent(TEST_TOPIC, OFF_SET, LENGTH);
        Assert.assertNotNull(events);
        Assert.assertFalse(events.isEmpty());
    }

    @Test
    public void testGetEvent_throwException() {
        Mockito.when(standaloneBroker.checkTopicExist(TEST_TOPIC)).thenReturn(TOPIC_DO_NOT_EXISTS);
        Exception exception = Assert.assertThrows(Exception.class, () -> standaloneAdmin.getEvent(TEST_TOPIC, OFF_SET, LENGTH));
        Assert.assertEquals("The topic name doesn't exist in the message queue", exception.getMessage());
    }

    @Test
    public void testPublish() {
    }

    private void initStaticInstance() {
        try (MockedStatic<StandaloneBroker> standaloneBrokerMockedStatic = Mockito.mockStatic(StandaloneBroker.class)) {
            standaloneBrokerMockedStatic.when(StandaloneBroker::getInstance).thenReturn(standaloneBroker);
            Mockito.when(standaloneBroker.getMessageContainer()).thenReturn(createMessageContainer());
            standaloneAdmin = new StandaloneAdmin(new Properties());
        }
    }

    private ConcurrentHashMap<TopicMetadata, MessageQueue> createMessageContainer() {
        ConcurrentHashMap<TopicMetadata, MessageQueue> messageContainer = new ConcurrentHashMap<>();
        messageContainer.put(new TopicMetadata(TEST_TOPIC), new MessageQueue());
        return messageContainer;
    }
}