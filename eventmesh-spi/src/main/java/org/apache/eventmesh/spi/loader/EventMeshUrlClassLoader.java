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

package org.apache.eventmesh.spi.loader;

import org.apache.commons.collections4.CollectionUtils;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class EventMeshUrlClassLoader extends URLClassLoader {

    public static EventMeshUrlClassLoader getInstance() {
        return EventMeshUrlClassLoaderHolder.instance;
    }

    /**
     * Appends the specified URL to the list of URLs to search for classes and resources.
     * <p>
     * If the URL specified is {@code null} or is already in the
     * list of URLs, or if this loader is closed, then invoking this
     * method has no effect.
     * <p>
     * More detail see {@link URLClassLoader#addURL(URL)}
     * @param urls
     */
    public void addUrls(List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            return;
        }
        urls.forEach(this::addURL);
    }

    private EventMeshUrlClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    private static class EventMeshUrlClassLoaderHolder {
        private static EventMeshUrlClassLoader instance = new EventMeshUrlClassLoader(new URL[0], Thread.currentThread().getContextClassLoader());
    }
}
