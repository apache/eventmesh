package org.apache.eventmesh.common.transform;

import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.utils.JsonPathUtils;
import org.omg.CORBA.PUBLIC_MEMBER;

public class TransformerBuilder {


    public static final class Builder {
        private final TransformerType transformerType;
        private String template;
        private String content;

        public Builder(TransformerType transformerType) {
            this.transformerType = transformerType;
        }

        public Builder setContent(String content){
            this.content = content;
            return this;
        }

        public Builder setTemplate(String template){
            this.template = template;
            return this;
        }

        public Transformer build(){
            switch (this.transformerType) {
                case CONSTANT:
                    return buildConstantTransformer(this.content);
                case ORIGINAL:
                    return buildOriginalTransformer();
                case TEMPLATE:
                    return buildTemplateTransFormer(this.content, this.template);
                default:
                    throw new EventMeshException("invalid config");
            }
        }

    }

        public static Transformer buildTemplateTransFormer(String jsonContent, String template){
            JsonPathParser jsonPathParser = new JsonPathParser(jsonContent);
            Template templateEntry = new Template(template);
            return new TemplateTransformer(jsonPathParser, templateEntry);
        }

        public static Transformer buildConstantTransformer(String constant) {
            return new ConstantTransformer(constant);
        }

        public static Transformer buildOriginalTransformer() {
            return new OriginalTransformer();
        }


}

