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

package id

import (
	"bytes"
	"fmt"
	"net"
	"strconv"
	"strings"

	"github.com/sony/sonyflake"
)

// flake generate uid by flake
type flake struct {
	sf *sonyflake.Sonyflake
}

func NewFlake() Interface {
	macAddr := getMacAddr()
	st := sonyflake.Settings{
		MachineID: func() (uint16, error) {
			ma := strings.Split(macAddr, ":")
			mid, err := strconv.ParseInt(ma[0]+ma[1], 16, 16)
			return uint16(mid), err
		},
	}
	return &flake{
		sf: sonyflake.NewSonyflake(st),
	}
}

// getMacAddr return the current machine mac address
func getMacAddr() (addr string) {
	interfaces, err := net.Interfaces()
	if err == nil {
		for _, i := range interfaces {
			if i.Flags&net.FlagUp != 0 && bytes.Compare(i.HardwareAddr, nil) != 0 {
				// Don't use random as we have a real address
				addr = i.HardwareAddr.String()
				break
			}
		}
	}
	return
}

// Nextv generates next id as an uint64
func (f *flake) Nextv() (id uint64, err error) {
	var i uint64
	if f.sf != nil {
		i, err = f.sf.NextID()
		if err == nil {
			id = i
		}
	}
	return
}

// Next generates next id as a string
func (f *flake) Next() string {
	var i uint64
	i, _ = f.Nextv()
	return fmt.Sprintf("%d", i)
}
