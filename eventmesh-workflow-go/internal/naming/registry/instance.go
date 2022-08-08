package registry

import (
	"fmt"
)

// Instance is the information of a instance.
type Instance struct {
	ServiceName string
	Address     string
	Weight      int
	Clusters    string
	Metadata    map[string]string
}

// String returns an abbreviation information of instance.
func (n *Instance) String() string {
	return fmt.Sprintf("service:%s, addr:%s", n.ServiceName, n.Address)
}
