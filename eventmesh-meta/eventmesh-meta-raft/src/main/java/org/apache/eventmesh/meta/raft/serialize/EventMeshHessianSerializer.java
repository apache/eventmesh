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

package org.apache.eventmesh.meta.raft.serialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.HessianSerializer;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.SerializerFactory;

public class EventMeshHessianSerializer extends HessianSerializer {

    private SerializerFactory customizeSerializerFactory = new EventMeshSerializerFactory();

    private static EventMeshHessianSerializer instance;

    private EventMeshHessianSerializer() {
    }

    public static HessianSerializer getInstance() {
        if (instance == null) {
            synchronized (EventMeshHessianSerializer.class) {
                if (instance == null) {
                    instance = new EventMeshHessianSerializer();
                }
            }
        }
        return instance;
    }

    @Override
    public byte[] serialize(Object obj) throws CodecException {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(byteArray);
        output.setSerializerFactory(customizeSerializerFactory);
        try {
            output.writeObject(obj);
            output.close();
        } catch (IOException e) {
            throw new CodecException("IOException occurred when Hessian serializer encode!", e);
        }

        return byteArray.toByteArray();
    }

    @Override
    public <T> T deserialize(byte[] data, String classOfT) throws CodecException {
        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(data));
        input.setSerializerFactory(customizeSerializerFactory);
        Object resultObject;
        try {
            resultObject = input.readObject();
            input.close();
        } catch (IOException e) {
            throw new CodecException("IOException occurred when Hessian serializer decode!", e);
        }
        return (T) resultObject;
    }
}
