[![Build Status](https://www.travis-ci.org/WeBankFinTech/DeFiBus.svg?branch=master)](https://www.travis-ci.org/WeBankFinTech/EventMesh) [![Coverage Status](https://coveralls.io/repos/github/WeBankFinTech/DeFiBus/badge.svg?branch=master)](https://coveralls.io/github/WeBankFinTech/EventMesh?branch=master)

## What is an Event Mesh?
An event mesh is a configurable and dynamic infrastructure layer for distributing events among decoupled applications, cloud services and devices. It enables event communications to be governed, flexible, reliable and fast. An event mesh is created and enabled through a network of interconnected event brokers.

In other words, an event mesh is an architecture layer that allows events from one application to be dynamically routed and received by any other application no matter where these applications are deployed (no cloud, private cloud, public cloud). This layer is composed of a network of event brokers.

## How is “event mesh” different from service mesh?
Event mesh complements service mesh. It is a layer parallel to service mesh and completes the application architecture by providing the full set of inter-application communication patterns: service mesh for RESTful and general request/reply interactions; event mesh for asynchronous, event-based interactions. Refer to the layering diagram below for position of each in an application stack.

A service mesh is a configurable infrastructure layer for microservices applications that makes communication flexible, reliable and fast. It is promoted by giants in the industry such as Google, Microsoft, IBM, Red Hat, Pivotal and others and is now included with Kubernetes, OpenShift, and PKS by Istio/Envoy. The dataplane portion (i.e., Envoy) is implemented through a sidecar proxy and provides:

* Service discovery
* Load balancing
* Encryption
* Authentication and authorization
* Circuit breaker support

Both meshes are similar in that they enable better communication between applications by putting certain functions into a layer between the network and the application. However, there are a few important distinctions:

Service mesh connects microservices in cloud environments – Kubernetes only today – with the promise of enabling this communication between different Kubernetes clusters and perhaps other clouds in the future.
Event mesh connects not only microservices but also legacy applications, cloud-native services, devices, and data sources/sinks and these can operate both in cloud and non-cloud environments. An event mesh can connect any event source to any event handler.

This diagram shows where an event mesh fits in an application stack relative to other technologies such as service mesh.

## What are the core capabilities of an event mesh?
The generic capabilities of an event mesh:

A network of interconnected event brokers that can be deployed in any cloud, PaaS or non-cloud (so it includes all capabilities of an event broker but is distributed)
Provides dynamic distribution of events so that event consumers can receive events from any event producer, no matter where the producer and consumer are attached to the mesh, without the need for configuration of event routing
In other words, an ‘event mesh’ is:

* inherently ‘event-driven;’
* created by connecting event brokers;
* environment agnostic (can be deployed anywhere); and,
* dynamic.

## Contacts
微信/QQ群：

![wechat_qr](./docs/images/wechat_helper.png)


