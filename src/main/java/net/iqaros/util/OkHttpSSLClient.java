package net.iqaros.util;

import java.security.cert.CertificateException;
//import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;

public class OkHttpSSLClient {

  private static SSLContext trustAllSslContext;
  private static SSLSocketFactory trustAllSslSocketFactory = null;

  public OkHttpSSLClient() {
    getTrustAllSslSocketFactory();
  }

  private static SSLSocketFactory getTrustAllSslSocketFactory() {
    if (trustAllSslSocketFactory == null ) {
      try {        	
        trustAllSslContext = SSLContext.getInstance("SSL");
        trustAllSslContext.init(null, trustAllCerts, new java.security.SecureRandom());
      } catch (Exception e ) {
        throw new RuntimeException(e);
      }
    }
    trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();
    return trustAllSslSocketFactory;
  }

  public static OkHttpClient clientAcceptingSelfSignedCertificates() {
    Builder builder = new OkHttpClient().newBuilder();
    /*builder.readTimeout(300, TimeUnit.SECONDS)
               .connectTimeout(300, TimeUnit.SECONDS);
     */
    builder.sslSocketFactory(getTrustAllSslSocketFactory(), (X509TrustManager)trustAllCerts[0]);
    builder.hostnameVerifier(new HostnameVerifier() {
      @Override
      public boolean verify(String hostname, SSLSession session) {
        return true;
      }
    });
    return builder.build();
  }

  private static final TrustManager[] trustAllCerts = new TrustManager[] {
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
          return new java.security.cert.X509Certificate[]{};
        }
      }
  };
}
