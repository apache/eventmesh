package filter

import (
	"encoding/json"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/third_party/jqer"
)

func FilterJsonData(jq jqer.JQ, filterJson string, inputDataJson string) (string, error) {
	ret, err := jq.Object(inputDataJson, filterJson)
	if err != nil {
		return "", err
	}

	outputDataJson, err := json.Marshal(ret)
	if err != nil {
		return "", err
	}

	return string(outputDataJson), nil
}
