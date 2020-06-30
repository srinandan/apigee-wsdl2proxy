package com.google.cloud.edge.wsdl2proxy.network;

import com.google.cloud.edge.wsdl2proxy.utils.Generator;
import com.google.cloud.edge.wsdl2proxy.utils.ProxyConfig;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

@SuppressWarnings("serial")
public class WSDL2ProxyServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ProxyConfig proxyConfig = new ProxyConfig();
        proxyConfig.setBinding(req.getParameter("binding"));
        proxyConfig.setWsdlUrl(req.getParameter("wsdlUrl"));
        String zipFile = "";
        OutputStream outputStream = resp.getOutputStream();
        try {
            Generator generator = new Generator(proxyConfig);
            zipFile = generator.generateAPIProxy();
            File file = new File(zipFile);
            resp.setStatus(200);
            resp.setHeader("Content-Disposition", "filename=\"" + zipFile.replace("gen/", "") + "\"");
            resp.setHeader("Content-Type", "application/zip");
            Files.copy(file.toPath(), outputStream);
        } catch (Exception e) {
            e.printStackTrace();
            String response = "{ \"error\": \"Unknown Error Occurred\" }";
            resp.setHeader("Content-Type", "application/json");
            resp.setStatus(500);
            outputStream.write(response.getBytes());
        }
        System.out.println("Sent " + zipFile.replace("gen/", ""));
        System.out.println("Delete " + zipFile);
        Runtime.getRuntime().exec("rm " + zipFile);
        outputStream.close();
        System.out.println("Done");
    }
}