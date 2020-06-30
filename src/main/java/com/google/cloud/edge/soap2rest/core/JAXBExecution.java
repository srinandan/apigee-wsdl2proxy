package com.google.cloud.edge.soap2rest.core;

/**
 * Created by mviswanathan on 23/03/17.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.w3c.dom.Document;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.MessageContext;

public class JAXBExecution implements Execution {
    private Map<String, String> execProperties;
    private Map<String, String> jaxbParseProperties;
    private List<Class<?>> jaxbContextClasses;

    private ClassLoader classLoader;
    private JAXBContext jaxbContext;

    private boolean isRequestFlow;

    public JAXBExecution(Map<String, String> properties) {
        this.execProperties = properties;
        this.jaxbContextClasses = new ArrayList<Class<?>>();
        this.classLoader = JAXBExecution.class.getClassLoader();
        this.jaxbParseProperties = new HashMap<>();
    }

    private void initJAXB() throws ClassNotFoundException, JAXBException {
        String[] jaxbClasses = execProperties.get("contextClasses").split(",");

        for (String jaxbClass : jaxbClasses) {
            Class<?> mClass = classLoader.loadClass(jaxbClass);
            jaxbContextClasses.add(mClass);
            System.out.println("Added class to Context = " + mClass.getName());
        }

        jaxbContext = JAXBContextFactory.createContext(jaxbContextClasses.toArray(new Class[]{}), jaxbParseProperties);
    }

    private JAXBContext getJAXBContext() throws JAXBException {
        return jaxbContext;
    }

    private StreamSource getBody(MessageContext messageContext) throws SOAPException, IOException {
        String body;
        if (isRequestFlow) {
            body = messageContext.getVariable("request.content");
        } else {
            MessageFactory factory = MessageFactory.newInstance();
            SOAPMessage message = factory.createMessage(new MimeHeaders(), new ByteArrayInputStream(
                    ((String) messageContext.get("response.content")).getBytes(Charset.forName("UTF-8"))));

            body = message.getSOAPBody().toString();
        }
        return new StreamSource(new StringReader(body));
    }

    public Object toObj(MessageContext messageContext, String type)
            throws ClassNotFoundException, JAXBException, SOAPException, IOException {
        StreamSource stream = this.getBody(messageContext);
        jaxbParseProperties.put(MarshallerProperties.MEDIA_TYPE, "application/" + type);
        return getJAXBContext().createUnmarshaller().unmarshal(stream);
    }

    private String convertRaw(MessageContext messageContext, String type)
            throws JAXBException, ClassNotFoundException, SOAPException, IOException {
        StringWriter sw = new StringWriter();
        jaxbParseProperties.put(MarshallerProperties.MEDIA_TYPE, "application/" + type);

        Marshaller jaxbMarshaller = getJAXBContext().createMarshaller();
        jaxbMarshaller.setProperty(MarshallerProperties.MEDIA_TYPE, "application/" + type);
        jaxbMarshaller.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, true);
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(this.toObj(messageContext, type), sw);
        return sw.toString();
    }

    public String toJSON(MessageContext messageContext)
            throws ClassNotFoundException, JAXBException, SOAPException, IOException {
        return convertRaw(messageContext, "json");
    }

    public String toXML(MessageContext messageContext)
            throws ClassNotFoundException, JAXBException, SOAPException, IOException {
        return convertRaw(messageContext, "xml");
    }

    private void setRequest(MessageContext messageContext)
            throws SOAPException, ParserConfigurationException, ClassNotFoundException, JAXBException, IOException {
        SOAPFactory soapFactory = SOAPFactory.newInstance();
        MessageFactory messageFactory = MessageFactory.newInstance();
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();
        SOAPEnvelope soapEnvelope = soapPart.getEnvelope();
        SOAPHeader soapHeader = soapEnvelope.getHeader();

        if (soapHeader == null) {
            soapHeader = soapEnvelope.addHeader();
        }

        SOAPBody soapBody = soapEnvelope.getBody();
        if (soapBody == null) {
            soapBody = soapEnvelope.addBody();
        }

        Document bodyDoc = db.newDocument();
        this.getJAXBContext().createMarshaller().marshal(this.toObj(messageContext, "json"), bodyDoc);

        SOAPElement body = soapFactory.createElement(bodyDoc.getDocumentElement());
        body.removeNamespaceDeclaration("u");
        soapBody.addChildElement(body);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        soapMessage.writeTo(out);

        String content = new String(out.toByteArray());
        System.out.println(content);
        messageContext.setVariable("request.content", content);
    }

    private void setResponse(MessageContext messageContext)
            throws SOAPException, ParserConfigurationException, ClassNotFoundException, JAXBException, IOException {
        messageContext.setVariable("request.content", this.toJSON(messageContext));
    }

    @Override
    public ExecutionResult execute(MessageContext messageContext, ExecutionContext executionContext) {
        isRequestFlow = executionContext == null || executionContext.isRequestFlow();

        try {
            initJAXB();
            if (isRequestFlow) {
                setRequest(messageContext);
            } else {
                setResponse(messageContext);
            }
            return ExecutionResult.SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}

