// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

//! Load-balancing across multiple EventMesh nodes (used by the HTTP
//! transport and multi-endpoint gRPC clients).
//!
//! Ported from `org.apache.eventmesh.common.loadbalance`.

use std::sync::Mutex;

use rand::Rng;

use crate::error::{EventMeshError, Result};

/// Configured load-balance strategy.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum LoadBalance {
    #[default]
    Random,
    WeightRandom,
    WeightRoundRobin,
}

/// A server endpoint, optionally weighted. Address format: `host:port` or
/// `host:port:weight` (weight defaults to 1 when omitted).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ServerNode {
    pub host: String,
    pub port: u16,
    pub weight: i32,
}

impl ServerNode {
    /// Parse `host:port` or `host:port:weight`.
    pub fn parse(addr: &str) -> Result<Self> {
        let parts: Vec<&str> = addr.split(':').collect();
        match parts.len() {
            2 => {
                let port = parts[1]
                    .parse::<u16>()
                    .map_err(|e| EventMeshError::Config(format!("bad port in {addr:?}: {e}")))?;
                Ok(Self {
                    host: parts[0].to_string(),
                    port,
                    weight: 1,
                })
            }
            3 => {
                let port = parts[1]
                    .parse::<u16>()
                    .map_err(|e| EventMeshError::Config(format!("bad port in {addr:?}: {e}")))?;
                let weight = parts[2]
                    .parse::<i32>()
                    .map_err(|e| EventMeshError::Config(format!("bad weight in {addr:?}: {e}")))?;
                if weight <= 0 {
                    return Err(EventMeshError::Config(format!(
                        "weight must be > 0: {addr:?}"
                    )));
                }
                Ok(Self {
                    host: parts[0].to_string(),
                    port,
                    weight,
                })
            }
            _ => Err(EventMeshError::Config(format!(
                "expected host:port[:weight], got {addr:?}"
            ))),
        }
    }

    pub fn addr(&self) -> String {
        format!("{}:{}", self.host, self.port)
    }
}

/// Stateful selector over a set of nodes.
pub enum LoadBalanceSelector {
    Random {
        nodes: Vec<ServerNode>,
    },
    WeightRandom {
        /// Nodes expanded into a weighted list for O(1) random pick.
        expanded: Vec<ServerNode>,
    },
    WeightRoundRobin {
        nodes: Vec<ServerNode>,
        /// Current weighted round-robin counters.
        counters: Mutex<Vec<i32>>,
    },
}

impl LoadBalanceSelector {
    /// Build a selector for the given nodes using the chosen strategy.
    pub fn new(nodes: Vec<ServerNode>, strategy: LoadBalance) -> Result<Self> {
        if nodes.is_empty() {
            return Err(EventMeshError::Config(
                "load-balance requires at least one node".into(),
            ));
        }
        Ok(match strategy {
            LoadBalance::Random => Self::Random { nodes },
            LoadBalance::WeightRandom => {
                let mut expanded = Vec::new();
                for n in &nodes {
                    for _ in 0..n.weight.max(1) {
                        expanded.push(n.clone());
                    }
                }
                Self::WeightRandom { expanded }
            }
            LoadBalance::WeightRoundRobin => {
                let counters = Mutex::new(vec![0; nodes.len()]);
                Self::WeightRoundRobin { nodes, counters }
            }
        })
    }

    /// Pick the next node.
    pub fn select(&self) -> &ServerNode {
        match self {
            Self::Random { nodes } => {
                let idx = rand::thread_rng().gen_range(0..nodes.len());
                &nodes[idx]
            }
            Self::WeightRandom { expanded } => {
                let idx = rand::thread_rng().gen_range(0..expanded.len());
                &expanded[idx]
            }
            Self::WeightRoundRobin { nodes, counters } => {
                // Smooth weighted round-robin (nginx-style).
                let mut guard = counters.lock().expect("counter lock poisoned");
                let total: i32 = nodes.iter().map(|n| n.weight).sum();
                let mut best = 0usize;
                for (i, n) in nodes.iter().enumerate() {
                    guard[i] += n.weight;
                    if guard[i] > guard[best] {
                        best = i;
                    }
                }
                guard[best] -= total;
                &nodes[best]
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_host_port() {
        let n = ServerNode::parse("1.2.3.4:10105").unwrap();
        assert_eq!(n.host, "1.2.3.4");
        assert_eq!(n.port, 10105);
        assert_eq!(n.weight, 1);
    }

    #[test]
    fn parse_weighted() {
        let n = ServerNode::parse("1.2.3.4:10105:5").unwrap();
        assert_eq!(n.weight, 5);
        assert_eq!(n.addr(), "1.2.3.4:10105");
    }

    #[test]
    fn random_selects_within_set() {
        let nodes = vec![
            ServerNode::parse("a:1").unwrap(),
            ServerNode::parse("b:2").unwrap(),
        ];
        let sel = LoadBalanceSelector::new(nodes.clone(), LoadBalance::Random).unwrap();
        for _ in 0..20 {
            let n = sel.select();
            assert!(nodes.contains(n));
        }
    }

    #[test]
    fn weight_round_robin_distributes_proportionally() {
        let nodes = vec![
            ServerNode::parse("a:1:5").unwrap(),
            ServerNode::parse("b:1:1").unwrap(),
        ];
        let sel = LoadBalanceSelector::new(nodes, LoadBalance::WeightRoundRobin).unwrap();
        let mut a = 0;
        for _ in 0..60 {
            if sel.select().host == "a" {
                a += 1;
            }
        }
        // ~5/6 should be 'a'.
        assert!(a > 35 && a < 65, "a={a}");
    }
}
