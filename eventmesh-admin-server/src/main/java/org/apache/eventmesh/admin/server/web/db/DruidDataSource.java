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

package org.apache.eventmesh.admin.server.web.db;


import org.apache.eventmesh.admin.server.web.utils.EncryptUtil;
import org.apache.eventmesh.admin.server.web.utils.ParamType;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import lombok.extern.slf4j.Slf4j;

@Configuration
@ComponentScan
@Slf4j
public class DruidDataSource {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.initialSize}")
    private int initialSize;

    @Value("${spring.datasource.minIdle}")
    private int minIdle;

    @Value("${spring.datasource.maxActive}")
    private int maxActive;

    @Value("${spring.datasource.maxWait}")
    private int maxWait;

    @Value("${spring.datasource.timeBetweenEvictionRunsMillis}")
    private int timeBetweenEvictionRunsMillis;

    @Value("${spring.datasource.minEvictableIdleTimeMillis}")
    private int minEvictableIdleTimeMillis;

    @Value("${spring.datasource.validationQuery}")
    private String validationQuery;

    @Value("${spring.datasource.testWhileIdle}")
    private boolean testWhileIdle;

    @Value("${spring.datasource.testOnBorrow}")
    private boolean testOnBorrow;

    @Value("${spring.datasource.testOnReturn}")
    private boolean testOnReturn;

    @Value("${spring.datasource.poolPreparedStatements}")
    private boolean poolPreparedStatements;

    @Value("${spring.datasource.maxPoolPreparedStatementPerConnectionSize}")
    private int maxPoolPreparedStatementPerConnectionSize;

    @Value("${spring.datasource.filters}")
    private String filters;

    @Value("{spring.datasource.connectionProperties}")
    private String connectionProperties;

    @Value("${sysPubKey}")
    private String sysPubKeyStr;

    @Value("${appPrivKey}")
    private String appPrivKeyStr;


    @Bean
    @Primary
    public DataSource dataSource() throws Exception {
        try (com.alibaba.druid.pool.DruidDataSource datasource = new com.alibaba.druid.pool.DruidDataSource()) {
            datasource.setUrl(this.dbUrl);
            datasource.setUsername(username);
            datasource.setPassword(rsaDecrypt(sysPubKeyStr, appPrivKeyStr, password));
            datasource.setDriverClassName(driverClassName);
            datasource.setInitialSize(initialSize);
            datasource.setMinIdle(minIdle);
            datasource.setMaxActive(maxActive);
            datasource.setMaxWait(maxWait);
            datasource.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
            datasource.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
            datasource.setValidationQuery(validationQuery);
            datasource.setTestWhileIdle(testWhileIdle);
            datasource.setTestOnBorrow(testOnBorrow);
            datasource.setTestOnReturn(testOnReturn);
            datasource.setPoolPreparedStatements(poolPreparedStatements);
            datasource.setMaxPoolPreparedStatementPerConnectionSize(maxPoolPreparedStatementPerConnectionSize);
            try {
                datasource.setFilters(filters);
            } catch (SQLException e) {
                log.error("druid configuration initialization filter", e);
            }
            datasource.setConnectionProperties(connectionProperties);

            return datasource;
        }
    }

    public static String rsaDecrypt(String sysPubKeyStr, String appPrivKeyStr, String encrtyptText) throws IOException {
        if (StringUtils.isNotBlank(encrtyptText) && encrtyptText.length() > "{RSA}".length() && encrtyptText.startsWith("{RSA}")) {
            String text = encrtyptText.startsWith("{RSA}") ? encrtyptText.substring("{RSA}".length()) : encrtyptText;

            try {
                return EncryptUtil.decrypt(ParamType.STRING, sysPubKeyStr, ParamType.STRING, appPrivKeyStr, ParamType.STRING, text);
            } catch (Exception e) {
                throw new RuntimeException("decrypt error", e);
            }
        } else {
            return encrtyptText;
        }
    }

}
