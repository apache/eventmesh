package common

type ProtocolVersion struct {
	version string
}

func (p *ProtocolVersion) Version() string {
	return p.version
}

func (p *ProtocolVersion) SetVersion(version string) {
	p.version = version
}

var DefaultProtocolVersion = struct {
	V1 ProtocolVersion
	V2 ProtocolVersion
}{
	V1: ProtocolVersion{
		version: "1.0",
	},
	V2: ProtocolVersion{
		version: "2.0",
	},
}
