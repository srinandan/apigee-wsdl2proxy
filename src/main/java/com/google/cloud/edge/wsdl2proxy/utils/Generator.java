package com.google.cloud.edge.wsdl2proxy.utils;

import com.predic8.wsdl.*;
import com.sun.xml.internal.bind.api.impl.NameConverter;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by mviswanathan on 10/04/17.
 */
public class Generator {
    private ProxyConfig proxyConfig;
    private WSDLParser parser;
    private Definitions definitions;
    private XMLUtils xmlUtils;

    private long workingTimestamp;
    private String workingPath;
    private String genPath;
    private String basePackage;

    private Document apiProxyDocument, proxyEndpointDocument, targetEndpointDocument, assignMessageSetHeaderDocument;
    private Node apiProxyNode, proxyEndpointNode, targetEndpointNode, apiProxyPoliciesNode, proxyEndpointFlows, apiProxyResourcesNode;

    public Generator(ProxyConfig proxyConfig) throws Exception {
        this.proxyConfig = proxyConfig;
        this.parser = new WSDLParser();
        try {
            this.xmlUtils = new XMLUtils();
        } catch (Exception e) {
            e.printStackTrace();
        }
        init();
    }

    private void init() throws Exception {
        ArrayList<SOAPOperation> soapOperations = new ArrayList<>();
        this.definitions = this.parser.parse(this.proxyConfig.getWsdlUrl());
        Binding binding = this.definitions.getBinding(this.proxyConfig.getBinding());
        PortType portType = this.definitions.getPortType(binding.getType().getLocalPart());

        System.out.println("Target Namespace: " + definitions.getTargetNamespace());
        System.out.println("Binding: " + this.proxyConfig.getBinding());
        System.out.println("PortTypes: ");
        System.out.println("  PortType Name: " + portType.getName());

        System.out.println("  PortType Operations: ");
        for (Operation operation : portType.getOperations()) {
            SOAPOperation soapOperation = new SOAPOperation();
            soapOperation.setName(operation.getName());
            soapOperation.setRequestSchema(this.definitions.getMessage(operation.getInput().getMessage().getQname()).getParts().get(0).getElement().getName());
            soapOperation.setResponseSchema(this.definitions.getMessage(operation.getOutput().getMessage().getQname()).getParts().get(0).getElement().getName());
            soapOperations.add(soapOperation);

            System.out.println("    Operation Name: " + soapOperation.getName());

            System.out.println("    Operation Input Name: " + ((operation.getInput().getName() != null) ? operation.getInput().getName() : "Not available!"));
            System.out.println("    Operation Input Message: " + operation.getInput().getMessage().getQname());
            System.out.println("    Operation Input Schema: " + soapOperation.getRequestSchema());

            System.out.println("\n    Operation Output Name: " + ((operation.getOutput().getName() != null) ? operation.getOutput().getName() : "Not available!"));
            System.out.println("    Operation Output Message: " + operation.getOutput().getMessage().getQname());
            System.out.println("    Operation Output Schema: " + soapOperation.getResponseSchema());

            System.out.println("    Operation Faults: ");
            if (operation.getFaults().size() > 0) {
                for (Fault fault : operation.getFaults()) {
                    System.out.println("      Fault Name: " + fault.getName());
                    System.out.println("      Fault Message: " + fault.getMessage().getQname());
                }
            } else System.out.println("      There are no faults available!");
        }

        NameConverter nameConverter = new NameConverter.Standard();
        this.basePackage = nameConverter.toPackageName(this.definitions.getTargetNamespace());
        System.out.println("Base Package - " + this.basePackage);

        // Find Target URL for the port
        for (Port servicePort : this.definitions.getServices().get(0).getPorts()) {
            if (servicePort.getBinding().getName().equals(this.proxyConfig.getBinding())) {
                this.proxyConfig.setTargetUrl(servicePort.getAddress().getLocation());
            }
        }

        // Init working vars
        this.proxyConfig.setResourcePaths(soapOperations);
        this.workingTimestamp = System.currentTimeMillis();
        this.workingPath = "working/" + this.workingTimestamp;
        this.genPath = "gen";

        // Init API Proxy Docs
        this.apiProxyDocument = xmlUtils.readXML(TemplateRef.SOAP2JAVA_APIPROXY_TEMPLATE);
        this.proxyEndpointDocument = xmlUtils.readXML(TemplateRef.SOAP2JAVA_PROXY_ENDPOINT_TEMPLATE);
        this.targetEndpointDocument = xmlUtils.readXML(TemplateRef.SOAP2JAVA_TARGET_TEMPLATE);
        this.assignMessageSetHeaderDocument = xmlUtils.readXML(TemplateRef.SOAP2JAVA_ASSIGN_MESSAGE_TEMPLATE);

        // Init Common Nodes
        this.apiProxyNode = apiProxyDocument.getElementsByTagName("APIProxy").item(0);
        this.proxyEndpointNode = proxyEndpointDocument.getElementsByTagName("ProxyEndpoint").item(0);
        this.targetEndpointNode = targetEndpointDocument.getElementsByTagName("TargetEndpoint").item(0);
        this.apiProxyResourcesNode = ((Element) apiProxyNode).getElementsByTagName("Resources").item(0);
        this.apiProxyPoliciesNode = ((Element) apiProxyNode).getElementsByTagName("Policies").item(0);
        this.proxyEndpointFlows = ((Element) proxyEndpointNode).getElementsByTagName("Flows").item(0);

    }

