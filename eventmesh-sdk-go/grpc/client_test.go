package grpc

import (
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"reflect"
	"testing"
)

func Test_newEventMeshGRPCClient(t *testing.T) {
	type args struct {
		cfg *conf.GRPCConfig
	}
	tests := []struct {
		name    string
		args    args
		want    *eventMeshGRPCClient
		wantErr bool
	}{
		{
			name: "client with send msg",
			args: args{cfg: &conf.GRPCConfig{
				Hosts:           []string{"101.43.84.47"},
				Port:            15030,
				ENV:             "sendmsgenv",
				Region:          "sh",
				IDC:             "idc01",
				SYS:             "test-system",
				ProtocolType:    "grpc",
				ProtocolVersion: "v1.0.0",
				ProducerConfig: conf.ProducerConfig{
					ProducerGroup:    "test-producer-group",
					LoadBalancerType: conf.Random,
				},
				Username: "user",
				Password: "passwd",
			}},
			want:    nil,
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := newEventMeshGRPCClient(tt.args.cfg)
			if (err != nil) != tt.wantErr {
				t.Errorf("newEventMeshGRPCClient() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("newEventMeshGRPCClient() got = %v, want %v", got, tt.want)
			}
		})
	}
}
