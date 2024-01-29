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

public class TransformerBuilder {

    public static Transformer buildTransformer(TransformerParam transformerParam) {
        switch (transformerParam.getTransformerType()) {
            case ORIGINAL:
                return buildOriginalTransformer();
            case CONSTANT:
                return buildConstantTransformer(transformerParam.getValue());
            case TEMPLATE:
                return buildTemplateTransFormer(transformerParam.getValue(), transformerParam.getTemplate());
            default:
                throw new TransformException("invalid config");
        }
    }

    public static Transformer buildTemplateTransFormer(String jsonContent, String template) {
        JsonPathParser jsonPathParser = new JsonPathParser(jsonContent);
        Template templateEntry = new Template(template);
        return new TemplateTransformer(jsonPathParser, templateEntry);
    }

    public static Transformer buildConstantTransformer(String constant) {
        return new ConstantTransformer(constant);
    }

    public static Transformer buildOriginalTransformer() {
        return new OriginalTransformer();
    }

}
