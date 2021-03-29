---
layout: home
header:
  title: Apache EventMesh
  text: >
    EventMesh is a dynamic cloud-native eventing infrastructure used to decouple the application and backend middleware layer,
    which supports a wide range of use cases that encompass complex multi-cloud, widely distributed topologies using diverse technology stacks.
  action: # action button is optional
    label: Get Started!
    url: 'https://github.com/WeBankFinTech/EventMesh'


sections:
  - type: services.html
    section_id: services
    #background_style: bg-info
    title: Features
    services:
      - title: Event driven
        text: Event-driven architecture can minimize coupling and well extend and adapt different types of service components
        icon: fa-google-drive fab
        # url: https://startbootstrap.com/ text-info
      - title: Event governance
        text: You can configure the event scheme and monitor the event with the relevant metrics
        icon: fa-whmcs fab
      - title: Dynamic routing
        text: Supports event filtering , and events can be dynamically routed to various service nodes
        icon: fa-route fas
      - title: Cloud native
        text: Supports container-oriented deployment, micro-service oriented, event orchestration and scheduling
        icon: fa-cloud fas
      - title: Flow control
        text: Support flow control, fusing and retry to ensure high availability of services
        icon: fa-toggle-on fas
      - title: Load balance
        text: Supports clustering mode and sidecar mode deployment
        icon: fa-balance-scale fas

  - type: portfolio.html
    # this section has always ID 'portfolio'
    #section_id: portfolio
    #background_style: bg-dark
    title: Instructions
    projects:
      - title: EventMesh Ecosystem
        text: What is EventMesh
        # the images are located in:
        # img/portfolio/fullsize
        # img/portfolio/thumbnails
        icon: eventmesh-define.png
        url: '#'
      - title: EventMesh Architecture
        text: EventMesh ability and architecture
        icon: eventmesh-runtime.png
        url: '#'
      - title: EventMesh Cloud Native
        text: Panels and cloud native deployment
        icon: eventmesh-panels.png
        url: '#'

  - type: aside.html
    section_id: aside
    title: Free Download at Start Bootstrap!
    actions:
      - title: Download Now!
        url: https://startbootstrap.com/themes/creative/
        class: btn-light

  - type: members.html
    section_id: members
    title: Use Case
    background_style: bg-info text-white
    members:
      - title:
        text:
        image: assets/img/members/webank.png
        url: 'www.webank.com'

  - type: timeline.html
    section_id: timeline
    title: Major Achievements!
    background_style: bg-dark text-primary
    last_image: assets/img/timeline-end.png
    actions:
      - image: assets/img/portfolio/thumbnails/1.jpg
        title: >+
          2017-2018
          **Humble Beginnings**
        text: >-
          We begun with small group of people willing to work hard and make our
          teaching skills worth , in front of all others!
      - image: assets/img/portfolio/thumbnails/2.jpg
        title: >+
          November 2019
          An Coaching started
        text: >-
          We started to gather like minded people and started our stategies
          and future plans to them. As a result , interested people joined us!

  - type: contact.html
    section_id: contacts
    title: Let's Get In Touch!
    text: >-
      Ready to start this awesome project with us? Send us an email
      and we will get back to you as soon as possible!
    actions:
    - title: E-Mail
      icon: fa-envelope
      text: -for subscribe:<br/>users-subscribe@eventmesh.incubator.apache.org<br/>dev-subscribe@eventmesh.incubator.apache.org<br/>-for contact:<br/>users@eventmesh.apache.org dev@eventmesh.apache.org
      # url: mailto:contact@yourwebsite.com
    - title: Twitter:<a href="https://twitter.com/ASFEventMesh">@ASFEventMesh</a>
      icon: fa-twitter
      icon_type: fab
      url: '#'
    - title: Facebook
      icon: fa-facebook
      icon_type: fab
      url: '#'
    - title: WeChat Official Account
      icon: fa-weixin
      text: <img src="assets/img/wechat.jpg" alt="wechat official account">
      icon_type: fab
      url: '#'

---
