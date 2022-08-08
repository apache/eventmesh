package registry

// Config register config
type Config struct {
	ServiceName string
	Weight      int
	Address     string
	Metadata    map[string]string
}
