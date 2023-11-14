/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.transformer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

public class TransformTest {

    public static final String EVENT = "{\n"
        + "\"id\": \"5b26115b-73e-cf74a******\",\n"
        + "      \"specversion\": \"1.0\",\n"
        + "\"source\": \"apache.eventmesh\",\n"
        + "\"type\": \"object:test\",\n"
        + "\"datacontenttype\": \"application/json\",\n"
        + "\"subject\": \"xxx.jpg\",\n"
        + "\"time\": \"2023-09-17T12:07:48.955Z\",\n"
        + "\"data\": {\n"
        + "\"name\": \"test-transformer\",\n"
        + "\"num\": 100  ,\n"
        + "\"boolean\": true,\n"
        + "\"nullV\": null\n"
        + "}\n"
        + "    }";

    @Test
    public void testOriginalTransformer() throws JsonProcessingException {

        Transformer transformer = new TransformerBuilder.Builder(TransformerType.ORIGINAL).build();
        String output = transformer.transform(EVENT);
        Assertions.assertEquals(EVENT, output);

        Transformer transformer1 = TransformerBuilder.buildOriginalTransformer();
        String output1 = transformer1.transform(EVENT);
        Assertions.assertEquals(EVENT, output1);
    }

    @Test
    public void testConstantTransformer() throws JsonProcessingException {
        Transformer transformer = new TransformerBuilder.Builder(TransformerType.CONSTANT).setContent("constant test").build();
        String output = transformer.transform(EVENT);
        Assertions.assertEquals("constant test", output);

        Transformer transformer1 = TransformerBuilder.buildConstantTransformer("constant test");
        String output1 = transformer1.transform(EVENT);
        Assertions.assertEquals("constant test", output1);

    }

    @Test
    public void testTemplateTransFormerWithStringValue() throws JsonProcessingException {
        String content = "{\"data-name\":\"$.data.name\"}";
        String template = "Transformers test:data name is ${data-name}";
        Transformer transform = TransformerBuilder.buildTemplateTransFormer(content, template);
        String output = transform.transform(EVENT);
        Assertions.assertEquals("Transformers test:data name is test-transformer", output);

        Transformer transformer1 = new TransformerBuilder.Builder(TransformerType.TEMPLATE)
            .setContent(content)
            .setTemplate(template).build();
        String output1 = transformer1.transform(EVENT);
        Assertions.assertEquals("Transformers test:data name is test-transformer", output1);

    }

    @Test
    public void testTemplateTransFormerWithNullContent() throws JsonProcessingException {
        String content = "{}";
        String template = "Transformers test:data num is ${data-num}";
        Transformer transformer = TransformerBuilder.buildTemplateTransFormer(content, template);
        String output = transformer.transform(EVENT);
        Assertions.assertEquals("Transformers test:data num is ${data-num}", output);
    }

    @Test
    public void testTemplateTransFormerWithNoMatchContent() throws JsonProcessingException {
        String extractJson = "{\"data-num\":\"$.data.no\"}";
        String template = "Transformers test:data num is ${data-num}";
        Transformer transformer = TransformerBuilder.buildTemplateTransFormer(extractJson, template);
        String output = transformer.transform(EVENT);
        Assertions.assertEquals("Transformers test:data num is null", output);
    }

    @Test
    public void testTemplateTransFormerWithMatchNumValue() throws JsonProcessingException {
        String extractJson = "{\"data-num\":\"$.data.num\"}";
        String template = "Transformers test:data num is ${data-num}";
        Transformer transformer = TransformerBuilder.buildTemplateTransFormer(extractJson, template);
        String output = transformer.transform(EVENT);
        Assertions.assertEquals("Transformers test:data num is 100", output);
    }

    @Test
    public void testTemplateTransFormerWithMatchNullValue() throws JsonProcessingException {
        String content = "{\"data-null\":\"$.data.nullV\"}";
        String template = "Transformers test:data null is ${data-null}";
        Transformer transformer = TransformerBuilder.buildTemplateTransFormer(content, template);
        String output = transformer.transform(EVENT);
        Assertions.assertEquals("Transformers test:data null is null", output);
    }

    @Test
    public void testTemplateTransFormerWithMatchBooleanValue() throws JsonProcessingException {
        String extractJson = "{\"boolean\":\"$.data.boolean\"}";
        String template = "Transformers test:data boolean is ${boolean}";
        Transformer transformer = TransformerBuilder.buildTemplateTransFormer(extractJson, template);
        String output = transformer.transform(EVENT);
        Assertions.assertEquals("Transformers test:data boolean is true", output);
    }

    //
    @Test
    public void testTemplateTransFormerWithConstant() throws JsonProcessingException {
        String extractJson = "{\"name\":\"$.data.name\",\"constant\":\"constant\"" + "}";
        String template = "Transformers test:data name is ${name}, constant is ${constant}";
        Transformer transformer = TransformerBuilder.buildTemplateTransFormer(extractJson, template);
        String output = transformer.transform(EVENT);
        Assertions.assertEquals("Transformers test:data name is test-transformer, constant is constant",
            output);
    }

}
