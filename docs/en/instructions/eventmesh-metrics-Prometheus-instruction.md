# Observe Metrics through Prometheus

## Download Prometheus

Official website：https://prometheus.io/

Download Prometheus locally：https://prometheus.io/download/

Select the corresponding version of your computer, download and unzip it

![Prometheus-download](../../images/Prometheus-download.png)

## Add YML configuration in Prometheus

If you are new to Prometheus, you can copy it directly： eventmesh-runtime/conf/prometheus.yml

For example：this was downloaded in win-64

![prometheus-yml](../../images/prometheus-yml.png)

Replace the file in the red box

If you know Prometheus well, you can configure it yourself. The default export port of eventmesh is 19090.

ps：If the port needs to be replaced, please modify:eventmesh-runtime/conf/eventmesh.properties中的

```properties
#prometheusPort
eventMesh.metrics.prometheus.port=19090
```

## Run Prometheus and EventMesh

Double click Prometheus.exe startup

run eventmesh-starter(reference [eventmesh-runtime-quickstart](eventmesh-runtime-quickstart.md))

run eventmesh-example(reference [eventmesh-sdk-java-quickstart](eventmesh-sdk-java-quickstart.md))

Open browser access：http://localhost:9090/

## Enter the metrics to be observed

input '**eventmesh_**' Relevant indicators will appear

![promethus-search](../../images/promethus-search.png)
