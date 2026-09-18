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

//! Load-balancing across multiple EventMesh nodes, used by the HTTP transport.
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

/// A weighted server endpoint supplied by the validated HTTP configuration.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ServerNode {
    pub host: String,
    pub port: u16,
    pub weight: i32,
}

impl ServerNode {
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
        nodes: Vec<ServerNode>,
        /// Precomputed sum of all node weights (each clamped to ≥ 1).
        /// Stored as `i64` to avoid overflow on large weights.
        total_weight: i64,
    },
    WeightRoundRobin {
        nodes: Vec<ServerNode>,
        /// Precomputed sum of all node weights (each clamped to ≥ 1).
        total_weight: i64,
        /// Current weighted round-robin counters (smooth WRR, nginx-style).
        /// Stored as `i64` to avoid overflow on large / long-running sums.
        counters: Mutex<Vec<i64>>,
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
                let total_weight: i64 = nodes.iter().map(|n| n.weight.max(1) as i64).sum();
                Self::WeightRandom {
                    nodes,
                    total_weight,
                }
            }
            LoadBalance::WeightRoundRobin => {
                let total_weight: i64 = nodes.iter().map(|n| n.weight.max(1) as i64).sum();
                let counters = Mutex::new(vec![0; nodes.len()]);
                Self::WeightRoundRobin {
                    nodes,
                    total_weight,
                    counters,
                }
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
            Self::WeightRandom {
                nodes,
                total_weight,
            } => {
                // O(n) walk — does NOT expand nodes by weight, so large
                // weights cannot cause OOM. Mirrors the Java SDK's
                // WeightRandomLoadBalanceSelector.
                let mut target = rand::thread_rng().gen_range(0..*total_weight);
                for n in nodes.iter() {
                    target -= n.weight.max(1) as i64;
                    if target < 0 {
                        return n;
                    }
                }
                // Fallback (defensive — unreachable when total_weight is exact).
                &nodes[nodes.len() - 1]
            }
            Self::WeightRoundRobin {
                nodes,
                total_weight,
                counters,
            } => {
                // Smooth weighted round-robin (nginx-style). Counters are i64
                // so large weights or long uptimes cannot overflow.
                let mut guard = counters.lock().expect("counter lock poisoned");
                let mut best = 0usize;
                for (i, n) in nodes.iter().enumerate() {
                    guard[i] += n.weight.max(1) as i64;
                    if guard[i] > guard[best] {
                        best = i;
                    }
                }
                guard[best] -= total_weight;
                &nodes[best]
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn node(host: &str, port: u16, weight: i32) -> ServerNode {
        ServerNode {
            host: host.to_string(),
            port,
            weight,
        }
    }

    #[test]
    fn random_selects_within_set() {
        let nodes = vec![node("a", 1, 1), node("b", 2, 1)];
        let sel = LoadBalanceSelector::new(nodes.clone(), LoadBalance::Random).unwrap();
        for _ in 0..20 {
            let n = sel.select();
            assert!(nodes.contains(n));
        }
    }

    #[test]
    fn weight_round_robin_distributes_proportionally() {
        let nodes = vec![node("a", 1, 5), node("b", 1, 1)];
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

    #[test]
    fn weight_random_distributes_proportionally() {
        let nodes = vec![node("a", 1, 9), node("b", 1, 1)];
        let sel = LoadBalanceSelector::new(nodes, LoadBalance::WeightRandom).unwrap();
        let mut a = 0;
        for _ in 0..1000 {
            if sel.select().host == "a" {
                a += 1;
            }
        }
        // ~9/10 should be 'a'.
        assert!(a > 850 && a < 950, "a={a}");
    }

    #[test]
    fn weight_random_handles_large_weight_without_oom() {
        // Previously this would expand to a Vec of 1 billion entries.
        let nodes = vec![node("a", 1, 1000000), node("b", 1, 1)];
        let sel = LoadBalanceSelector::new(nodes, LoadBalance::WeightRandom).unwrap();
        for _ in 0..10 {
            sel.select();
        }
    }
}
