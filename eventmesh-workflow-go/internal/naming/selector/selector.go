package selector

import "github.com/apache/incubator-eventmesh/eventmesh-workflow-go/internal/naming/registry"

// Selector is the interface that defines the selector.
type Selector interface {
	// Select gets a backend instance by service name.
	Select(serviceName string) (*registry.Instance, error)
}

var (
	selectors = make(map[string]Selector)
)

// Register registers a named Selector, such as l5, cmlb and tseer.
func Register(name string, s Selector) {
	selectors[name] = s
}

// Get gets a named Selector.
func Get(name string) Selector {
	s := selectors[name]
	return s
}

func unregisterForTesting(name string) {
	delete(selectors, name)
}
