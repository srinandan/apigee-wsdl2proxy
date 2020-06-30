package com.google.cloud.edge.wsdl2proxy.utils;

import java.util.ArrayList;

/**
 * Created by mviswanathan on 11/04/17.
 */
public class ProxyConfig {
    private String protocol;
    private String binding;
    private String targetUrl;
    private String wsdlUrl;
    private ArrayList<SOAPOperation> resourcePaths;

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getBinding() {
        return binding;
    }

    public void setBinding(String binding) {
        this.binding = binding;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public ArrayList<SOAPOperation> getResourcePaths() {
        return resourcePaths;
    }

    public void setResourcePaths(ArrayList<SOAPOperation> resourcePaths) {
        this.resourcePaths = resourcePaths;
    }

    public String getWsdlUrl() {
        return wsdlUrl;
    }

    public void setWsdlUrl(String wsdlUrl) {
        this.wsdlUrl = wsdlUrl;
    }
}