    private Document buildJavaCalloutPolicy(String basePackage, String contextClass) throws Exception {
        Document javacalloutPolicyDocument = xmlUtils.readXML(TemplateRef.SOAP2JAVA_JAVACALLOUT_TEMPLATE);

        Node javacalloutNode = javacalloutPolicyDocument.getElementsByTagName("JavaCallout").item(0);
        ((Element) javacalloutNode).setAttribute("name", "JavaCallout-" + contextClass);
        ((Element) javacalloutNode).getElementsByTagName("DisplayName").item(0).setTextContent("JavaCallout-" + contextClass);

        Node javacalloutPropertyNode = ((Element) javacalloutNode).getElementsByTagName("Properties").item(0);

        Node javacalloutClassPropertyNode = javacalloutPolicyDocument.createElement("Property");
        ((Element) javacalloutClassPropertyNode).setAttribute("name", "classes");
        javacalloutClassPropertyNode.setTextContent(basePackage + contextClass);
        javacalloutPropertyNode.appendChild(javacalloutClassPropertyNode);

        Node javacalloutContextClassPropertyNode = javacalloutPolicyDocument.createElement("Property");
        ((Element) javacalloutContextClassPropertyNode).setAttribute("name", "contextClass");
        javacalloutContextClassPropertyNode.setTextContent(basePackage + contextClass);
        javacalloutPropertyNode.appendChild(javacalloutContextClassPropertyNode);

        // Add JAR file as Resource
        Node javacalloutRequestResource = javacalloutPolicyDocument.createElement("ResourceURL");
        javacalloutRequestResource.setTextContent("java://" + proxyConfig.getBinding() + ".jar");
        javacalloutNode.appendChild(javacalloutRequestResource);

        return javacalloutPolicyDocument;
    }

    private Node createFlow(String type, List<String> steps, Document document) {
        Node flow = document.createElement(type);

        for (String step : steps) {
            Node stepNode = proxyEndpointDocument.createElement("Step");
            Node stepNameNode = proxyEndpointDocument.createElement("Name");
            stepNameNode.setTextContent(step);
            stepNode.appendChild(stepNameNode);
            flow.appendChild(stepNode);
        }
        return flow;
    }

    private Node createConditionalFlow(String flowName, String condition, List<String> requestSteps, List<String> responseSteps, Document document) {
        Node conditionalFlow = document.createElement("Flow");
        ((Element) conditionalFlow).setAttribute("name", flowName);

        conditionalFlow.appendChild(this.createFlow("Request", requestSteps, document));
        conditionalFlow.appendChild(this.createFlow("Response", responseSteps, document));

        Node conditionNode = document.createElement("Condition");
        conditionNode.setTextContent(condition);
        conditionalFlow.appendChild(conditionNode);

        return conditionalFlow;
    }

    private void addAPIProxyPolicy(String policyName) {
        Node policyNode = apiProxyDocument.createElement("Policy");
        policyNode.setTextContent(policyName);
        this.apiProxyPoliciesNode.appendChild(policyNode);
    }

