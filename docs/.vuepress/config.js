/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

module.exports = {
    title: 'EventMesh',
    description: 'EventMesh',
    head: [
        ['link', {rel: 'icon', href: '/logo.png'}]
    ],
    locales: {
        '/': {
            lang: 'English',
            title: 'EventMesh',
            description: ''
        },
        '/cn/': {
            lang: '中文',
            title: 'EventMesh',
            description: ''
        },
    },
    themeConfig: {
        locales: {
            '/': {
                selectText: 'Languages',
                label: 'English',
                ariaLabel: 'Languages',
                nav: [
                    {text: 'Community', link: '/en/community/'},
                    {
                        text: 'Blog',
                        link: '/en/blog/'
                    },
                    {
                        text: 'Documentation', link: '/en/documentation/'
                    },
                    {text: 'Github', link: 'https://github.com/WeBankFinTech/EventMesh'},
                ],
            },
            '/en/': {
                selectText: 'Languages',
                label: 'English',
                ariaLabel: 'Languages',
                nav: [
                    {text: 'Community', link: '/en/community/'},
                    {
                        text: 'Blog',
                        link: '/en/blog/'
                    },
                    {
                        text: 'Documentation', link: '/en/documentation/'
                    },
                    {text: 'Github', link: 'https://github.com/WeBankFinTech/EventMesh'},
                ],
            },
            '/cn/': {
                // 多语言下拉菜单的标题
                selectText: '选择语言',
                // 该语言在下拉菜单中的标签
                label: '简体中文',
                nav: [
                    {text: '社区', link: '/cn/community/'},
                    {
                        text: '博客',
                        link: '/cn/blog/'
                    },
                    {
                        text: '文档', link: '/en/documentation/'
                    },
                    {text: 'Github', link: 'https://github.com/WeBankFinTech/EventMesh'},
                ],
            }
        },

        sidebar: true
    }
}
