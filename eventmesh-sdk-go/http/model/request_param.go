package model

type HttpMethod string

type RequestParam struct {
	queryParams map[string][]string
	httpMethod  HttpMethod
	body        map[string]string
	headers     map[string]string
	timeout     int64
}

func NewRequestParam(httpMethod HttpMethod) *RequestParam {
	return &RequestParam{httpMethod: httpMethod}
}

func (r *RequestParam) QueryParams() map[string][]string {
	return r.queryParams
}

func (r *RequestParam) SetQueryParams(queryParams map[string][]string) {
	r.queryParams = queryParams
}

func (r *RequestParam) Body() map[string]string {
	return r.body
}

func (r *RequestParam) AddBody(key, value string) {
	if r.body == nil {
		r.body = make(map[string]string)
	}
	r.body[key] = value
}

func (r *RequestParam) Headers() map[string]string {
	return r.headers
}

func (r *RequestParam) AddHeader(key string, object interface{}) {
	if r.headers == nil {
		r.headers = make(map[string]string)
	}
	r.headers[key] = object.(string)
}

func (r *RequestParam) Timeout() int64 {
	return r.timeout
}

func (r *RequestParam) SetTimeout(timeout int64) {
	r.timeout = timeout
}