    private void generateJarWSImport(boolean setBinding) throws Exception {
        StreamGobbler errorGobbler, stdoutGobbler;
        System.out.println("WSImport - Run");
        String command = "wsimport " + this.proxyConfig.getWsdlUrl() + " -d " + this.workingPath + " -clientjar " + this.proxyConfig.getBinding() + ".jar -verbose";

        if (setBinding) {
            System.out.println("WSImport - Set custom bindings");
            command = command + " -J-Djavax.xml.accessExternalSchema=all -J-Djavax.xml.accessExternalDTD=all -b xsd.xjb -b http://www.w3.org/2001/XMLSchema.xsd";
        }

        System.out.println("WSImport - Command: " + command);

        Process proc = Runtime.getRuntime().exec(command);

        errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERR");
        stdoutGobbler = new StreamGobbler(proc.getInputStream(), "STDOUT");

        stdoutGobbler.start();
        errorGobbler.start();

        int exitVal = proc.waitFor();
        System.out.println("WSImport Exit Value: " + exitVal);

        if (exitVal == 1) {
            throw new Exception("WSImport - " + errorGobbler.getStackTrace().toString());
        }

        System.out.println("WSImport - Success");
        System.out.println("Copy " + this.workingPath + "/" + this.proxyConfig.getBinding() + ".jar to " + this.workingPath + "/apiproxy/resources/java/" + this.proxyConfig.getBinding() + ".jar");
        copyFiles(this.workingPath + "/" + this.proxyConfig.getBinding() + ".jar", this.workingPath + "/apiproxy/resources/java/" + this.proxyConfig.getBinding() + ".jar", null);

    }

