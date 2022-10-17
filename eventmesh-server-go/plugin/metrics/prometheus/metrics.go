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

package prometheus

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin"
	"github.com/apache/incubator-eventmesh/eventmesh-server-go/plugin/metrics"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"net/http"
	"sync"
)

func init() {
	plugin.Register("prometheus", getDefaultMetrics())
}

func getDefaultMetrics() *Metrics {
	return &Metrics{
		counters:   make(map[string]prometheus.Collector),
		histograms: make(map[string]prometheus.Collector)}
}

type Metrics struct {
	counters   map[string]prometheus.Collector
	histograms map[string]prometheus.Collector
	lock       sync.Mutex
	once       sync.Once
	config     *Config
}

const nameSpace = "eventmesh"
const subSystem = "runtime"

const (
	histogram = iota
	counter
)

func (p *Metrics) Type() string {
	return metrics.PluginType
}

func (p *Metrics) Setup(name string, dec plugin.Decoder) error {
	config := NewDefaultConfig()
	err := dec.Decode(config)
	if err != nil {
		return err
	}
	p.Init(config)
	return nil
}

// Init try to init metrics, include exposing http endpoint
func (p *Metrics) Init(config *Config) {
	p.once.Do(func() {
		p.config = config
		p.exposeEndpoint()
	})
}

// exposeEndpoint expose http endpoint
func (p *Metrics) exposeEndpoint() {
	go func() {
		http.Handle("/metrics", promhttp.Handler())
		http.ListenAndServe(fmt.Sprintf(":%s", p.config.EndpointPort), nil)
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
	case counter:
		return p.counters[name]
	case histogram:
		return p.histograms[name]
	default:
		panic("prometheus metrics plugin get collector error, illegal collector type")
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
	case counter:
		collector = prometheus.NewCounterVec(
			prometheus.CounterOpts{
				Namespace: nameSpace,
				Subsystem: subSystem,
				Name:      name,
			}, []string{"label"},
		)
		p.counters[name] = collector
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
	default:
		panic("prometheus metrics plugin register collector error, illegal collector type")
	}
	prometheus.MustRegister(collector)
	return collector
}

func (p *Metrics) IncCount(name string, label string) {
	collector := p.loadCollector(name, counter).(*prometheus.CounterVec)
	collector.With(prometheus.Labels{"label": label}).Inc()
}

func (p *Metrics) AddCount(name string, label string, val float64) {
	collector := p.loadCollector(name, counter).(*prometheus.CounterVec)
	collector.With(prometheus.Labels{"label": label}).Add(val)
}

func (p *Metrics) RecordLatency(name string, label string, latency float64) {
	collector := p.loadCollector(name, histogram).(*prometheus.HistogramVec)
	collector.With(prometheus.Labels{"label": label}).Observe(latency)
}
