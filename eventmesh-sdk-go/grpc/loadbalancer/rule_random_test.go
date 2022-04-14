package loadbalancer

import (
	"fmt"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestRandomRule_Choose(t *testing.T) {
	type fields struct {
		BaseRule BaseRule
	}
	type args struct {
		in0 interface{}
	}
	fled := fields{
		BaseRule: func() BaseRule {
			lb, _ := NewLoadBalancer(conf.Random, []*StatusServer{
				{
					RealServer:      "127.0.0.1",
					ReadyForService: true,
					Host:            "127.0.0.1",
				},
				{
					RealServer:      "127.0.0.2",
					ReadyForService: true,
					Host:            "127.0.0.2",
				},
				{
					RealServer:      "127.0.0.3",
					ReadyForService: true,
					Host:            "127.0.0.3",
				},
			})
			return BaseRule{
				lb: lb,
			}
		}(),
	}
	tests := []struct {
		name    string
		fields  fields
		args    args
		want    interface{}
		wantErr assert.ErrorAssertionFunc
	}{
		{
			name:   "random one",
			fields: fled,
			args: args{
				in0: "",
			},
			want: "",
			wantErr: func(t assert.TestingT, err error, i ...interface{}) bool {
				return true
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := &RandomRule{
				BaseRule: tt.fields.BaseRule,
			}
			got, err := r.Choose(tt.args.in0)
			if !tt.wantErr(t, err, fmt.Sprintf("Choose(%v)", tt.args.in0)) {
				return
			}
			assert.NotEmpty(t, got, "Choose(%v)", tt.args.in0)
		})
	}
}
