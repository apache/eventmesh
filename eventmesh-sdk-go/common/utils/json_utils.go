package utils

import (
	"encoding/json"
	"log"
)

func MarshalJsonBytes(obj interface{}) []byte {
	ret, err := json.Marshal(obj)
	if err != nil {
		log.Fatal("Failed to marshal json")
	}
	return ret
}

func MarshalJsonString(obj interface{}) string {
	return string(MarshalJsonBytes(obj))
}

func UnMarshalJsonBytes(data []byte, obj interface{}) {
	err := json.Unmarshal(data, obj)
	if err != nil {
		log.Fatal("Failed to unmarshal json")
	}
}

func UnMarshalJsonString(data string, obj interface{}) {
	UnMarshalJsonBytes([]byte(data), obj)
}
