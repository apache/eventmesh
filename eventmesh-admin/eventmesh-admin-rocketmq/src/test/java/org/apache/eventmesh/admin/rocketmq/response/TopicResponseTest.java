package org.apache.eventmesh.admin.rocketmq.response;

import junit.framework.TestCase;
import org.junit.Assert;

public class TopicResponseTest extends TestCase {
    String topic = "testTopic";
    String createTime = "2022-5-12 11:43:22";
    public void testTopicResponseGetAndSet() {
        TopicResponse response = new TopicResponse(topic,createTime);

        Assert.assertEquals(createTime, response.getCreatedTime());
        Assert.assertEquals(topic, response.getTopic());

        response.setTopic(topic);
        response.setCreatedTime(createTime);

        Assert.assertEquals(createTime, response.getCreatedTime());
        Assert.assertEquals(topic, response.getTopic());
    }
}
