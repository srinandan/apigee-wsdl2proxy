package com.google.cloud.edge.wsdl2proxy.utils;

/**
 * Created by mviswanathan on 11/04/17.
 */
public class SOAPOperation {
    String name;
    String requestSchema;
    String responseSchema;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRequestSchema() {
        return requestSchema;
    }

    public void setRequestSchema(String requestSchema) {
        this.requestSchema = requestSchema;
    }

    public String getResponseSchema() {
        return responseSchema;
    }

    public void setResponseSchema(String responseSchema) {
        this.responseSchema = responseSchema;
    }
}
