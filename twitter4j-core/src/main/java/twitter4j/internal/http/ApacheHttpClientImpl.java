/*
 * Copyright 2007 Yusuke Yamamoto
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package twitter4j.internal.http;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.NameValuePair;

import static twitter4j.internal.http.RequestMethod.GET;
import static twitter4j.internal.http.RequestMethod.POST;

import twitter4j.conf.ConfigurationContext;

import twitter4j.internal.logging.Logger;
import twitter4j.internal.util.z_T4JInternalStringUtil;

import twitter4j.TwitterException;

/**
 * @author Paul Bourke - pauldbourke at gmail.com
 * @since Twitter4J 2.2.5
 */
public class ApacheHttpClientImpl extends HttpClientBase
        implements twitter4j.internal.http.HttpClient, HttpResponseCode,
                   java.io.Serializable {

    private static final Logger logger = Logger.getLogger(
            ApacheHttpClientImpl.class);

    private static boolean isJDK14orEarlier = false;

    private static final long serialVersionUID = -8819171414069621503L;

    public ApacheHttpClientImpl() {
        super(ConfigurationContext.getInstance());
    }

    public ApacheHttpClientImpl(HttpClientConfiguration conf) {
        super(conf);
        if (isProxyConfigured() && isJDK14orEarlier) {
            logger.warn("HTTP Proxy is not supported on JDK1.4 or earlier. " +
                    "Try twitter4j-httpclient-support artifact");
        }
    }

    private static final Map<HttpClientConfiguration,
            twitter4j.internal.http.HttpClient> instanceMap =
                new HashMap<HttpClientConfiguration,
            twitter4j.internal.http.HttpClient>(1);

    public static twitter4j.internal.http.HttpClient getInstance(
            HttpClientConfiguration conf) {
        twitter4j.internal.http.HttpClient client = instanceMap.get(conf);
        if (null == client) {
            client = new ApacheHttpClientImpl(conf);
            instanceMap.put(conf, client);
        }
        return client;
    }

    public twitter4j.internal.http.HttpResponse get(String url)
            throws TwitterException {
        return request(new HttpRequest(RequestMethod.GET, url, null, null,
                    null));
    }

    public twitter4j.internal.http.HttpResponse post(String url,
            HttpParameter[] params) throws TwitterException {
        return request(new HttpRequest(RequestMethod.POST, url, params, null,
                    null));
    }

    public twitter4j.internal.http.HttpResponse request(HttpRequest req)
            throws TwitterException {
        int retriedCount;
        int retry = CONF.getHttpRetryCount() + 1;
        twitter4j.internal.http.HttpResponse res = null;
        org.apache.http.HttpResponse _res = null;

        for (retriedCount = 0; retriedCount < retry; retriedCount++) {
            int responseCode = -1;
            try {
                HttpClient client = new DefaultHttpClient();

                if (req.getMethod() == POST) {
                    HttpPost postRequest = new HttpPost(req.getURL());
                    setHeaders(req, postRequest);
                    /*
                    if (HttpParameter.containsFile(req.getParameters())) {
                        String boundary = "----Twitter4J-upload" +
                            System.currentTimeMillis();
                        postRequest.addHeader("Content-Type",
                                "multipart/form-data; boundary=" + boundary);
                        boundary = "--" + boundary;
                        os = con.getOutputStream();
                        DataOutputStream out = new DataOutputStream(os);
                        for (HttpParameter param : req.getParameters()) {
                            if (param.isFile()) {
                                write(out, boundary + "\r\n");
                                write(out,
                                        "Content-Disposition: form-data; " +
                                        "name=\"" + param.getName() +
                                        "\"; filename=\"" +
                                        param.getFile().getName() +
                                        "\"\r\n");
                                write(out, "Content-Type: " +
                                        param.getContentType() + "\r\n\r\n");
                                BufferedInputStream in =
                                    new BufferedInputStream(
                                        param.hasFileBody() ?
                                        param.getFileBody() :
                                        new FileInputStream(
                                            param.getFile())
                                );
                                int buff;
                                while ((buff = in.read()) != -1) {
                                    out.write(buff);
                                }
                                write(out, "\r\n");
                                in.close();
                            } else {
                                write(out, boundary + "\r\n");
                                write(out,
                                        "Content-Disposition: form-data; " +
                                        "name=\"" + param.getName() +
                                        "\"\r\n");
                                write(out,
                                        "Content-Type: text/plain; " +
                                        "charset=UTF-8\r\n\r\n");
                                logger.debug(param.getValue());
                                out.write(param.getValue().getBytes(
                                            "UTF-8"));
                                write(out, "\r\n");
                            }
                        }
                        write(out, boundary + "--\r\n");
                        write(out, "\r\n");

                    } else {
                    */
                    postRequest.addHeader("Content-Type",
                            "application/x-www-form-urlencoded");
                    String postParam = HttpParameter.encodeParameters(
                            req.getParameters());
                    logger.debug("Post Params: ", postParam);
                    postRequest.setEntity(new StringEntity(postParam));
                    _res = client.execute(postRequest);
                    res = new ApacheHttpResponseImpl(_res, CONF);
                    /*}*/
                } else if (req.getMethod() == GET) {
                    HttpGet getReq = new HttpGet(req.getURL());
                    setHeaders(req, getReq);
                    _res = client.execute(getReq);
                    res = new ApacheHttpResponseImpl(_res, CONF);
                }

                // Read the response
                responseCode = _res.getStatusLine().getStatusCode();

                /*
                // Write out response to log if debug on
                if (logger.isDebugEnabled()) {
                    logger.debug("Response: ");
                    Map<String, List<String>> responseHeaders =
                        res.getHeaderFields();
                    for (String key : responseHeaders.keySet()) {
                        List<String> values = responseHeaders.get(key);
                        for (String value : values) {
                            if (key != null) {
                                logger.debug(key + ": " + value);
                            } else {
                                logger.debug(value);
                            }
                        }
                    }
                }
                */

                // Check actual response code
                if (responseCode < OK ||
                        (responseCode != FOUND &&
                         MULTIPLE_CHOICES <= responseCode)) {
                    if (responseCode == ENHANCE_YOUR_CLAIM ||
                            responseCode == BAD_REQUEST ||
                            responseCode < INTERNAL_SERVER_ERROR ||
                            retriedCount == CONF.getHttpRetryCount()) {
                        throw new TwitterException(res.asString(), res);
                    }
                    // will retry if the status code is INTERNAL_SERVER_ERROR
                } else {
                    break;
                }
            } catch (IOException ioe) {
                // connection timeout or read timeout
                if (retriedCount == CONF.getHttpRetryCount()) {
                    throw new TwitterException(ioe.getMessage(), ioe,
                            responseCode);
                }
            }

            try {
                if (logger.isDebugEnabled() && res != null) {
                    //res.asString();
                }
                logger.debug("Sleeping " +
                        CONF.getHttpRetryIntervalSeconds() +
                        " seconds until the next retry.");
                Thread.sleep(CONF.getHttpRetryIntervalSeconds() * 1000);
            } catch (InterruptedException ignore) {
                //nothing to do
            }
        }
        return res;
    }

    public static String encode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (java.io.UnsupportedEncodingException neverHappen) {
            throw new AssertionError("will never happen");
        }
    }

    /**
     * sets HTTP headers
     *
     * @param req        The request
     * @param connection HttpURLConnection
     */
    private void setHeaders(HttpRequest req, HttpRequestBase connection) {
        if (logger.isDebugEnabled()) {
            logger.debug("Request: ");
            logger.debug(req.getMethod().name() + " ", req.getURL());
        }

        String authorizationHeader;
        if (req.getAuthorization() != null &&
                (authorizationHeader =
                 req.getAuthorization().getAuthorizationHeader(req)) != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Authorization: ",
                        z_T4JInternalStringUtil.maskString(
                            authorizationHeader));
            }
            connection.addHeader("Authorization", authorizationHeader);
        }
        if (req.getRequestHeaders() != null) {
            for (String key : req.getRequestHeaders().keySet()) {
                connection.addHeader(key, req.getRequestHeaders().get(key));
                logger.debug(key + ": " + req.getRequestHeaders().get(key));
            }
        }
    }

}