    private void copyFiles(String sourceFile, String destFile, URI sourceURI) throws URISyntaxException {
        File source;
        if (sourceFile != null) {
            source = new File(sourceFile);
        } else {
            source = new File(sourceURI);
        }

        File dest = new File(destFile);
        try {
            if (source.isDirectory()) {
                FileUtils.copyDirectory(source, dest);
            } else {
                FileUtils.copyFile(source, dest);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initWorkingDir() throws Exception {
        System.out.println("Setting up working dir..");
        Runtime.getRuntime().exec("mkdir -p " + this.workingPath + "/apiproxy/proxies");
        Runtime.getRuntime().exec("mkdir -p" + this.workingPath + "/apiproxy/resources/java");
        Runtime.getRuntime().exec("mkdir " + this.workingPath + "/apiproxy/targets");
        Runtime.getRuntime().exec("mkdir " + this.workingPath + "/apiproxy/policies");
        Runtime.getRuntime().exec("mkdir " + this.genPath);

        copyFiles(null, this.workingPath + "/apiproxy/resources/java", getClass().getClassLoader().getResource("soap2java/resources/java").toURI());
    }

    private void buildAPIProxy() throws Exception {
        System.out.println("Build API Proxy");

        // Add JAR file as API Proxy resource
        Node apiProxyResource = this.apiProxyDocument.createElement("Resource");
        apiProxyResource.setTextContent("java://" + proxyConfig.getBinding() + ".jar");
        this.apiProxyResourcesNode.appendChild(apiProxyResource);

        // Set API Proxy DisplayName
        (((Element) apiProxyNode).getElementsByTagName("DisplayName").item(0)).setTextContent(this.proxyConfig.getBinding());

        // Set API Proxy Name
        ((Element) apiProxyNode).setAttribute("name", this.proxyConfig.getBinding().replace(" ", "") + "Proxy");

        // Set API Proxy BasePath
        (((Element) apiProxyNode).getElementsByTagName("Basepaths").item(0)).setTextContent("/" + this.proxyConfig.getBinding().toLowerCase());

        // Set Proxy BasePath
        Node proxyEndpointHTTPConnection = (((Element) this.proxyEndpointNode).getElementsByTagName("HTTPProxyConnection").item(0));
        ((Element) proxyEndpointHTTPConnection).getElementsByTagName("BasePath").item(0).setTextContent("/" + this.proxyConfig.getBinding().toLowerCase());

        // Set Target URL
        Node targetEndpointHTTPTargetNode = ((Element) this.targetEndpointNode).getElementsByTagName("HTTPTargetConnection").item(0);
        ((Element) targetEndpointHTTPTargetNode).getElementsByTagName("URL").item(0).setTextContent(this.proxyConfig.getTargetUrl());


        for (SOAPOperation soapOperation : this.proxyConfig.getResourcePaths()) {
            // Create Request Policy
            Document javacalloutRequestPolicyDocument = buildJavaCalloutPolicy(this.basePackage, soapOperation.getRequestSchema());
            // Create Request Policy
            Document javacalloutResponsePolicyDocument = buildJavaCalloutPolicy(this.basePackage, soapOperation.getResponseSchema());

            String conditionStr = "(proxy.pathsuffix MatchesPath \"/" + soapOperation.getName().toLowerCase() + "\") and (request.verb = \"POST\")";
            String requestStepStr = "JavaCallout-" + soapOperation.getRequestSchema().replace(" ", "");
            String responseStepStr = "JavaCallout-" + soapOperation.getResponseSchema().replace(" ", "");

            // Add Conditional Flow
            this.proxyEndpointFlows.appendChild(createConditionalFlow(soapOperation.getName(), conditionStr, Arrays.asList(requestStepStr), Arrays.asList(responseStepStr), this.proxyEndpointDocument));

            // Add Policies to the API Proxy
            addAPIProxyPolicy(requestStepStr);
            addAPIProxyPolicy(responseStepStr);

            // Write Policies to apiproxy folder
            this.xmlUtils.writeXML(javacalloutRequestPolicyDocument, this.workingPath + "/apiproxy/policies/" + requestStepStr + ".xml");
            this.xmlUtils.writeXML(javacalloutResponsePolicyDocument, this.workingPath + "/apiproxy/policies/" + responseStepStr + ".xml");
        }

        // Write rest to apiproxy folder
        this.xmlUtils.writeXML(this.assignMessageSetHeaderDocument, this.workingPath + "/apiproxy/policies/AssignMessage-SetContentType.xml");
        this.xmlUtils.writeXML(this.proxyEndpointDocument, this.workingPath + "/apiproxy/proxies/default.xml");
        this.xmlUtils.writeXML(this.targetEndpointDocument, this.workingPath + "/apiproxy/targets/default.xml");
        this.xmlUtils.writeXML(this.apiProxyDocument, this.workingPath + "/apiproxy/" + this.proxyConfig.getBinding() + "Proxy.xml");

    }

    private class JarBuilder implements Callable<String> {

        private Generator mGenerator;

        public JarBuilder(Generator generator) {
            this.mGenerator = generator;
        }

        @Override
        public String call() throws Exception {
            try {
                mGenerator.generateJarWSImport(false);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                mGenerator.generateJarWSImport(true);
            }
            return "";
        }
    }

    private class ProxyBuilder implements Callable<String> {

        private Generator mGenerator;

        public ProxyBuilder(Generator generator) {
            this.mGenerator = generator;
        }

        @Override
        public String call() throws Exception {
            mGenerator.buildAPIProxy();
            return null;
        }
    }

    public String generateAPIProxy() throws Exception {
        long startTime = System.currentTimeMillis();

        initWorkingDir();

        JarBuilder jarBuilder = new JarBuilder(this);
        ProxyBuilder proxyBuilder = new ProxyBuilder(this);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> jarFuture = executor.submit(jarBuilder);
        Future<String> proxyFuture = executor.submit(proxyBuilder);

        jarFuture.get();
        proxyFuture.get();

        while (!jarFuture.isDone() && !proxyFuture.isDone()) {
            // Do nothing
        }

        // Create API Proxy zip
        System.out.println("Create " + this.proxyConfig.getBinding() + "-" + this.workingTimestamp + ".zip at " + this.genPath + "/");
        FolderZipper.zipFolder(this.workingPath + "/apiproxy", this.genPath + "/" + this.proxyConfig.getBinding() + "-" + this.workingTimestamp + ".zip");

        // Delete working dir
        System.out.println("Delete " + this.workingPath);
        Runtime.getRuntime().exec("rm -rf " + this.workingPath);

        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.println("Completed in " + elapsed + "s");

        executor.shutdown();

        return this.genPath + "/" + this.proxyConfig.getBinding() + "-" + this.workingTimestamp + ".zip";
    }


    public static void main(String[] args) throws Exception {
        ProxyConfig proxyConfig = new ProxyConfig();
        proxyConfig.setWsdlUrl("http://www.webservicex.net/weatherforecast.asmx?wsdl");
        proxyConfig.setBinding("WeatherForecastSoap");

        Generator generator = new Generator(proxyConfig);
        System.out.println(generator.generateAPIProxy());

        ProxyConfig proxyConfig2 = new ProxyConfig();
        proxyConfig2.setWsdlUrl("https://www.paypalobjects.com/wsdl/PayPalSvc.wsdl");
        proxyConfig2.setBinding("PayPalAPISoapBinding");
        System.out.println(new Generator(proxyConfig2).generateAPIProxy());

        ProxyConfig proxyConfig3 = new ProxyConfig();
        proxyConfig3.setWsdlUrl("https://ws.cdyne.com/delayedstockquote/delayedstockquote.asmx?WSDL");
        proxyConfig3.setBinding("DelayedStockQuoteSoap");
        System.out.println(new Generator(proxyConfig3).generateAPIProxy());
    }
}

