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

-- --------------------------------------------------------
-- 主机:                           127.0.0.1
-- 服务器版本:                        8.0.36 - MySQL Community Server - GPL
-- 服务器操作系统:                      Win64
-- HeidiSQL 版本:                  11.3.0.6295
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- 导出 eventmesh 的数据库结构
CREATE DATABASE IF NOT EXISTS `eventmesh` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `eventmesh`;

-- 导出  表 eventmesh.event_mesh_data_source 结构
CREATE TABLE IF NOT EXISTS `event_mesh_data_source` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `dataType` int unsigned NOT NULL,
  `description` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `configuration` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `createUid` int NOT NULL,
  `updateUid` int NOT NULL,
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 数据导出被取消选择。

-- 导出  表 eventmesh.event_mesh_job_info 结构
CREATE TABLE IF NOT EXISTS `event_mesh_job_info` (
  `jobID` int unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `transportType` int unsigned DEFAULT NULL COMMENT 'JobTransportType',
  `sourceData` int unsigned DEFAULT NULL COMMENT 'data_source表',
  `targetData` int unsigned DEFAULT NULL,
  `state` tinyint unsigned NOT NULL COMMENT 'JobState',
  `jobType` tinyint unsigned NOT NULL COMMENT 'connector,mesh,func,...',
  `createUid` int unsigned NOT NULL,
  `updateUid` int unsigned NOT NULL,
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`jobID`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 数据导出被取消选择。

-- 导出  表 eventmesh.event_mesh_mysql_position 结构
CREATE TABLE IF NOT EXISTS `event_mesh_mysql_position` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `jobID` int unsigned NOT NULL,
  `address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `position` bigint DEFAULT NULL,
  `timestamp` bigint DEFAULT NULL,
  `journalName` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `jobID` (`jobID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC;

-- 数据导出被取消选择。

-- 导出  表 eventmesh.event_mesh_position_reporter_history 结构
CREATE TABLE IF NOT EXISTS `event_mesh_position_reporter_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job` int NOT NULL,
  `record` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `address` varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `job` (`job`),
  KEY `address` (`address`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='记录position上报者变更时，老记录';

-- 数据导出被取消选择。

-- 导出  表 eventmesh.event_mesh_runtime_heartbeat 结构
CREATE TABLE IF NOT EXISTS `event_mesh_runtime_heartbeat` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `adminAddr` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `runtimeAddr` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `jobID` int unsigned DEFAULT NULL,
  `reportTime` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'runtime本地上报时间',
  `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `runtimeAddr` (`runtimeAddr`),
  KEY `jobID` (`jobID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 数据导出被取消选择。

-- 导出  表 eventmesh.event_mesh_runtime_history 结构
CREATE TABLE IF NOT EXISTS `event_mesh_runtime_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job` int NOT NULL,
  `address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `address` (`address`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='记录runtime上运行任务的变更';

-- 数据导出被取消选择。

/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
