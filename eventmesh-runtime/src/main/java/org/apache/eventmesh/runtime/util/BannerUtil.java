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

    public static final String logo =
                       "           EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM          EMEMEMEM            EMEMEMEM           " + System.lineSeparator()
                     + "      EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM      EMEMEMEMEMEMEMEM       EMEMEMEMEMEMEME      " + System.lineSeparator()
                     + "    EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME     EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM    " + System.lineSeparator()
                     + "  EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME      EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME  " + System.lineSeparator()
                     + " EMEMEMEMEMEME                                     EMEMEMEMEMEME   EMEMEMEMEMEMEMEM    EMEMEMEMEMEM " + System.lineSeparator()
                     + "EMEMEMEMEM                                       EMEMEMEMEMEM         EMEMEMEMEM          EMEMEMEMEM" + System.lineSeparator()
                     + "EMEMEMEME                                       EMEMEMEMEME               EME              EMEMEMEME" + System.lineSeparator()
                     + "EMEMEMEME                                     EMEMEMEMEME                                  EMEMEMEME" + System.lineSeparator()
                     + "EMEMEMEME                                    EMEMEMEMEME                                   EMEMEMEME" + System.lineSeparator()
                     + "EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM      EMEMEMEMEME                                    EMEMEMEME" + System.lineSeparator()
                     + "EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME     EMEMEMEMEME                                      EMEMEMEME" + System.lineSeparator()
                     + "EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM      EMEMEMEMEME                                        EMEMEMEME" + System.lineSeparator()
                     + "EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME      EMEMEMEMEME                                         EMEMEMEME" + System.lineSeparator()
                     + "EMEMEMEME                            EMEMEMEMEME                                           EMEMEMEME" + System.lineSeparator()
                     + "EMEMEMEME                           EMEMEMEMEME                                            EMEMEMEME" + System.lineSeparator()
                     + "EMEMEMEME                         EMEMEMEMEMEM                                             EMEMEMEME" + System.lineSeparator()
                     + " EMEMEMEMEM                      EMEMEMEMEME                                             EMEMEMEMEME" + System.lineSeparator()
                     + " EMEMEMEMEMEM                  EMEMEMEMEMEM                                            EMEMEMEMEMEM " + System.lineSeparator()
                     + "  EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM      EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM  " + System.lineSeparator()
                     + "   EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM     EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME   " + System.lineSeparator()
                     + "     EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM      EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEM     " + System.lineSeparator()
                     + "        EMEMEMEMEMEMEMEMEMEMEMEMEMEM       EMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEMEME        ";

    public static final String name =
                      " ________                                 __      __       __                      __                " + System.lineSeparator()
                    + "|        \\                               |  \\    |  \\     /  \\                    |  \\          " + System.lineSeparator()
                    + "| EMEMEMEM__     __   ______   _______  _| EM_   | EM\\   /  EM  ______    _______ | EM____          " + System.lineSeparator()
                    + "| EM__   |  \\   /  \\ /      \\ |       \\|   EM \\  | EME\\ /  EME /      \\  /       \\| EM    \\ " + System.lineSeparator()
                    + "| EM  \\   \\EM\\ /  EM|  EMEMEM\\| EMEMEME\\\\EMEMEM  | EMEM\\  EMEM|  EMEMEM\\|  EMEMEME| EMEMEME\\" + System.lineSeparator()
                    + "| EMEME    \\EM\\  EM | EM    EM| EM  | EM | EM __ | EM\\EM EM EM| EM    EM \\EM    \\ | EM  | EM    " + System.lineSeparator()
                    + "| EM_____   \\EM EM  | EMEMEMEM| EM  | EM | EM|  \\| EM \\EME| EM| EMEMEMEM _\\EMEMEM\\| EM  | EM    " + System.lineSeparator()
                    + "| EM     \\   \\EME    \\EM     \\| EM  | EM  \\EM  EM| EM  \\E | EM \\EM     \\|       EM| EM  | EM " + System.lineSeparator()
                    + " \\EMEMEMEM    \\E      \\EMEMEME \\EM   \\EM   \\EMEM  \\EM      \\EM  \\EMEMEME \\EMEMEME  \\EM   \\EM";

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
