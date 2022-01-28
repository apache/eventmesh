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

// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: eventmesh-client.proto

package org.apache.eventmesh.common.protocol.grpc.protos;

public final class EventmeshGrpc {
  private EventmeshGrpc() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_RequestHeader_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_RequestHeader_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_SimpleMessage_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_SimpleMessage_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_SimpleMessage_PropertiesEntry_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_SimpleMessage_PropertiesEntry_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_BatchMessage_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_BatchMessage_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_PropertiesEntry_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_PropertiesEntry_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_Response_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_Response_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_Subscription_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_Subscription_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_Subscription_SubscriptionItem_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_Subscription_SubscriptionItem_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_PropertiesEntry_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_PropertiesEntry_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_Heartbeat_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_Heartbeat_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_eventmesh_common_protocol_grpc_Heartbeat_HeartbeatItem_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_eventmesh_common_protocol_grpc_Heartbeat_HeartbeatItem_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    String[] descriptorData = {
      "\n\026eventmesh-client.proto\022\036eventmesh.comm" +
      "on.protocol.grpc\"\332\001\n\rRequestHeader\022\013\n\003en" +
      "v\030\001 \001(\t\022\016\n\006region\030\002 \001(\t\022\013\n\003idc\030\003 \001(\t\022\n\n\002" +
      "ip\030\004 \001(\t\022\013\n\003pid\030\005 \001(\t\022\013\n\003sys\030\006 \001(\t\022\020\n\010us" +
      "ername\030\007 \001(\t\022\020\n\010password\030\010 \001(\t\022\020\n\010langua" +
      "ge\030\t \001(\t\022\024\n\014protocolType\030\n \001(\t\022\027\n\017protoc" +
      "olVersion\030\013 \001(\t\022\024\n\014protocolDesc\030\014 \001(\t\"\307\002" +
      "\n\rSimpleMessage\022=\n\006header\030\001 \001(\0132-.eventm" +
      "esh.common.protocol.grpc.RequestHeader\022\025" +
      "\n\rproducerGroup\030\002 \001(\t\022\r\n\005topic\030\003 \001(\t\022\017\n\007" +
      "content\030\004 \001(\t\022\013\n\003ttl\030\005 \001(\t\022\020\n\010uniqueId\030\006" +
      " \001(\t\022\016\n\006seqNum\030\007 \001(\t\022\013\n\003tag\030\010 \001(\t\022Q\n\npro" +
      "perties\030\t \003(\0132=.eventmesh.common.protoco" +
      "l.grpc.SimpleMessage.PropertiesEntry\0321\n\017" +
      "PropertiesEntry\022\013\n\003key\030\001 \001(\t\022\r\n\005value\030\002 " +
      "\001(\t:\0028\001\"\260\003\n\014BatchMessage\022=\n\006header\030\001 \001(\013" +
      "2-.eventmesh.common.protocol.grpc.Reques" +
      "tHeader\022\025\n\rproducerGroup\030\002 \001(\t\022\r\n\005topic\030" +
      "\003 \001(\t\022M\n\013messageItem\030\004 \003(\01328.eventmesh.c" +
      "ommon.protocol.grpc.BatchMessage.Message" +
      "Item\032\353\001\n\013MessageItem\022\017\n\007content\030\001 \001(\t\022\013\n" +
      "\003ttl\030\002 \001(\t\022\020\n\010uniqueId\030\003 \001(\t\022\016\n\006seqNum\030\004" +
      " \001(\t\022\013\n\003tag\030\005 \001(\t\022\\\n\nproperties\030\006 \003(\0132H." +
      "eventmesh.common.protocol.grpc.BatchMess" +
      "age.MessageItem.PropertiesEntry\0321\n\017Prope" +
      "rtiesEntry\022\013\n\003key\030\001 \001(\t\022\r\n\005value\030\002 \001(\t:\002" +
      "8\001\"?\n\010Response\022\020\n\010respCode\030\001 \001(\t\022\017\n\007resp" +
      "Msg\030\002 \001(\t\022\020\n\010respTime\030\003 \001(\t\"\325\006\n\014Subscrip" +
      "tion\022=\n\006header\030\001 \001(\0132-.eventmesh.common." +
      "protocol.grpc.RequestHeader\022\025\n\rconsumerG" +
      "roup\030\002 \001(\t\022X\n\021subscriptionItems\030\003 \003(\0132=." +
      "eventmesh.common.protocol.grpc.Subscript" +
      "ion.SubscriptionItem\022\013\n\003url\030\004 \001(\t\022A\n\005rep" +
      "ly\030\005 \001(\01322.eventmesh.common.protocol.grp" +
      "c.Subscription.Reply\032\274\002\n\020SubscriptionIte" +
      "m\022\r\n\005topic\030\001 \001(\t\022\\\n\004mode\030\002 \001(\0162N.eventme" +
      "sh.common.protocol.grpc.Subscription.Sub" +
      "scriptionItem.SubscriptionMode\022\\\n\004type\030\003" +
      " \001(\0162N.eventmesh.common.protocol.grpc.Su" +
      "bscription.SubscriptionItem.Subscription" +
      "Type\"4\n\020SubscriptionMode\022\016\n\nCLUSTERING\020\000" +
      "\022\020\n\014BROADCASTING\020\001\"\'\n\020SubscriptionType\022\t" +
      "\n\005ASYNC\020\000\022\010\n\004SYNC\020\001\032\205\002\n\005Reply\022\025\n\rproduce" +
      "rGroup\030\001 \001(\t\022\r\n\005topic\030\002 \001(\t\022\017\n\007content\030\003" +
      " \001(\t\022\013\n\003ttl\030\004 \001(\t\022\020\n\010uniqueId\030\005 \001(\t\022\016\n\006s" +
      "eqNum\030\006 \001(\t\022\013\n\003tag\030\007 \001(\t\022V\n\nproperties\030\010" +
      " \003(\0132B.eventmesh.common.protocol.grpc.Su" +
      "bscription.Reply.PropertiesEntry\0321\n\017Prop" +
      "ertiesEntry\022\013\n\003key\030\001 \001(\t\022\r\n\005value\030\002 \001(\t:" +
      "\0028\001\"\340\002\n\tHeartbeat\022=\n\006header\030\001 \001(\0132-.even" +
      "tmesh.common.protocol.grpc.RequestHeader" +
      "\022H\n\nclientType\030\002 \001(\01624.eventmesh.common." +
      "protocol.grpc.Heartbeat.ClientType\022\025\n\rpr" +
      "oducerGroup\030\003 \001(\t\022\025\n\rconsumerGroup\030\004 \001(\t" +
      "\022O\n\016heartbeatItems\030\005 \003(\01327.eventmesh.com" +
      "mon.protocol.grpc.Heartbeat.HeartbeatIte" +
      "m\032+\n\rHeartbeatItem\022\r\n\005topic\030\001 \001(\t\022\013\n\003url" +
      "\030\002 \001(\t\"\036\n\nClientType\022\007\n\003PUB\020\000\022\007\n\003SUB\020\0012\314" +
      "\002\n\020PublisherService\022b\n\007publish\022-.eventme" +
      "sh.common.protocol.grpc.SimpleMessage\032(." +
      "eventmesh.common.protocol.grpc.Response\022" +
      "l\n\014requestReply\022-.eventmesh.common.proto" +
      "col.grpc.SimpleMessage\032-.eventmesh.commo" +
      "n.protocol.grpc.SimpleMessage\022f\n\014batchPu" +
      "blish\022,.eventmesh.common.protocol.grpc.B" +
      "atchMessage\032(.eventmesh.common.protocol." +
      "grpc.Response2\321\002\n\017ConsumerService\022c\n\tsub" +
      "scribe\022,.eventmesh.common.protocol.grpc." +
      "Subscription\032(.eventmesh.common.protocol" +
      ".grpc.Response\022r\n\017subscribeStream\022,.even" +
      "tmesh.common.protocol.grpc.Subscription\032" +
      "-.eventmesh.common.protocol.grpc.SimpleM" +
      "essage(\0010\001\022e\n\013unsubscribe\022,.eventmesh.co" +
      "mmon.protocol.grpc.Subscription\032(.eventm" +
      "esh.common.protocol.grpc.Response2t\n\020Hea" +
      "rtbeatService\022`\n\theartbeat\022).eventmesh.c" +
      "ommon.protocol.grpc.Heartbeat\032(.eventmes" +
      "h.common.protocol.grpc.ResponseBC\n0org.a" +
      "pache.eventmesh.common.protocol.grpc.pro" +
      "tosB\rEventmeshGrpcP\001b\006proto3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_eventmesh_common_protocol_grpc_RequestHeader_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_eventmesh_common_protocol_grpc_RequestHeader_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_RequestHeader_descriptor,
        new String[] { "Env", "Region", "Idc", "Ip", "Pid", "Sys", "Username", "Password", "Language", "ProtocolType", "ProtocolVersion", "ProtocolDesc", });
    internal_static_eventmesh_common_protocol_grpc_SimpleMessage_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_eventmesh_common_protocol_grpc_SimpleMessage_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_SimpleMessage_descriptor,
        new String[] { "Header", "ProducerGroup", "Topic", "Content", "Ttl", "UniqueId", "SeqNum", "Tag", "Properties", });
    internal_static_eventmesh_common_protocol_grpc_SimpleMessage_PropertiesEntry_descriptor =
      internal_static_eventmesh_common_protocol_grpc_SimpleMessage_descriptor.getNestedTypes().get(0);
    internal_static_eventmesh_common_protocol_grpc_SimpleMessage_PropertiesEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_SimpleMessage_PropertiesEntry_descriptor,
        new String[] { "Key", "Value", });
    internal_static_eventmesh_common_protocol_grpc_BatchMessage_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_eventmesh_common_protocol_grpc_BatchMessage_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_BatchMessage_descriptor,
        new String[] { "Header", "ProducerGroup", "Topic", "MessageItem", });
    internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_descriptor =
      internal_static_eventmesh_common_protocol_grpc_BatchMessage_descriptor.getNestedTypes().get(0);
    internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_descriptor,
        new String[] { "Content", "Ttl", "UniqueId", "SeqNum", "Tag", "Properties", });
    internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_PropertiesEntry_descriptor =
      internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_descriptor.getNestedTypes().get(0);
    internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_PropertiesEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_BatchMessage_MessageItem_PropertiesEntry_descriptor,
        new String[] { "Key", "Value", });
    internal_static_eventmesh_common_protocol_grpc_Response_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_eventmesh_common_protocol_grpc_Response_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_Response_descriptor,
        new String[] { "RespCode", "RespMsg", "RespTime", });
    internal_static_eventmesh_common_protocol_grpc_Subscription_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_eventmesh_common_protocol_grpc_Subscription_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_Subscription_descriptor,
        new String[] { "Header", "ConsumerGroup", "SubscriptionItems", "Url", "Reply", });
    internal_static_eventmesh_common_protocol_grpc_Subscription_SubscriptionItem_descriptor =
      internal_static_eventmesh_common_protocol_grpc_Subscription_descriptor.getNestedTypes().get(0);
    internal_static_eventmesh_common_protocol_grpc_Subscription_SubscriptionItem_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_Subscription_SubscriptionItem_descriptor,
        new String[] { "Topic", "Mode", "Type", });
    internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_descriptor =
      internal_static_eventmesh_common_protocol_grpc_Subscription_descriptor.getNestedTypes().get(1);
    internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_descriptor,
        new String[] { "ProducerGroup", "Topic", "Content", "Ttl", "UniqueId", "SeqNum", "Tag", "Properties", });
    internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_PropertiesEntry_descriptor =
      internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_descriptor.getNestedTypes().get(0);
    internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_PropertiesEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_Subscription_Reply_PropertiesEntry_descriptor,
        new String[] { "Key", "Value", });
    internal_static_eventmesh_common_protocol_grpc_Heartbeat_descriptor =
      getDescriptor().getMessageTypes().get(5);
    internal_static_eventmesh_common_protocol_grpc_Heartbeat_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_Heartbeat_descriptor,
        new String[] { "Header", "ClientType", "ProducerGroup", "ConsumerGroup", "HeartbeatItems", });
    internal_static_eventmesh_common_protocol_grpc_Heartbeat_HeartbeatItem_descriptor =
      internal_static_eventmesh_common_protocol_grpc_Heartbeat_descriptor.getNestedTypes().get(0);
    internal_static_eventmesh_common_protocol_grpc_Heartbeat_HeartbeatItem_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_eventmesh_common_protocol_grpc_Heartbeat_HeartbeatItem_descriptor,
        new String[] { "Topic", "Url", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
