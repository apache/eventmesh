package org.apache.eventmesh.common.transform;

import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.exception.JsonException;
import org.apache.eventmesh.common.utils.JsonPathUtils;

import javax.print.DocFlavor;

public class TransformBuilder {


    public static Transform build(TransformType type, String ruleContent, String template)
            throws EventMeshException {
        switch (type) {
            case CONSTANT:
                return buildConstantTransform(ruleContent);
            case JSONPATH:
                return buildJsonTransform(ruleContent);
            case ORIGINAL:
                return buildWholeTransform();
            case TEMPLATE:
                return buildTemplateTransForm(ruleContent, template);
            default:
                throw new EventMeshException("invalid config");
        }
    }


    public static Transform buildJsonTransform(String inputString) throws EventMeshException {
        JsonPathParser jsonPathParser = new JsonPathParser(JsonPathUtils.buildJsonString("VAR_NAME", inputString));
        return new JsonPathTransform(jsonPathParser);
    }

    public static Transform buildTemplateTransForm(String extractJson, String template)   {
        JsonPathParser jsonPathParser = new JsonPathParser(extractJson);
        Template templateEntry = new SubstituteTemplate(template);
        return new TemplateTransform(jsonPathParser, templateEntry);
    }

    public static Transform buildConstantTransform(String constant) {
        return new ConstantTransform(constant);
    }

    public static Transform buildWholeTransform() {
        return new WholeTransform();
    }
}
