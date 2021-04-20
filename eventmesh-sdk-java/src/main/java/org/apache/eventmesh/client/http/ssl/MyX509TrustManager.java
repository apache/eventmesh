package org.apache.eventmesh.client.http.ssl;

import org.apache.commons.lang3.StringUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
public class MyX509TrustManager implements X509TrustManager {
    X509TrustManager myTrustManager;

    public MyX509TrustManager() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        String fileName = System.getProperty("ssl.client.cer","sChat2.jks");
        String pass = System.getProperty("ssl.client.pass", "sNetty");
        char[] filePass = null;
        if(StringUtils.isNotBlank(pass)){
            filePass = pass.toCharArray();
        }
        keyStore.load(Files.newInputStream(Paths.get(System.getProperty("confPath", System.getenv("confPath"))
                + File.separator
                + fileName), StandardOpenOption.READ), filePass);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        TrustManager trustManagers[] = trustManagerFactory.getTrustManagers();
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                myTrustManager = (X509TrustManager) trustManager;
                return;
            }
        }
        throw new Exception("Couldn't initialize");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
        if ((certificates != null) && (certificates.length == 1)) {
            certificates[0].checkValidity();
        } else {
            myTrustManager.checkServerTrusted(certificates, authType);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return myTrustManager.getAcceptedIssuers();
    }
}
