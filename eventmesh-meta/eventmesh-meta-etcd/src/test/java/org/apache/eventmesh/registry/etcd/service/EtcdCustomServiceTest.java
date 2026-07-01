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

package org.apache.eventmesh.registry.etcd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.api.meta.bo.EventMeshAppSubTopicInfo;
import org.apache.eventmesh.api.meta.bo.EventMeshServicePubTopicInfo;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.meta.etcd.service.EtcdCustomService;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;

@ExtendWith(MockitoExtension.class)
public class EtcdCustomServiceTest {

    @Mock
    private Client etcdClient;

    @Mock
    private KV kvClient;

    @Mock
    private KeyValue keyValue;

    @Mock
    private GetResponse getResponse;

    @Mock
    private CompletableFuture<GetResponse> futureResponse;

    @InjectMocks
    private EtcdCustomService etcdCustomService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(etcdClient.getKVClient()).thenReturn(kvClient);
    }

    @Test
    public void testFindEventMeshServicePubTopicInfos() throws Exception {

        EventMeshServicePubTopicInfo mockInfo = new EventMeshServicePubTopicInfo();
        mockInfo.setService("testService");
        mockInfo.setTopics(Collections.unmodifiableSet(new HashSet<>(Arrays.asList("topic1", "topic2"))));

        String mockValue = JsonUtils.toJSONString(mockInfo);
        ByteSequence mockByteSequence = ByteSequence.from(mockValue, StandardCharsets.UTF_8);

        when(keyValue.getValue()).thenReturn(mockByteSequence);
        when(getResponse.getKvs()).thenReturn(Arrays.asList(keyValue));
        when(futureResponse.get()).thenReturn(getResponse);
        when(kvClient.get(any(ByteSequence.class), any(GetOption.class))).thenReturn(futureResponse);

        List<EventMeshServicePubTopicInfo> result = etcdCustomService.findEventMeshServicePubTopicInfos();
        assertNotNull(result);
        assertEquals(1, result.size());
        EventMeshServicePubTopicInfo resultInfo = result.get(0);
        assertEquals("testService", resultInfo.getService());
        assertEquals(new HashSet<>(Arrays.asList("topic1", "topic2")), resultInfo.getTopics());
    }


    @Test
    public void testFindEventMeshAppSubTopicInfoByGroup() throws Exception {

        String group = "testGroup";
        EventMeshAppSubTopicInfo mockInfo = new EventMeshAppSubTopicInfo();

        String mockValue = JsonUtils.toJSONString(mockInfo);
        ByteSequence mockByteSequence = ByteSequence.from(mockValue, StandardCharsets.UTF_8);

        when(keyValue.getValue()).thenReturn(mockByteSequence);
        when(kvClient.get(any(ByteSequence.class), any(GetOption.class))).thenReturn(futureResponse);
        when(futureResponse.get()).thenReturn(getResponse);
        when(getResponse.getKvs()).thenReturn(Collections.singletonList(keyValue));

        EventMeshAppSubTopicInfo result = etcdCustomService.findEventMeshAppSubTopicInfoByGroup(group);

        assertNotNull(result);
    }

}
