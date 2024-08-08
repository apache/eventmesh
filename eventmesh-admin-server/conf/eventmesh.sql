-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- export eventmesh database
CREATE DATABASE IF NOT EXISTS `eventmesh` /*!40100 DEFAULT CHARACTER SET utf8 COLLATE utf8_bin */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `eventmesh`;

-- export table eventmesh.event_mesh_data_source structure
CREATE TABLE IF NOT EXISTS `event_mesh_data_source` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `dataType` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `description` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin DEFAULT NULL,
  `configuration` text CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
  `configurationClass` varchar(200) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL DEFAULT '',
  `region` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `createUid` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `updateUid` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

-- export table eventmesh.event_mesh_job_info structure
CREATE TABLE IF NOT EXISTS `event_mesh_job_info` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `jobID` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
  `jobDesc` varchar(50) COLLATE utf8_bin NOT NULL,
  `taskID` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `transportType` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `sourceData` int NOT NULL DEFAULT '0',
  `targetData` int NOT NULL DEFAULT '0',
  `jobState` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL DEFAULT '',
  `jobType` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL DEFAULT '',
  `fromRegion` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `runningRegion` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `createUid` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `updateUid` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `jobID` (`jobID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

-- export table eventmesh.event_mesh_mysql_position structure
CREATE TABLE IF NOT EXISTS `event_mesh_mysql_position` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `jobID` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `serverUUID` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin DEFAULT NULL,
  `address` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
  `position` bigint DEFAULT NULL,
  `gtid` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin DEFAULT NULL,
  `currentGtid` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin DEFAULT NULL,
  `timestamp` bigint DEFAULT NULL,
  `journalName` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin DEFAULT NULL,
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `jobID` (`jobID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin ROW_FORMAT=DYNAMIC;

-- export table eventmesh.event_mesh_position_reporter_history structure
CREATE TABLE IF NOT EXISTS `event_mesh_position_reporter_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `record` text CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
  `address` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL DEFAULT '',
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `job` (`job`),
  KEY `address` (`address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='record position reporter changes';

-- export table eventmesh.event_mesh_runtime_heartbeat structure
CREATE TABLE IF NOT EXISTS `event_mesh_runtime_heartbeat` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `adminAddr` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
  `runtimeAddr` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
  `jobID` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `reportTime` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT 'runtime local report time',
  `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `runtimeAddr` (`runtimeAddr`),
  KEY `jobID` (`jobID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

-- export table eventmesh.event_mesh_runtime_history structure
CREATE TABLE IF NOT EXISTS `event_mesh_runtime_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `address` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL DEFAULT '',
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `address` (`address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin ROW_FORMAT=DYNAMIC COMMENT='record runtime task change history';

-- export table eventmesh.event_mesh_task_info structure
CREATE TABLE IF NOT EXISTS `event_mesh_task_info` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `taskID` varchar(50) COLLATE utf8_bin NOT NULL,
  `taskName` varchar(50) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
  `taskDesc` varchar(50) COLLATE utf8_bin NOT NULL,
  `taskState` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '' COMMENT 'taskstate',
  `sourceRegion` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `targetRegion` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `createUid` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `updateUid` varchar(50) COLLATE utf8_bin NOT NULL DEFAULT '',
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `taskID` (`taskID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

-- export table eventmesh.event_mesh_verify structure
CREATE TABLE IF NOT EXISTS `event_mesh_verify` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `taskID` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `recordID` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `recordSig` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `connectorName` varchar(200) COLLATE utf8_bin DEFAULT NULL,
  `connectorStage` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `position` text COLLATE utf8_bin DEFAULT NULL,
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
