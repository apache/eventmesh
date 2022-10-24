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

package org.apache.eventmesh.connector.storage.jdbc.SQL;

import org.junit.Assert;
import org.junit.Test;

public class StorageSQLTest {
	
	public static final String TABLE_NAME = "cloud_event_test";

	MySQLStorageSQL storageSQL = new MySQLStorageSQL();
	
	@Test
	public void testReplySelectSQL() {
		String sql = storageSQL.replySelectSQL(TABLE_NAME, 5);
		Assert.assertEquals("select * from cloud_event_test where cloud_event_info_id in(?,?,?,?,?) and cloud_event_reply_data is not null", sql);
	}
	
	@Test
	public void testReplyResult() {
		String sql = storageSQL.replyResult(TABLE_NAME);
		Assert.assertEquals(sql , " update cloud_event_test  set  cloud_event_reply_data = ? , cloud_event_reply_state = 'NOTHING' where cloud_event_info_id = ?");
	}
	
	@Test
	public void testUpdateOffsetSQL() {
		String sql = storageSQL.updateOffsetSQL(TABLE_NAME);
		Assert.assertEquals(sql , "update cloud_event_test set cloud_event_state = 'SUCCESS'  where cloud_event_info_id = ?");
	}
}
