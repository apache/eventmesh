package conf

import "testing"

func TestValidateDefaultConf(t *testing.T) {
	type args struct {
		cfg *GRPCConfig
	}
	tests := []struct {
		name    string
		args    args
		wantErr bool
	}{
		{
			name: "test no hosts",
			args: args{
				cfg: &GRPCConfig{},
			},
			wantErr: true,
		},
		{
			name: "test default duration",
			args: args{
				cfg: &GRPCConfig{
					Hosts: []string{"1", "2"},
				},
			},
			wantErr: false,
		},
		{
			name: "test consumer enable, but no group",
			args: args{
				cfg: &GRPCConfig{
					Hosts: []string{"1"},
					ConsumerConfig: ConsumerConfig{
						Enabled: true,
					},
				},
			},
			wantErr: true,
		},
		{
			name: "test consumer enable, but no group",
			args: args{
				cfg: &GRPCConfig{
					Hosts: []string{"1"},
					ConsumerConfig: ConsumerConfig{
						Enabled:       true,
						ConsumerGroup: "test",
					},
				},
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if err := ValidateDefaultConf(tt.args.cfg); (err != nil) != tt.wantErr {
				t.Errorf("ValidateDefaultConf() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}
