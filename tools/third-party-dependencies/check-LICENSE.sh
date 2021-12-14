#!/usr/bin bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

decompress_conf='build/tmp'
self_modules_txt='tools/third-party-dependencies/self-modules.txt'
all_dependencies_txt='tools/third-party-dependencies/all-dependencies.txt'
third_party_dependencies_txt='tools/third-party-dependencies/third-party-dependencies.txt'
known_third_party_dependencies_txt='tools/third-party-dependencies/known-dependencies.txt'

mkdir $decompress_conf || true
tar -zxf build/EventMesh*.tar.gz -C $decompress_conf

./gradlew printProjects | grep '.jar' > "$self_modules_txt"

find "$decompress_conf" -name "*.jar" -exec basename {} \; | uniq | sort > "$all_dependencies_txt"

grep -wvf "$self_modules_txt" "$all_dependencies_txt" | uniq | sort > "$third_party_dependencies_txt"

# If the check is success it will return 0
diff -w -B "$third_party_dependencies_txt" "$known_third_party_dependencies_txt"

