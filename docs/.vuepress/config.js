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
    nav: [
      { text: '指南', link: '/' },
      { 
        text: '特性', 
        link: '/features/',
        items: [
          { text: '0-introduction', link: '/features/0-introduction' },
          { text: '1-request-response-call', link: '/feature/1-request-response-call' },
          { text: '2-dark-launch', link: '/features/2-dark-launch' },
          { text: '3-circuit-break-mechanism', link: '/feature/3-circuit-break-mechanism' },
          { text: '4-invoke-service-nearby', link: '/features/4-invoke-service-nearby' },
          { text: '5-multi-active', link: '/features/5-multi-active' },
          { text: '6-dynamic-adjust-queue', link: '/features/6-dynamic-adjust-queue' },
          { text: '7-isolation-mechanism', link: '/features/7-isolation-mechanism' },
          { text: '8-fault-tolerant', link: '/features/8-fault-tolerant' },
          { text: '9-publish-type', link: '/features/9-publish-type' },
          { text: '10-flow-control', link: '/features/10-flow-control' },
        ]
      },
      { 
        text: '说明', 
        items: [
          { text: 'eventmesh-emesher', link: '/instructions/eventmesh-emesher-quickstart' },
          { text: 'eventmesh-store', link: '/instructions/eventmesh-store-quickstart' },
          { text: 'eventmesh-sdk-java', link: '/instructions/eventmesh-sdk-java-quickstart' },
        ]
      },
      { text: 'Github', link: 'https://github.com/WeBankFinTech/EventMesh'},
    ],
    sidebar: true
  }
}