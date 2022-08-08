package selector

// Config selector config
type Config struct {
	ServiceName string
	Weight      int
	ClusterName string
	GroupName   string
	Metadata    map[string]string
}
