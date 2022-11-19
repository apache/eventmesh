package filter

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-workflow-go/third_party/jqer"
	"testing"
)

func TestFilterStateData(t *testing.T) {

	data := make(map[string]interface{})
	data["veggieName"] = "potato"
	data["veggieLike"] = true

	jq := jqer.NewJQ()
	command := "${ {fruits: .veggieName} }"
	ret, err := jq.Object(data, command)
	if err != nil {
		return
	}
	fmt.Println(ret)

}
