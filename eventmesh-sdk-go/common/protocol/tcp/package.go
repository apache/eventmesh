package tcp

type Package struct {
	Header Header      `json:"header"`
	Body   interface{} `json:"body"`
}

func NewPackage(header Header) Package {
	return Package{Header: header}
}
