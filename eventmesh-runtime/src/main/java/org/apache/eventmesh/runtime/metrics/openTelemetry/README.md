# Open Telemetry exporter

we can use Prometheus UI to see the metrics exported by openTelemetry

# How to run Prometheus

download Prometheus from https://prometheus.io/download/
remember to fix the [prometheus.yml](prometheus.yml)

---
or use docker
Start Prometheus instance with a configuration that sets up a HTTP collection job for  ```127.0.0.1:19090```

See [prometheus.yml](prometheus.yml)

```shell script
docker run --network="host" --rm -it \
    --name prometheus \
    -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
    prom/prometheus 

```

you can run the quickstart and open the Prometheus UI:
http://localhost:9090/graph?g0.expr=max_HTTPCost&g0.tab=1&g0.stacked=0&g0.show_exemplars=0&g0.range_input=1h


search the key word:

*max_HTTP_TPS*

*max_HTTPCost*

*avg_HTTPCost*

## special explanation
Prometheus runs on port 9090,Open telemetry exports data to port 19090,Prometheus will collect data from port 19090

the exporter is exporting the data in 'SummaryMetrics'(package org.apache.eventmesh.runtime.metrics.http;)

The export mechanism I set is to export every 3 seconds, because QuickStart only has httpcost at a short time. If the interval is set too long, it will always be 0. In practical application, I think it should be set to more than 30 seconds
