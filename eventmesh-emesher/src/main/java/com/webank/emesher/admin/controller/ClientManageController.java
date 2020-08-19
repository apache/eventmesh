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

package com.webank.emesher.admin.controller;

import com.webank.emesher.boot.ProxyTCPServer;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.core.protocol.tcp.client.ProxyTcp2Client;
import com.webank.emesher.core.protocol.tcp.client.group.ClientGroupWrapper;
import com.webank.emesher.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import com.webank.emesher.core.protocol.tcp.client.group.dispatch.DownstreamDispatchStrategy;
import com.webank.emesher.core.protocol.tcp.client.session.Session;
import com.webank.emesher.core.protocol.tcp.client.session.push.DownStreamMsgContext;
import com.webank.emesher.util.ProxyUtil;
import com.webank.eventmesh.common.protocol.tcp.UserAgent;
import com.alibaba.fastjson.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientManageController {

    private static final Logger logger = LoggerFactory.getLogger(ClientManageController.class);

    private ProxyTCPServer proxyTCPServer;

    public ClientManageController(ProxyTCPServer proxyTCPServer){
        this.proxyTCPServer = proxyTCPServer;
    }

    public  void start() throws IOException {
        int port = proxyTCPServer.getAccessConfiguration().proxyServerAdminPort;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/clientManage/showClient", new ShowClientHandler());
        server.createContext("/clientManage/showClientBySystemAndDcn", new ShowClientBySystemAndDcnHandler());
        server.createContext("/clientManage/rejectAllClient", new RejectAllClientHandler());
        server.createContext("/clientManage/rejectClientByIpPort", new RejectClientByIpPortHandler());
        server.createContext("/clientManage/rejectClientBySubSystem", new RejectClientBySubSystemHandler());
        server.createContext("/clientManage/redirectClientBySubSystem", new RedirectClientBySubSystemHandler());
        server.createContext("/clientManage/redirectClientByPath", new RedirectClientByPathHandler());
        server.createContext("/clientManage/redirectClientByIpPort", new RedirectClientByIpPortHandler());
        server.createContext("/proxy/msg/push", new ProxyMsgDownStreamHandler());
        server.createContext("/clientManage/showListenClientByTopic", new ShowListenClientByTopicHandler());

        server.start();
        logger.info("ClientManageController start success, port:{}", port);
    }

    private Map<String, Object> parsePostParameters(HttpExchange exchange)
            throws IOException {
        Map<String, Object> parameters = new HashMap<>();
        if ("post".equalsIgnoreCase(exchange.getRequestMethod())) {
            InputStreamReader isr =
                    new InputStreamReader(exchange.getRequestBody(),"utf-8");
            BufferedReader br = new BufferedReader(isr);
            String query = br.readLine();
            parseQuery(query, parameters);
        }
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private void parseQuery(String query, Map<String, Object> parameters)
            throws UnsupportedEncodingException {

        if (query != null) {
            String pairs[] = query.split("&");

            for (String pair : pairs) {
                String param[] = pair.split("=");

                String key = null;
                String value = null;
                if (param.length > 0) {
                    key = URLDecoder.decode(param[0],"UTF-8");
                }

                if (param.length > 1) {
                    value = URLDecoder.decode(param[1],"UTF-8");
                }

                if (parameters.containsKey(key)) {
                    Object obj = parameters.get(key);
                    if(obj instanceof List<?>) {
                        List<String> values = (List<String>)obj;
                        values.add(value);
                    } else if(obj instanceof String) {
                        List<String> values = new ArrayList<String>();
                        values.add((String)obj);
                        values.add(value);
                        parameters.put(key, values);
                    }
                } else {
                    parameters.put(key, value);
                }
            }
        }
    }

    /**
     * 打印本proxy上所有客户端信息
     *
     * @return
     */
    class ShowClientHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "";
            OutputStream out = httpExchange.getResponseBody();
            try{
                String newLine = System.getProperty("line.separator");
                logger.info("showAllClient=================");
                ClientSessionGroupMapping clientSessionGroupMapping = proxyTCPServer.getClientSessionGroupMapping();
                Map<String, AtomicInteger> dcnSystemMap = clientSessionGroupMapping.statDCNSystemInfo();
                if (!dcnSystemMap.isEmpty()) {
                    List<Map.Entry<String, AtomicInteger>> list = new ArrayList<>();
                    ValueComparator vc = new ValueComparator();
                    for (Map.Entry<String, AtomicInteger> entry : dcnSystemMap.entrySet()) {
                        list.add(entry);
                    }
                    Collections.sort(list, vc);
                    for (Map.Entry<String, AtomicInteger> entry : list) {
                        result += String.format("System=%s | ClientNum=%d", entry.getKey(), entry.getValue().intValue()) +
                                newLine;
                    }
                }
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
            }catch (Exception e){
                logger.error("ShowClientHandler fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }

    class ValueComparator implements Comparator<Map.Entry<String, AtomicInteger>> {
        @Override
        public int compare(Map.Entry<String, AtomicInteger> x, Map.Entry<String, AtomicInteger> y) {
            return x.getValue().intValue() - y.getValue().intValue();
        }
    }

    /**
     * print clientInfo by subsys and dcn
     *
     * @return
     */
    class ShowClientBySystemAndDcnHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "";
            OutputStream out = httpExchange.getResponseBody();
            try{
                String queryString =  httpExchange.getRequestURI().getQuery();
                Map<String,String> queryStringInfo = formData2Dic(queryString);
                String dcn = queryStringInfo.get(ProxyConstants.MANAGE_DCN);
                String subSystem = queryStringInfo.get(ProxyConstants.MANAGE_SUBSYSTEM);

                String newLine = System.getProperty("line.separator");
                logger.info("showClientBySubsysAndDcn,subsys:{},dcn:{}=================",subSystem,dcn);
                ClientSessionGroupMapping clientSessionGroupMapping = proxyTCPServer.getClientSessionGroupMapping();
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
                if (!sessionMap.isEmpty()) {
                    for (Session session : sessionMap.values()) {
                        if (session.getClient().getDcn().equals(dcn) && session.getClient().getSubsystem().equals(subSystem)) {
                            UserAgent userAgent = session.getClient();
                            result += String.format("pid=%s | ip=%s | port=%s | path=%s | purpose=%s", userAgent.getPid(), userAgent
                                    .getHost(), userAgent.getPort(), userAgent.getPath(), userAgent.getPurpose()) + newLine;
                        }
                    }
                }
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
            }catch (Exception e){
                logger.error("ShowClientBySystemAndDcnHandler fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }


    /**
     * query client subscription by topic
     *
     */
    class ShowListenClientByTopicHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "";
            OutputStream out = httpExchange.getResponseBody();
            try{
                String queryString =  httpExchange.getRequestURI().getQuery();
                Map<String,String> queryStringInfo = formData2Dic(queryString);
                String topic = queryStringInfo.get(ProxyConstants.MANAGE_TOPIC);

                String newLine = System.getProperty("line.separator");
                logger.info("showListeningClientByTopic,topic:{}=================",topic);
                ClientSessionGroupMapping clientSessionGroupMapping = proxyTCPServer.getClientSessionGroupMapping();
                ConcurrentHashMap<String, ClientGroupWrapper> clientGroupMap = clientSessionGroupMapping.getClientGroupMap();
                if (!clientGroupMap.isEmpty()) {
                    for (ClientGroupWrapper cgw : clientGroupMap.values()) {
                        Set<Session> listenSessionSet = cgw.getTopic2sessionInGroupMapping().get(topic);
                        if (listenSessionSet != null && listenSessionSet.size() > 0) {
                            result += String.format("group:%s",cgw.getGroupName()) + newLine;
                            for(Session session : listenSessionSet) {
                                UserAgent userAgent = session.getClient();
                                result += String.format("pid=%s | ip=%s | port=%s | path=%s | version=%s", userAgent.getPid(), userAgent
                                        .getHost(), userAgent.getPort(), userAgent.getPath(), userAgent.getVersion()) + newLine;
                            }
                        }
                    }
                }
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
            }catch (Exception e){
                logger.error("ShowListenClientByTopicHandler fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }


    /**
     * remove all clients accessed by proxy
     *
     * @return
     */
    class RejectAllClientHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "";
            OutputStream out = httpExchange.getResponseBody();
            try{
                ClientSessionGroupMapping clientSessionGroupMapping = proxyTCPServer.getClientSessionGroupMapping();
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
                final List<InetSocketAddress> successRemoteAddrs = new ArrayList<InetSocketAddress>();
                try {
                    logger.info("rejectAllClient in admin====================");
                    if (!sessionMap.isEmpty()) {
                        for (Map.Entry<InetSocketAddress, Session> entry : sessionMap.entrySet()) {
                            InetSocketAddress addr = ProxyTcp2Client.serverGoodby2Client(entry.getValue(), clientSessionGroupMapping);
                            if (addr != null) {
                                successRemoteAddrs.add(addr);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("clientManage|rejectAllClient|fail", e);
                    result = String.format("rejectAllClient fail! sessionMap size {%d}, had reject {%s}, errorMsg : %s",
                            sessionMap.size(), printClients(successRemoteAddrs), e.getMessage());
                    httpExchange.sendResponseHeaders(200, 0);
                    out.write(result.getBytes());
                    return;
                }
                result = String.format("rejectAllClient success! sessionMap size {%d}, had reject {%s}", sessionMap.size
                        (), printClients(successRemoteAddrs));
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
            }catch (Exception e){
                logger.error("rejectAllClient fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }

    /**
     * remove c client by ip and port
     *
     * @return
     */
    class RejectClientByIpPortHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "";
            OutputStream out = httpExchange.getResponseBody();
            try{
                String queryString =  httpExchange.getRequestURI().getQuery();
                Map<String,String> queryStringInfo = formData2Dic(queryString);
                String ip = queryStringInfo.get(ProxyConstants.MANAGE_IP);
                String port = queryStringInfo.get(ProxyConstants.MANAGE_PORT);

                if (StringUtils.isBlank(ip) || StringUtils.isBlank(port)) {
                    httpExchange.sendResponseHeaders(200, 0);
                    result = "params illegal!";
                    out.write(result.getBytes());
                    return;
                }
                logger.info("rejectClientByIpPort in admin,ip:{},port:{}====================",ip,port);
                ClientSessionGroupMapping clientSessionGroupMapping = proxyTCPServer.getClientSessionGroupMapping();
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
                final List<InetSocketAddress> successRemoteAddrs = new ArrayList<InetSocketAddress>();
                try {
                    if (!sessionMap.isEmpty()) {
                        for (Map.Entry<InetSocketAddress, Session> entry : sessionMap.entrySet()) {
                            if (entry.getKey().getHostString().equals(ip) && String.valueOf(entry.getKey().getPort()).equals(port)) {
                                InetSocketAddress addr = ProxyTcp2Client.serverGoodby2Client(entry.getValue(), clientSessionGroupMapping);
                                if (addr != null) {
                                    successRemoteAddrs.add(addr);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("clientManage|rejectClientByIpPort|fail|ip={}|port={},errMsg={}", ip, port, e);
                    result = String.format("rejectClientByIpPort fail! {ip=%s port=%s}, had reject {%s}, errorMsg : %s", ip,
                            port, printClients(successRemoteAddrs), e.getMessage());
                    httpExchange.sendResponseHeaders(200, 0);
                    out.write(result.getBytes());
                    return;
                }

                result = String.format("rejectClientByIpPort success! {ip=%s port=%s}, had reject {%s}", ip, port, printClients
                        (successRemoteAddrs));
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
            }catch (Exception e){
                logger.error("rejectClientByIpPort fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }


    /**
     * remove c client by dcn and susysId
     *
     * @return
     */
    class RejectClientBySubSystemHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "";
            OutputStream out = httpExchange.getResponseBody();
            try{
                String queryString =  httpExchange.getRequestURI().getQuery();
                Map<String,String> queryStringInfo = formData2Dic(queryString);
                String dcn = queryStringInfo.get(ProxyConstants.MANAGE_DCN);
                String subSystem = queryStringInfo.get(ProxyConstants.MANAGE_SUBSYSTEM);

                if (StringUtils.isBlank(dcn) || StringUtils.isBlank(subSystem)) {
                    httpExchange.sendResponseHeaders(200, 0);
                    result = "params illegal!";
                    out.write(result.getBytes());
                    return;
                }

                logger.info("rejectClientBySubSystem in admin,subsys:{},dcn:{}====================",subSystem,dcn);
                ClientSessionGroupMapping clientSessionGroupMapping = proxyTCPServer.getClientSessionGroupMapping();
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
                final List<InetSocketAddress> successRemoteAddrs = new ArrayList<InetSocketAddress>();
                try {
                    if (!sessionMap.isEmpty()) {
                        for (Session session : sessionMap.values()) {
                            if (session.getClient().getDcn().equals(dcn) && session.getClient().getSubsystem().equals(subSystem)) {
                                InetSocketAddress addr = ProxyTcp2Client.serverGoodby2Client(session, clientSessionGroupMapping);
                                if (addr != null) {
                                    successRemoteAddrs.add(addr);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("clientManage|rejectClientBySubSystem|fail|dcn={}|subSystemId={},errMsg={}", dcn, subSystem, e);
                    result = String.format("rejectClientBySubSystem fail! sessionMap size {%d}, had reject {%d} , {dcn=%s " +
                                    "port=%s}, errorMsg : %s", sessionMap.size(), printClients(successRemoteAddrs), dcn,
                            subSystem, e.getMessage());
                    httpExchange.sendResponseHeaders(200, 0);
                    out.write(result.getBytes());
                    return;
                }
                result = String.format("rejectClientBySubSystem success! sessionMap size {%d}, had reject {%s} , {dcn=%s " +
                        "port=%s}", sessionMap.size(), printClients(successRemoteAddrs), dcn, subSystem);
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
            }catch (Exception e){
                logger.error("rejectClientBySubSystem fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }

    /**
     * redirect subsystem for subsys and dcn
     *
     * @return
     */
    class RedirectClientBySubSystemHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "";
            OutputStream out = httpExchange.getResponseBody();
            try{
                String queryString =  httpExchange.getRequestURI().getQuery();
                Map<String,String> queryStringInfo = formData2Dic(queryString);
                String dcn = queryStringInfo.get(ProxyConstants.MANAGE_DCN);
                String subSystem = queryStringInfo.get(ProxyConstants.MANAGE_SUBSYSTEM);
                String destProxyIp = queryStringInfo.get(ProxyConstants.MANAGE_DEST_IP);
                String destProxyPort = queryStringInfo.get(ProxyConstants.MANAGE_DEST_PORT);

                if (StringUtils.isBlank(dcn) || !StringUtils.isNumeric(subSystem)
                        || StringUtils.isBlank(destProxyIp) || StringUtils.isBlank(destProxyPort)
                        || !StringUtils.isNumeric(destProxyPort)) {
                    httpExchange.sendResponseHeaders(200, 0);
                    result = "params illegal!";
                    out.write(result.getBytes());
                    return;
                }
                logger.info("redirectClientBySubSystem in admin,subsys:{},dcn:{},destIp:{},destPort:{}====================",subSystem,dcn,destProxyIp,destProxyPort);
                ClientSessionGroupMapping clientSessionGroupMapping = proxyTCPServer.getClientSessionGroupMapping();
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
                String redirectResult = "";
                try {
                    if (!sessionMap.isEmpty()) {
                        for (Session session : sessionMap.values()) {
                            if (session.getClient().getDcn().equals(dcn) && session.getClient().getSubsystem().equals(subSystem)) {
                                redirectResult += "|";
                                redirectResult += ProxyTcp2Client.redirectClient2NewProxy(destProxyIp, Integer.parseInt(destProxyPort),
                                        session, clientSessionGroupMapping);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("clientManage|redirectClientBySubSystem|fail|dcn={}|subSystem={}|destProxyIp" +
                            "={}|destProxyPort={},errMsg={}", dcn, subSystem, destProxyIp, destProxyPort, e);
                    result = String.format("redirectClientBySubSystem fail! sessionMap size {%d}, {clientIp=%s clientPort=%s " +
                                    "destProxyIp=%s destProxyPort=%s}, result {%s}, errorMsg : %s",
                            sessionMap.size(), dcn, subSystem, destProxyIp, destProxyPort, redirectResult, e
                                    .getMessage());
                    httpExchange.sendResponseHeaders(200, 0);
                    out.write(result.getBytes());
                    return;
                }
                result = String.format("redirectClientBySubSystem success! sessionMap size {%d}, {dcn=%s subSystem=%s " +
                                "destProxyIp=%s destProxyPort=%s}, result {%s} ",
                        sessionMap.size(), dcn, subSystem, destProxyIp, destProxyPort, redirectResult);
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
            }catch (Exception e){
                logger.error("redirectClientBySubSystem fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }
    
    /**
     * redirect subsystem for path
     *
     * @return
     */
    class RedirectClientByPathHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "";
            OutputStream out = httpExchange.getResponseBody();
            try{
                String queryString =  httpExchange.getRequestURI().getQuery();
                Map<String,String> queryStringInfo = formData2Dic(queryString);
                String path = queryStringInfo.get(ProxyConstants.MANAGE_PATH);
                String destProxyIp = queryStringInfo.get(ProxyConstants.MANAGE_DEST_IP);
                String destProxyPort = queryStringInfo.get(ProxyConstants.MANAGE_DEST_PORT);

                if (StringUtils.isBlank(path) || StringUtils.isBlank(destProxyIp) || StringUtils.isBlank(destProxyPort) ||
                        !StringUtils.isNumeric(destProxyPort)) {
                    httpExchange.sendResponseHeaders(200, 0);
                    result = "params illegal!";
                    out.write(result.getBytes());
                    return;
                }
                logger.info("redirectClientByPath in admin,path:{},destIp:{},destPort:{}====================",path,destProxyIp,destProxyPort);
                ClientSessionGroupMapping clientSessionGroupMapping = proxyTCPServer.getClientSessionGroupMapping();
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
                String redirectResult = "";
                try {
                    if (!sessionMap.isEmpty()) {
                        for (Session session : sessionMap.values()) {
                            if (session.getClient().getPath().contains(path)) {
                                redirectResult += "|";
                                redirectResult += ProxyTcp2Client.redirectClient2NewProxy(destProxyIp, Integer.parseInt(destProxyPort),
                                        session, clientSessionGroupMapping);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("clientManage|redirectClientByPath|fail|path={}|destProxyIp" +
                            "={}|destProxyPort={},errMsg={}", path, destProxyIp, destProxyPort, e);
                    result = String.format("redirectClientByPath fail! sessionMap size {%d}, {path=%s " +
                                    "destProxyIp=%s destProxyPort=%s}, result {%s}, errorMsg : %s",
                            sessionMap.size(), path, destProxyIp, destProxyPort, redirectResult, e
                                    .getMessage());
                    httpExchange.sendResponseHeaders(200, 0);
                    out.write(result.getBytes());
                    return;
                }
                result = String.format("redirectClientByPath success! sessionMap size {%d}, {path=%s " +
                                "destProxyIp=%s destProxyPort=%s}, result {%s} ",
                        sessionMap.size(), path, destProxyIp, destProxyPort, redirectResult);
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
            }catch (Exception e){
                logger.error("redirectClientByPath fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }

    /**
     * redirect subsystem for ip and port
     *
     * @return
     */
    class RedirectClientByIpPortHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "";
            OutputStream out = httpExchange.getResponseBody();
            try{
                String queryString =  httpExchange.getRequestURI().getQuery();
                Map<String,String> queryStringInfo = formData2Dic(queryString);
                String ip = queryStringInfo.get(ProxyConstants.MANAGE_IP);
                String port = queryStringInfo.get(ProxyConstants.MANAGE_PORT);
                String destProxyIp = queryStringInfo.get(ProxyConstants.MANAGE_DEST_IP);
                String destProxyPort = queryStringInfo.get(ProxyConstants.MANAGE_DEST_PORT);

                if (StringUtils.isBlank(ip) || !StringUtils.isNumeric(port)
                        || StringUtils.isBlank(destProxyIp) || StringUtils.isBlank(destProxyPort)
                        || !StringUtils.isNumeric(destProxyPort)) {
                    httpExchange.sendResponseHeaders(200, 0);
                    result = "params illegal!";
                    out.write(result.getBytes());
                    return;
                }
                logger.info("redirectClientByIpPort in admin,ip:{},port:{},destIp:{},destPort:{}====================",ip,port,destProxyIp,destProxyPort);
                ClientSessionGroupMapping clientSessionGroupMapping = proxyTCPServer.getClientSessionGroupMapping();
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
                String redirectResult = "";
                try {
                    if (!sessionMap.isEmpty()) {
                        for (Session session : sessionMap.values()) {
                            if (session.getClient().getHost().equals(ip) && String.valueOf(session.getClient().getPort()).equals(port)) {
                                redirectResult += "|";
                                redirectResult += ProxyTcp2Client.redirectClient2NewProxy(destProxyIp, Integer.parseInt(destProxyPort),
                                        session, clientSessionGroupMapping);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("clientManage|redirectClientByIpPort|fail|ip={}|port={}|destProxyIp" +
                            "={}|destProxyPort={},errMsg={}", ip, port, destProxyIp, destProxyPort, e);
                    result = String.format("redirectClientByIpPort fail! sessionMap size {%d}, {clientIp=%s clientPort=%s " +
                                    "destProxyIp=%s destProxyPort=%s}, result {%s}, errorMsg : %s",
                            sessionMap.size(), ip, port, destProxyIp, destProxyPort, redirectResult, e
                                    .getMessage());
                    httpExchange.sendResponseHeaders(200, 0);
                    out.write(result.getBytes());
                    return;
                }
                result = String.format("redirectClientByIpPort success! sessionMap size {%d}, {ip=%s port=%s " +
                                "destProxyIp=%s destProxyPort=%s}, result {%s} ",
                        sessionMap.size(), ip, port, destProxyIp, destProxyPort, redirectResult);
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
            }catch (Exception e){
                logger.error("redirectClientByIpPort fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }

    private String printClients(List<InetSocketAddress> clients) {
        if (clients.isEmpty()) {
            return "no session had been closed";
        }
        StringBuffer sb = new StringBuffer();
        for (InetSocketAddress addr : clients) {
            sb.append(addr).append("|");
        }
        return sb.toString();
    }

    private Map<String,String> formData2Dic(String formData) {
        Map<String,String> result = new HashMap<>();
        if(formData== null || formData.trim().length() == 0) {
            return result;
        }
        final String[] items = formData.split("&");
        Arrays.stream(items).forEach(item ->{
            final String[] keyAndVal = item.split("=");
            if( keyAndVal.length == 2) {
                try{
                    final String key = URLDecoder.decode( keyAndVal[0],"utf8");
                    final String val = URLDecoder.decode( keyAndVal[1],"utf8");
                    result.put(key,val);
                }catch (UnsupportedEncodingException e) {
                    logger.warn("formData2Dic:param decode failed...", e);
                }
            }
        });
        return result;
    }

    class ProxyMsgDownStreamHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String result = "false";
            OutputStream out = httpExchange.getResponseBody();
            try{
                Map<String, Object> queryStringInfo =  parsePostParameters(httpExchange);
                String msgStr = (String)queryStringInfo.get("msg");
                String groupName = (String)queryStringInfo.get("group");
                logger.info("recieve msg from other proxy, group:{}, msg:{}", groupName, msgStr);
                if (StringUtils.isBlank(msgStr) || StringUtils.isBlank(groupName)) {
                    logger.warn("msg or groupName is null");
                    httpExchange.sendResponseHeaders(200, 0);
                    out.write(result.getBytes());
                    return;
                }
                MessageExt messageExt = JSON.parseObject(msgStr, MessageExt.class);
                String topic = messageExt.getTopic();

                if (!ProxyUtil.isValidRMBTopic(topic)) {
                    logger.warn("msg topic is illegal");
                    httpExchange.sendResponseHeaders(200, 0);
                    out.write(result.getBytes());
                    return;
                }

                DownstreamDispatchStrategy downstreamDispatchStrategy = proxyTCPServer.getClientSessionGroupMapping().getClientGroupWrapper(groupName).getDownstreamDispatchStrategy();
                Set<Session> groupConsumerSessions = proxyTCPServer.getClientSessionGroupMapping().getClientGroupWrapper(groupName).getGroupConsumerSessions();
                Session session = downstreamDispatchStrategy.select(groupName, topic, groupConsumerSessions);

                if(session == null){
                    logger.error("DownStream msg,retry other proxy found no session again");
                    httpExchange.sendResponseHeaders(200, 0);
                    out.write(result.getBytes());
                    return;
                }

                DownStreamMsgContext downStreamMsgContext =
                        new DownStreamMsgContext(messageExt, session, proxyTCPServer.getClientSessionGroupMapping().getClientGroupWrapper(groupName).getPersistentMsgConsumer(), null, true);
                proxyTCPServer.getClientSessionGroupMapping().getClientGroupWrapper(groupName).getDownstreamMap().putIfAbsent(downStreamMsgContext.seq, downStreamMsgContext);

                if (session.isCanDownStream()) {
                    session.downstreamMsg(downStreamMsgContext);
                    httpExchange.sendResponseHeaders(200, 0);
                    result = "true";
                    out.write(result.getBytes());
                    return;
                }

                logger.warn("ProxyMsgDownStreamHandler|dispatch retry, seq[{}]", downStreamMsgContext.seq);
                long delayTime = ProxyUtil.isService(downStreamMsgContext.msgExt.getTopic()) ? 0 : proxyTCPServer.getAccessConfiguration().proxyTcpMsgRetryDelayInMills;
                downStreamMsgContext.delay(delayTime);
                proxyTCPServer.getProxyTcpRetryer().pushRetry(downStreamMsgContext);
                result = "true";
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());

            }catch (Exception e){
                logger.error("ProxyMsgDownStreamHandler handle fail...", e);
            }finally {
                if(out != null){
                    try {
                        out.close();
                    }catch (IOException e){
                        logger.warn("out close failed...", e);
                    }
                }
            }

        }
    }
}
