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
use rand::distributions::Alphanumeric;
use rand::Rng;
use std::iter;
use uuid;

pub struct RandomStringUtils;

impl RandomStringUtils {
    /// Generate a random alphanumeric string.
    ///
    /// Generate a random string with given length, containing alphanumeric characters.
    ///
    /// # Arguments
    ///
    /// * `length` - The length of generated string.
    ///
    /// # Returns
    ///
    /// The randomly generated string.
    pub fn generate_num(length: usize) -> String {
        let random_string = iter::repeat(())
            .map(|()| rand::thread_rng().sample(Alphanumeric) as char)
            .take(length)
            .collect();
        random_string
    }

    /// Generate a random UUID string.
    ///
    /// # Returns
    ///
    /// The randomly generated UUID string.
    pub fn generate_uuid() -> String {
        let uuid = uuid::Uuid::new_v4();
        uuid.to_string()
    }
}
