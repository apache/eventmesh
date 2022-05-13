package org.apache.eventmesh.admin.rocketmq.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import junit.framework.TestCase;
import org.apache.eventmesh.admin.rocketmq.response.TopicResponse;
import org.junit.Assert;

import java.io.IOException;

public class JsonUtilsTest extends TestCase {

    String objectJson = "{\"topic\":\"testTopic\",\"created_time\":\"2022-5-12 11:43:22\"}";
    TopicResponse response = new TopicResponse("testTopic", "2022-5-12 11:43:22");

    public void testToJson() throws JsonProcessingException {
        String res = JsonUtils.toJson(this.response);

        Assert.assertEquals(res, this.objectJson);
    }

    public void testToObject() throws JsonProcessingException {

        TopicResponse res = JsonUtils.toObject(this.objectJson, TopicResponse.class);

        Assert.assertEquals(this.response.getCreatedTime(), res.getCreatedTime());

        Assert.assertEquals(this.response.getTopic(), res.getTopic());
    }

    public void testSerializeAndDeserialize() throws IOException {

        byte[] serialize = JsonUtils.serialize(this.response);

        TopicResponse deserializeByBytes = JsonUtils.deserialize(TopicResponse.class, serialize);

        TopicResponse deserializeByString = JsonUtils.deserialize(TopicResponse.class, objectJson);


        Assert.assertEquals(deserializeByString.getCreatedTime(), deserializeByBytes.getCreatedTime());

        Assert.assertEquals(deserializeByString.getTopic(), deserializeByBytes.getTopic());



    }


}
