module.exports = {
  title: 'EventMesh',
  description: 'EventMesh',
  head: [
    ['link', { rel: 'icon', href: '/logo.png' }]
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
          { text: 'Community', link: '/en/community/' },
          { 
            text: 'Blog', 
            link: '/en/blog/'
          },
          { 
            text: 'Documentation', link: '/en/documentation/'
          },
          { text: 'Github', link: 'https://github.com/WeBankFinTech/EventMesh'},
        ],
      },
      '/en/': {
        selectText: 'Languages',
        label: 'English',
        ariaLabel: 'Languages',
        nav: [
          { text: 'Community', link: '/en/community/' },
          { 
           text: 'Blog', 
            link: '/en/blog/'
          },
          { 
            text: 'Documentation', link: '/en/documentation/'
          },
          { text: 'Github', link: 'https://github.com/WeBankFinTech/EventMesh'},
        ],
      },
      '/cn/': {
        // 多语言下拉菜单的标题
        selectText: '选择语言',
        // 该语言在下拉菜单中的标签
        label: '简体中文',
        nav: [
          { text: '社区', link: '/cn/community/' },
          { 
            text: '博客', 
            link: '/cn/blog/'
          },
          { 
            text: '文档', link: '/en/documentation/'
          },
          { text: 'Github', link: 'https://github.com/WeBankFinTech/EventMesh'},
        ],
      }
    },
    
    sidebar: true
  }
}
