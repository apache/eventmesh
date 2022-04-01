package tcp

type UserAgent struct {
	Env       string `json:"env"`
	Subsystem string `json:"subsystem"`
	Path      string `json:"path"`
	Pid       int    `json:"pid"`
	Host      string `json:"host"`
	Port      int    `json:"port"`
	Version   string `json:"version"`
	Username  string `json:"username"`
	Password  string `json:"password"`
	Idc       string `json:"idc"`
	Group     string `json:"group"`
	Purpose   string `json:"purpose"`
	Unack     int    `json:"unack"`
}

func NewUserAgent(env string, subsystem string, path string, pid int, host string, port int, version string,
	username string, password string, idc string, producerGroup string, consumerGroup string) *UserAgent {
	return &UserAgent{Env: env, Subsystem: subsystem, Path: path, Pid: pid, Host: host, Port: port, Version: version,
		Username: username, Password: password, Idc: idc, Group: producerGroup}
}
