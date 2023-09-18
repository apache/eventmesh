package org.apache.eventmesh.common.filter;

import org.apache.eventmesh.common.filter.pattern.Pattern;
import org.apache.eventmesh.common.filter.patternbuild.PatternBuilder;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PatternTest {

    private final String EVENT = "{\n" +
            "\"id\": \"4b26115b-73e-cf74a******\",\n" +
            "        \"specversion\": \"1.0\",\n" +
            "\"source\": \"eventmesh.source\",\n" +
            "\"type\": \"object:put\",\n" +
            "\"datacontenttype\": \"application/json\",\n" +
            "\"subject\": \"xxx.jpg\",\n" +
            "\"time\": \"2022-01-17T12:07:48.955Z\",\n" +
            "\"data\": {\n" +
            "\"name\": \"test01\",\n" +
            "\"state\": \"enable\",\n" +
            "\"num\": 10 ,\n" +
            "\"num1\": 50.7 \n" +
            "}\n" +
            "    }";

    @Test
    public void testSpecifiedFilter(){
        String condition = "{\n" +
                "    \"source\":[\n" +
                "        {\n" +
                "            \"prefix\":\"eventmesh.\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(EVENT);
        Assert.assertEquals(true, res);
    }

    @Test
    public void testPrefixFilter(){
        String condition = "{\n" +
                "    \"source\":[\n" +
                "        {\n" +
                "            \"prefix\":\"eventmesh.\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(EVENT);
        Assert.assertEquals(true, res);
    }

    @Test
    public void testSuffixFilter(){
        String condition = "{\n" +
                "    \"subject\":[\n" +
                "        {\n" +
                "            \"suffix\":\".jpg\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(EVENT);
        Assert.assertEquals(true, res);
    }

    @Test
    public void testNumericFilter(){
        String condition = "{\n" +
                "    \"data\":{\n" +
                "        \"num\":[\n" +
                "            {\n" +
                "                \"numeric\":[\n" +
                "                    \">\",\n" +
                "                    0,\n" +
                "                    \"<=\",\n" +
                "                    10\n" +
                "                ]\n" +
                "            }\n" +
                "        ],\n" +
                "        \"num1\":[\n" +
                "            {\n" +
                "                \"numeric\":[\n" +
                "                    \"=\",\n" +
                "                    50.7\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(EVENT);
        Assert.assertEquals(true, res);
    }

    @Test
    public void testExistsFilter(){
        String condition = "{\n" +
                "    \"data\":{\n" +
                "        \"state\":[\n" +
                "            {\n" +
                "               \"exists\": false\n" +
                "            }\n" +
                "         ]\n" +
                "    }\n" +
                "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(EVENT);
        Assert.assertEquals(false, res);
    }

    @Test
    public void testAnythingButFilter(){
        String condition = "{\n" +
                "    \"data\":{\n" +
                "        \"state\":[\n" +
                "            {\n" +
                "               \"anything-but\": \"enable\"\n" +
                "            }\n" +
                "         ]\n" +
                "    }\n" +
                "}";
        Pattern pattern = PatternBuilder.build(condition);
        Boolean res = pattern.filter(EVENT);
        Assert.assertEquals(false, res);
    }

}
