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

package org.apache.eventmesh.connector.storage.jdbc;

import java.util.Properties;

import org.junit.Test;

public class JDBCStorageConnectorTest  {

	JDBCStorageConnector connector = new JDBCStorageConnector();
	
	@Test
	public void testInit() throws Exception {
		Properties properties = new Properties();
		properties.put("url", "jdbc:mysql://127.0.0.1:3306/electron?useSSL=false&useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull");
		properties.put("username", "root");
		properties.put("password", "Ab123123@");
		properties.put("maxActive", "20");
		properties.put("maxWait", "10000");
		connector.init(properties);
	}
}
