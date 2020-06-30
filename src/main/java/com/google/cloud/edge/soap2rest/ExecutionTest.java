package com.google.cloud.edge.soap2rest;

import com.apigee.flow.message.Message;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import com.google.cloud.edge.soap2rest.core.JAXBExecution;
import com.google.cloud.edge.soap2rest.models.Customer;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.MarshallerProperties;

import com.apigee.flow.FlowInfo;
import com.apigee.flow.message.Connection;
import com.apigee.flow.message.FlowContext;
import com.apigee.flow.message.MessageContext;
import com.apigee.flow.message.TransportMessage;

/**
 * Created by mviswanathan on 23/03/17.
 */
public class ExecutionTest {
    public static void main(String[] args) {
        Map<String, String> jaxbProps = new HashMap<>();

        Customer cust = new Customer();
        cust.setAge(30);
        cust.setId(100);
        cust.setName("John");

        try {
            final StringWriter sw = new StringWriter();
            JAXBContext jaxbContext = JAXBContextFactory.createContext(new Class[]{Customer.class}, jaxbProps);

            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(MarshallerProperties.MEDIA_TYPE, "application/json");
            jaxbMarshaller.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, true);
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.marshal(cust, sw);

            Map<String, String> props = new HashMap<>();
            props.put("classes", "com.google.cloud.edge.soap2rest.Customer");
            props.put("contextClass", "com.google.cloud.edge.soap2rest.Customer");
            props.put("local", "true");
            props.put("body", sw.toString());

            JAXBExecution jaxbExecution = new JAXBExecution(props);
            MessageContext messageContext = new MessageContext() {

                @Override
                public <T extends Comparable> T get(String arg0) {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public boolean addFlowInfo(FlowInfo arg0) {
                    // TODO Auto-generated method stub
                    return false;
                }

                @Override
                public Message createMessage(TransportMessage arg0) {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public Connection getClientConnection() {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public Message getErrorMessage() {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public <T extends FlowInfo> T getFlowInfo(String arg0) {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public Message getMessage() {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public Message getMessage(FlowContext arg0) {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public Message getRequestMessage() {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public Message getResponseMessage() {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public Connection getTargetConnection() {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public <T> T getVariable(String arg0) {
                    // TODO Auto-generated method stub
                    return (T) sw.toString();
                }

                @Override
                public void removeFlowInfo(String arg0) {
                    // TODO Auto-generated method stub

                }

                @Override
                public boolean removeVariable(String arg0) {
                    // TODO Auto-generated method stub
                    return false;
                }

                @Override
                public void setErrorMessage(Message arg0) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void setMessage(FlowContext arg0, Message arg1) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void setRequestMessage(Message arg0) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void setResponseMessage(Message arg0) {
                    // TODO Auto-generated method stub

                }

                @Override
                public boolean setVariable(String arg0, Object arg1) {
                    // TODO Auto-generated method stub
                    return false;
                }
            };
            messageContext.setVariable("request.content", sw.toString());
            System.out.println(sw.toString());
            jaxbExecution.execute(messageContext, null);
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }
}
