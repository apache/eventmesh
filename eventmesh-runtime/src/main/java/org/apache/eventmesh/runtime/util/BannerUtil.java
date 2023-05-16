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

package org.apache.eventmesh.runtime.util;

import lombok.extern.slf4j.Slf4j;

/**
 * EventMesh banner util
 */
@Slf4j
public class BannerUtil {

    private static final String logo =
                       "       EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME        EMEMEMEME               EMEMEMEME         " + System.lineSeparator()
                     + "   EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME       EMEMEMEMEMEMEMEME     EMEMEMEMEMEMEMEMEM     " + System.lineSeparator()
                     + "  EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM        EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME   " + System.lineSeparator()
                     + "EMEMEMEMEMEM                                        EMEMEMEMEM    EMEMEMEMEMEMEMEME    EMEMEMEMEME  " + System.lineSeparator()
                     + "EMEMEMEME                                         EMEMEMEMEM        EMEMEMEMEMEME        EMEMEMEME  " + System.lineSeparator()
                     + "EMEMEME                                          EMEMEMEME              EMEME             EMEMEMEM  " + System.lineSeparator()
                     + "EMEMEME                                        EMEMEMEME                                   EMEMEME  " + System.lineSeparator()
                     + "EMEMEME                                      EMEMEMEMEM                                    EMEMEME  " + System.lineSeparator()
                     + "EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM        EMEMEMEMEM                                     EMEMEME  " + System.lineSeparator()
                     + "EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM        EMEMEMEMEM                                       EMEMEME  " + System.lineSeparator()
                     + "EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM         EMEMEMEMEM                                        EMEMEME  " + System.lineSeparator()
                     + "EMEMEME                                EMEMEMEMEM                                          EMEMEME  " + System.lineSeparator()
                     + "EMEMEME                               EMEMEMEMEM                                           EMEMEME  " + System.lineSeparator()
                     + "EMEMEME                             EMEMEMEMEM                                             EMEMEME  " + System.lineSeparator()
                     + "EMEMEMEME                          EMEMEMEMEM                                            EMEMEMEME  " + System.lineSeparator()
                     + "EMEMEMEMEMEM                     EMEMEMEMEM                                           EMEMEMEMEMEM  " + System.lineSeparator()
                     + "  EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM      EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM    " + System.lineSeparator()
                     + "   EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME      EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME     " + System.lineSeparator()
                     + "       MEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM      EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME         ";

    private static final String name =
                       "                      _____                 _   __  __           _            " + System.lineSeparator()
                     + "                     | ____|_   _____ _ __ | |_|  \\/  | ___  ___| |__        " + System.lineSeparator()
                     + "                     |  _| \\ \\ / / _ \\ '_ \\| __| |\\/| |/ _ \\/ __| '_ \\ " + System.lineSeparator()
                     + "                     | |___ \\ V /  __/ | | | |_| |  | |  __/\\__ \\ | | |    " + System.lineSeparator()
                     + "                     |_____| \\_/ \\___|_| |_|\\__|_|  |_|\\___||___/_| |_|";

    public static void generateBanner() {
        String banner =
                          System.lineSeparator()
                        + System.lineSeparator()
                        + logo
                        + System.lineSeparator()
                        + name
                        + System.lineSeparator();
        if (log.isInfoEnabled()) {
            log.info(banner);
        } else {
            System.out.print(banner);
        }
    }
}
