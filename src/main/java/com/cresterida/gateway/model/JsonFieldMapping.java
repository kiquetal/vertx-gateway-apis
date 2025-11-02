package com.cresterida.gateway.model;

import java.util.Map;
import java.util.HashMap;

public class JsonFieldMapping {
    private String protoField;
    private String jsonField;
    private String type;
    private Map<String, String> enumMappings;

    public JsonFieldMapping() {
        this.enumMappings = new HashMap<>();
    }

    public String getProtoField() {
        return protoField;
    }

    public void setProtoField(String protoField) {
        this.protoField = protoField;
    }

    public String getJsonField() {
        return jsonField;
    }

    public void setJsonField(String jsonField) {
        this.jsonField = jsonField;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, String> getEnumMappings() {
        return enumMappings;
    }

    public void setEnumMappings(Map<String, String> enumMappings) {
        this.enumMappings = enumMappings;
    }
}
