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

package org.apache.eventmesh.function.transformer;

public class TransformerParam {

    private TransformerType transformerType;
    private String value;
    private String template;

    public TransformerParam() {
    }

    public TransformerParam(TransformerType transformerType, String value, String template) {
        this.transformerType = transformerType;
        this.value = value;
        this.template = template;
    }

    public TransformerParam(TransformerType transformerType, String value) {
        this(transformerType, value, null);
    }

    public TransformerType getTransformerType() {
        return transformerType;
    }

    public void setTransformerType(TransformerType transformerType) {
        this.transformerType = transformerType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

}
