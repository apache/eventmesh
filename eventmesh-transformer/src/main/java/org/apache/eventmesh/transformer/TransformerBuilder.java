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

    public static final class Builder {

        private final TransformerType transformerType;
        private String template;
        private String content;

        public Builder(TransformerType transformerType) {
            this.transformerType = transformerType;
        }

        public Builder setContent(String content) {
            this.content = content;
            return this;
        }

        public Builder setTemplate(String template) {
            this.template = template;
            return this;
        }

        public Transformer build() {
            switch (this.transformerType) {
                case CONSTANT:
                    return buildConstantTransformer(this.content);
                case ORIGINAL:
                    return buildOriginalTransformer();
                case TEMPLATE:
                    return buildTemplateTransFormer(this.content, this.template);
                default:
                    throw new TransformException("invalid config");
            }
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
