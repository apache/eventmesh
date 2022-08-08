package swf

import (
	"github.com/gogf/gf/util/gconv"
	"github.com/serverlessworkflow/sdk-go/v2/model"
	"github.com/serverlessworkflow/sdk-go/v2/parser"
)

func Parse(source string) (*model.Workflow, error) {
	if len(source) == 0 {
		return nil, nil
	}
	return parser.FromYAMLSource(gconv.Bytes(source))
}
