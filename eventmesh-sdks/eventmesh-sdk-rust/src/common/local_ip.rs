/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
use local_ip_address::local_ip;
use std::net::{IpAddr, Ipv4Addr};

/// Get local IPv4 address.
///
/// Get local IPv4 address, fallback to 127.0.0.1 if failed.
///
/// # Returns
///
/// The local IPv4 address as string.
pub(crate) fn get_local_ip_v4() -> String {
    let ip_addr = local_ip().unwrap_or(IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1)));
    ip_addr.to_string()
}
