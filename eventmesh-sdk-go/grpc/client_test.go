package grpc

import (
	"context"
	"github.com/apache/incubator-eventmesh/eventmesh-sdk-go/grpc/conf"
	"github.com/stretchr/testify/assert"
	"testing"
	"time"
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
			name: "host is empty",
			args: args{cfg: &conf.GRPCConfig{
				Hosts: []string{},
			}},
			wantErr: true,
			want:    nil,
		},
		{
			name: "producer wrong",
			args: args{cfg: &conf.GRPCConfig{
				Hosts: []string{"1.1.1.1"},
				ProducerConfig: conf.ProducerConfig{
					LoadBalancerType: "111",
				},
			}},
			wantErr: true,
			want:    nil,
		},
		{
			name: "client with send msg",
			args: args{cfg: &conf.GRPCConfig{
				Hosts:           []string{"101.43.84.47"},
				Port:            10205,
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
			cli, err := newEventMeshGRPCClient(tt.args.cfg)
			if (err != nil) != tt.wantErr {
				t.Errorf("newEventMeshGRPCClient() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			assert.NoError(t, cli.Close())
		})
	}
}

func Test_multiple_set_context(t *testing.T) {
	root := context.Background()
	onec, cancel := context.WithTimeout(root, time.Second*5)
	defer cancel()
	valc := context.WithValue(onec, "test", "got")

	select {
	case <-valc.Done():
		val := valc.Value("test")
		t.Logf("5 s reached, value in context:%v", val)
	case <-time.After(time.Second * 10):
		t.Logf("ooor, 10s timeout")
	}

}
