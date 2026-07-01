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

package org.apache.eventmesh.connector.jdbc.sink.hibernate;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

public final class HibernateConfiguration {

    public static HibernateConfigurationBuilder newBuilder() {
        return new HibernateConfigurationBuilder();
    }

    public static class HibernateConfigurationBuilder {

        private Configuration configuration;

        public HibernateConfigurationBuilder() {
            this.configuration = new Configuration();
            this.configuration.setProperty("hibernate.connection.provider_class", DruidConnectionProvider.class.getName());
        }

        public HibernateConfigurationBuilder withUser(String username) {
            configuration.setProperty("username", username);
            return this;
        }

        public HibernateConfigurationBuilder withPassword(String password) {
            configuration.setProperty("password", password);
            return this;
        }

        public HibernateConfigurationBuilder withUrl(String url) {
            configuration.setProperty("url", url);
            return this;
        }

        public HibernateConfigurationBuilder withDruidMaxActive(String maxActive) {
            configuration.setProperty("maxActive", maxActive);
            return this;
        }

        public HibernateConfigurationBuilder withShowSql(boolean showSql) {
            configuration.setProperty("hibernate.show_sql", Boolean.toString(showSql));
            return this;
        }

        public SessionFactory build() {
            ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(configuration.getProperties())
                .build();
            return configuration.buildSessionFactory(serviceRegistry);
        }

    }
}
