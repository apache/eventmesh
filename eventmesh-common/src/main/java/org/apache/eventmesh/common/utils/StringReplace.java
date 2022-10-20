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

package org.apache.eventmesh.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StringReplace {

    private List<Paragraph> paragraphList = new ArrayList<>();


    public StringReplace(String data) {
        String[] stringArray = data.split("\\{");
        for (int i = 0; i < stringArray.length; i++) {
            String splitString = stringArray[i];
            if (Objects.equals(splitString, "")) {
                continue;
            } else {
                if (splitString.indexOf('}') == -1) {
                    Paragraph paragraph = new Paragraph();
                    paragraph.splitString = splitString;
                    paragraphList.add(paragraph);
                    continue;
                }
            }
            String[] tmp = splitString.split("}");
            Paragraph paragraph = new Paragraph();
            paragraph.key = tmp[0];
            paragraphList.add(paragraph);

            if (tmp.length == 2) {
                paragraph = new Paragraph();
                paragraph.splitString = tmp[1];
                paragraphList.add(paragraph);
            }
        }
    }

    public String replace(Map<String, String> values) {
        StringBuffer sb = new StringBuffer();
        for (Paragraph paragraph : paragraphList) {
            if (Objects.nonNull(paragraph.splitString)) {
                sb.append(paragraph.splitString);
            } else {
                sb.append(values.get(paragraph.key));
            }
        }
        return sb.toString();
    }

    public String replace(List<String> values) {
        StringBuffer sb = new StringBuffer();
        int i = 0;
        for (Paragraph paragraph : paragraphList) {
            if (Objects.nonNull(paragraph)) {
                sb.append(paragraph.splitString);
            } else {
                sb.append(values.get(i++));
            }
        }
        return sb.toString();
    }

    public String replace(Object[] values) {
        StringBuffer sb = new StringBuffer();
        int i = 0;
        for (Paragraph paragraph : paragraphList) {
            if (Objects.nonNull(paragraph.splitString)) {
                sb.append(paragraph.splitString);
            } else {
                sb.append(values[i++]);
            }
        }
        return sb.toString();
    }

    public String replaceObject(Object values) {
        return null;
    }

    private static class Paragraph {

        private String splitString;

        private String key;
    }
}
