package com.cresterida.gateway.model;

import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.HashMap;

public class EndpointDefinition {
    private String name;
    private String methodName;
    private String inputMessage;
    private String outputMessage;
    private Map<String, String> inputMapping;
    private Map<String, String> outputMapping;

    public EndpointDefinition() {
        this.inputMapping = new HashMap<>();
        this.outputMapping = new HashMap<>();
    }

    public static EndpointDefinition fromJson(JsonObject json) {
        EndpointDefinition endpoint = new EndpointDefinition();
        endpoint.name = json.getString("name");
        endpoint.methodName = json.getString("methodName");
        endpoint.inputMessage = json.getString("inputMessage");
        endpoint.outputMessage = json.getString("outputMessage");

        if (json.containsKey("inputMapping")) {
            JsonObject inputMap = json.getJsonObject("inputMapping");
            inputMap.forEach(entry -> endpoint.inputMapping.put(entry.getKey(), entry.getValue().toString()));
        }

        if (json.containsKey("outputMapping")) {
            JsonObject outputMap = json.getJsonObject("outputMapping");
            outputMap.forEach(entry -> endpoint.outputMapping.put(entry.getKey(), entry.getValue().toString()));
        }

        return endpoint;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
            .put("name", name)
            .put("methodName", methodName)
            .put("inputMessage", inputMessage)
            .put("outputMessage", outputMessage);

        JsonObject inputMapJson = new JsonObject();
        inputMapping.forEach(inputMapJson::put);
        json.put("inputMapping", inputMapJson);

        JsonObject outputMapJson = new JsonObject();
        outputMapping.forEach(outputMapJson::put);
        json.put("outputMapping", outputMapJson);

        return json;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getInputMessage() {
        return inputMessage;
    }

    public void setInputMessage(String inputMessage) {
        this.inputMessage = inputMessage;
    }

    public String getOutputMessage() {
        return outputMessage;
    }

    public void setOutputMessage(String outputMessage) {
        this.outputMessage = outputMessage;
    }

    public Map<String, String> getInputMapping() {
        return inputMapping;
    }

    public void setInputMapping(Map<String, String> inputMapping) {
        this.inputMapping = inputMapping;
    }

    public Map<String, String> getOutputMapping() {
        return outputMapping;
    }

    public void setOutputMapping(Map<String, String> outputMapping) {
        this.outputMapping = outputMapping;
    }
}
