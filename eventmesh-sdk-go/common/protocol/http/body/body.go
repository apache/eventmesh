package body

type Body struct {
	ToMap map[string]interface{}
}

func (b *Body) BuildBody(requestCode string, originalMap map[string]interface{}) *Body {
	return nil
}
