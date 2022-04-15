package loadbalancer

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestStatusServer_String(t *testing.T) {
	type fields struct {
		ReadyForService bool
		Host            string
		RealServer      interface{}
	}
	tests := []struct {
		name   string
		fields fields
		want   string
	}{
		{
			name: "test string",
			fields: fields{
				RealServer:      "srv",
				ReadyForService: true,
				Host:            "127.0.0.1",
			},
			want: "",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &StatusServer{
				ReadyForService: tt.fields.ReadyForService,
				Host:            tt.fields.Host,
				RealServer:      tt.fields.RealServer,
			}
			assert.NotEmpty(t, s.String(), "String()")
			t.Logf(s.String())
		})
	}
}
