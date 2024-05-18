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

package org.apache.eventmesh.meta.consul.config;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigField;
import org.apache.eventmesh.common.config.convert.converter.EnumConverter;

import com.ecwid.consul.transport.TLSConfig.KeyStoreInstanceType;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config(prefix = "eventMesh.registry.consul.tls")
public class ConsulTLSConfig {

    @ConfigField(field = "keyStoreInstanceType", converter = EnumConverter.class)
    private KeyStoreInstanceType keyStoreInstanceType;

    @ConfigField(field = "certificatePath")
    private String certificatePath;

    @ConfigField(field = "certificatePassword")
    private String certificatePassword;

    @ConfigField(field = "keyStorePath")
    private String keyStorePath;

    @ConfigField(field = "keyStorePassword")
    private String keyStorePassword;

}
