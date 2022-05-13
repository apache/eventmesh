package org.apache.eventmesh.admin.rocketmq.request;

import junit.framework.TestCase;
import org.junit.Assert;

public class TopicCreateRequestTest extends TestCase {

    public void testTestSetAndGetName() {
        TopicCreateRequest request = new TopicCreateRequest("test");
        Assert.assertEquals("test",request.getName());
        request.setName("test");
        Assert.assertEquals("test",request.getName());
    }
}
