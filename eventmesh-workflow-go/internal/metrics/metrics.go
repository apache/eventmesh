// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package metrics

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/log"
	conf "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/config"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"net/http"
	"sync"
)

func init() {
	prometheusMetrics = getPrometheusMetricsByConfig()
}

var prometheusMetrics *Metrics

func Inc(name string, label string) {
	collector := prometheusMetrics.loadCollector(name, gauge).(*prometheus.GaugeVec)
	collector.With(prometheus.Labels{"label": label}).Inc()
}

func Add(name string, label string, val float64) {
	collector := prometheusMetrics.loadCollector(name, gauge).(*prometheus.GaugeVec)
	collector.With(prometheus.Labels{"label": label}).Add(val)
}

func Sub(name string, label string, val float64) {
	collector := prometheusMetrics.loadCollector(name, gauge).(*prometheus.GaugeVec)
	collector.With(prometheus.Labels{"label": label}).Sub(val)
}

func Dec(name string, label string) {
	collector := prometheusMetrics.loadCollector(name, gauge).(*prometheus.GaugeVec)
	collector.With(prometheus.Labels{"label": label}).Dec()
}

func RecordLatency(name string, label string, latency float64) {
	collector := prometheusMetrics.loadCollector(name, histogram).(*prometheus.HistogramVec)
	collector.With(prometheus.Labels{"label": label}).Observe(latency)
}

func getPrometheusMetricsByConfig() *Metrics {
	config := conf.Get()

	port := ""
	if !checkMetricsConfig(config) {
		port = defaultEndPoint
	} else {
		port = config.Metrics.EndpointPort
	}

	m := &Metrics{
		counters:   make(map[string]prometheus.Collector),
		histograms: make(map[string]prometheus.Collector),
		port:       port,
	}
	m.Init()
	return m
}

func checkMetricsConfig(config *conf.Config) bool {
	if config == nil || len(config.Metrics.EndpointPort) == 0 {
		return false
	}
	return true
}

type Metrics struct {
	counters   map[string]prometheus.Collector
	histograms map[string]prometheus.Collector
	gauges     map[string]prometheus.Collector
	lock       sync.Mutex
	once       sync.Once
	port       string
}

const (
	nameSpace       = "eventmesh"
	subSystem       = "workflow"
	defaultEndPoint = "19090"
)

const (
	histogram = iota
	gauge
)

// Init try to init metrics, include exposing http endpoint
func (p *Metrics) Init() {
	p.once.Do(func() {
		p.exposeEndpoint()
	})
}

// exposeEndpoint expose http endpoint
func (p *Metrics) exposeEndpoint() {
	go func() {
		http.Handle("/metrics", promhttp.Handler())
		err := http.ListenAndServe(fmt.Sprintf(":%s", p.port), nil)
		if err != nil {
			log.Errorf("fail to listen prometheus endpoint port %s, err=%v", p.port, err)
		}
	}()
}

// loadCollector load collector by name and collectorType
func (p *Metrics) loadCollector(name string, collectorType int) prometheus.Collector {
	if collector := p.getCollectorByNameAndType(name, collectorType); collector != nil {
		return collector
	}
	return p.registerNewCollector(name, collectorType)
}

func (p *Metrics) getCollectorByNameAndType(name string, collectorType int) prometheus.Collector {
	switch collectorType {
	case histogram:
		return p.histograms[name]
	case gauge:
		return p.histograms[name]
	default:
		panic("prometheus metrics get collector error, illegal collector type")
	}
}

// registerNewCollector create and register new collector of collectorType
func (p *Metrics) registerNewCollector(name string, collectorType int) prometheus.Collector {
	p.lock.Lock()
	defer p.lock.Unlock()

	if collector := p.getCollectorByNameAndType(name, collectorType); collector != nil {
		return collector
	}

	var collector prometheus.Collector
	switch collectorType {
	case histogram:
		collector = prometheus.NewHistogramVec(
			prometheus.HistogramOpts{
				Namespace: nameSpace,
				Subsystem: subSystem,
				Name:      name,
				Buckets:   prometheus.ExponentialBuckets(1, 2, 13),
			}, []string{"label"},
		)
		p.histograms[name] = collector
	case gauge:
		collector = prometheus.NewGaugeVec(
			prometheus.GaugeOpts{
				Namespace: nameSpace,
				Subsystem: subSystem,
				Name:      name,
			}, []string{"label"},
		)
		p.gauges[name] = collector
	default:
		panic("prometheus metrics plugin register collector error, illegal collector type")
	}
	prometheus.MustRegister(collector)
	return collector
}
