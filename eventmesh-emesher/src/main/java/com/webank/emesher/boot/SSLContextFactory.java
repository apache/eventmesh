package com.webank.emesher.boot;

import com.webank.emesher.constants.ProxyConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;

public class SSLContextFactory {
    private static Logger httpLogger = LoggerFactory.getLogger("http");

    private static String protocol = "TLSv1.1";

    private static String fileName;

    private static String pass;


    public static SSLContext getSslContext(){
        SSLContext sslContext = null;
        try{
            protocol = System.getProperty("ssl.server.protocol", "TLSv1.1");

            fileName = System.getProperty("ssl.server.cer","sChat2.jks");

            char[] filePass = null;
            pass = System.getProperty("ssl.server.pass","sNetty");
            if(StringUtils.isNotBlank(pass)){
                filePass = pass.toCharArray();
            }
            sslContext = SSLContext.getInstance(protocol);
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(Files.newInputStream(Paths.get(ProxyConstants.PROXY_CONF_HOME
                    + File.separator
                    + fileName), StandardOpenOption.READ), filePass);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, filePass);
            sslContext.init(kmf.getKeyManagers(), null, null);
        }catch (Exception e){
            httpLogger.warn("sslContext init failed", e);
            sslContext = null;
        }
        return sslContext;
    }
}
