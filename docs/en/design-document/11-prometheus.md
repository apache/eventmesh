# Observe Metrics with Prometheus

## Prometheus

[Prometheus](https://prometheus.io/docs/introduction/overview/) is an open-source system monitoring and alerting toolkit that collects and stores the metrics as time-series data. EventMesh exposes a collection of metrics data that could be scraped and analyzed by Prometheus. Please follow [the "First steps with Prometheus" tutorial](https://prometheus.io/docs/introduction/first_steps/) to download and install the latest release of Prometheus.

## Edit Prometheus Configuration

The `eventmesh-runtime/conf/prometheus.yml` configuration file specifies the port of the metrics HTTP endpoint. The default metrics port is `19090`.

```properties
eventMesh.metrics.prometheus.port=19090
```

Please refer to [the Prometheus configuration guide](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#scrape_config) to add the EventMesh metrics as a scrape target in the configuration file. Here's the minimum configuration that creates a job with the name `eventmesh` and the endpoint `http://localhost:19090`:

```yaml
scrape_configs:
  - job_name: "eventmesh"
    static_configs:
      - targets: ["localhost:19090"]
```

Please navigate to the Prometheus dashboard (e.g. `http://localhost:9090`) to view the list of metrics exported by EventMesh, which are prefixed with `eventmesh_`.
