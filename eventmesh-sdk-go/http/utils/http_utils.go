package utils

import (
	"eventmesh/http/model"
	"io/ioutil"
	nethttp "net/http"
	"net/url"
	"strings"
)

func HttpPost(client *nethttp.Client, uri string, requestParam *model.RequestParam) string {

	data := url.Values{}
	body := requestParam.Body()
	for key := range body {
		data.Set(key, body[key])
	}

	req, err := nethttp.NewRequest(nethttp.MethodPost, uri, strings.NewReader(data.Encode()))
	if err != nil {
	}

	req.Header.Set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")

	headers := requestParam.Headers()
	for header := range headers {
		req.Header[header] = []string{headers[header]}
	}

	resp, err := client.Do(req)
	if err != nil {
	}

	defer resp.Body.Close()

	respBody, err := ioutil.ReadAll(resp.Body)
	if err != nil {
	}

	return string(respBody)
}
