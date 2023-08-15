package org.apache.eventmesh.common.transform;

import org.apache.eventmesh.common.utils.JsonUtilsTest;
import org.junit.Assert;
import org.junit.Test;

public class TransformTest {

    public static final String EVENT = "{\n" + "    \"specversion\":\"1.0\",\n"
            + "    \"id\":\"51efe8e2-841f-4900-8ff5-3c6dfae1060e\",\n" + "    \"source\":\"eventmesh.plugin.rocketmq.producer\",\n"
            + "    \"type\":\"push\",\n"
            + "    \"dataschema\":\"http://eventmesh.com/test.json\",\n"
            + "    \"eventbusname\":\"demo-bus\",\n" + "    \"data\":{\n" + "        \"null\":null,\n"
            + "        \"text\":\"100\",\n" + "        \"number\":100,\n" + "        \"boolean\":false,\n"
            + "\"cn\":\"中国\",\n" + "        \"array\":[\n" + "            {\n"
            + "                \"level2-1\":\"level2-1\"\n" + "            },\n" + "            {\n"
            + "                \"level2-2\":\"level2-2\"\n" + "            }\n" + "        ]\n" + "    }\n" + "}";



    @Test
    public void testTransformWhole()  {
        Transform transform = TransformBuilder.buildWholeTransform();
        String output = transform.process(EVENT);
        Assert.assertEquals(EVENT, output);
    }

    @Test
    public void testTransformConstant()  {
        Transform transform = TransformBuilder.buildConstantTransform("This is a constant!");
        String output = transform.process(EVENT);
        Assert.assertEquals("This is a constant!", output);
    }

    @Test
    public void testTransformJsonPath_OneKey()  {
        Transform transform = TransformBuilder.buildJsonTransform("$.id");
        String output = transform.process(EVENT);
        Assert.assertEquals("51efe8e2-841f-4900-8ff5-3c6dfae1060e", output);
    }



    @Test
    public void testTransformJsonPath_Array()  {
        Transform transform = TransformBuilder.buildJsonTransform("$.data.text");
        String output = transform.process(EVENT);
        Assert.assertEquals("100", output);
    }

    @Test
    public void testTransformTemplate_Text()  {
        String extractJson = "{\"text\":\"$.data.text\",\"number\":\"$.data.number\"}";
        String template = "The ${text} = ${number}!";
        Transform transform = TransformBuilder.buildTemplateTransForm(extractJson, template);
        String output = transform.process(EVENT);
        Assert.assertEquals("The 100 = 100!", output);
    }

    @Test
    public void testTransformTemplate_Json()  {
        String extractJson = "{\"text\":\"$.data.text\",\"number\":\"$.data.number\"}";
        String template = "{\n" + "  \"text\":\"${text}\",\n" + "  \"number\":${number},\n  \"data\":\"666\"\n}";
        System.out.println(extractJson);
        System.out.println(template);
        Transform transform = TransformBuilder.buildTemplateTransForm(extractJson, template);
        String output = transform.process(EVENT);
        System.out.println(output);
        Assert.assertEquals("{\n  \"text\":\"100\",\n  \"number\":100,\n  \"data\":\"666\"\n}", output.toString());
    }


    @Test
    public void testTransformTemplate_Constant() {
        String extractJson = "{\"name\":\"$.data.text\",\"table\":\"tableName\"" + "}";
        String template = "The instance is broken，which name is ${name} , ${table}";
        Transform transform = TransformBuilder.buildTemplateTransForm(extractJson, template);
        String output = transform.process(EVENT);
        Assert.assertEquals("The instance is broken，which name is 100 , tableName",
                output);
    }

    @Test
    public void testTransformTemplate_InvalidJsonPath() {
        String extractJson = "{\"name\":\"$.data.text\",\"constant\":\"Please deal with it timely.\"" + "}";
        String template = "The instance is broken，which name is ${name} , ${constant}";
        Transform transform = TransformBuilder.buildTemplateTransForm(extractJson, template);
        String output = transform.process(EVENT);
        Assert.assertEquals("The instance is broken，which name is 100 , Please deal with it timely.",
                output);
    }



}
