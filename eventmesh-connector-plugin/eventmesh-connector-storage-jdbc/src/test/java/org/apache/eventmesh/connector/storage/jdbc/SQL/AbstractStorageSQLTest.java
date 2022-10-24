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

public class AbstractStorageSQLTest {

    MySQLStorageSQL storageSQL = new MySQLStorageSQL();

    @Test
    public void testLocationEventSQL() {
        String sql = storageSQL.locationEventSQL(StorageSQLTest.TABLE_NAME);
        Assert.assertEquals(sql,
            "update cloud_event_test set json_set( cloud_event_consume_location , ? ,? ) where cloud_event_info_id > ? and json_extract(cloud_event_consume_location, ?) is null limit 200");
    }

    @Test
    public void testQueryLocationEventSQL() {
        String sql = storageSQL.queryLocationEventSQL(StorageSQLTest.TABLE_NAME);
        Assert.assertEquals(sql,
            "select * from cloud_event_test where cloud_event_info_id > ? and JSON_CONTAINS_PATH(cloud_event_consume_location, 'one', ?)");
    }

    @Test
    public void testSelectFastMessageSQL() {
        String sql = storageSQL.selectFastMessageSQL(StorageSQLTest.TABLE_NAME);
        Assert.assertEquals(sql,
            "select 'cloud_event_test' as tableName , cloud_event_info_id from cloud_event_test  order by  cloud_event_info_id limit 1");
    }

    @Test
    public void testSelectLastMessageSQL() {
        String sql = storageSQL.selectLastMessageSQL(StorageSQLTest.TABLE_NAME);
        Assert.assertEquals(sql,
            "select 'cloud_event_test' as tableName , cloud_event_info_id from cloud_event_test  order by  cloud_event_info_id desc  limit 1");
    }
}
