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

import java.io.IOException;

import java.net.HttpURLConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.Header;
import org.apache.http.HeaderElement;

/**
 * @author Paul Bourke - pauldbourke at gmail.com
 * @since Twitter4J 2.2.5
 */
public class ApacheHttpResponseImpl
        extends twitter4j.internal.http.HttpResponse {

    private org.apache.http.HttpResponse con;

    ApacheHttpResponseImpl(org.apache.http.HttpResponse con,
            HttpClientConfiguration conf) throws IOException {
        super(conf);
        this.con = con;
        this.statusCode = con.getStatusLine().getStatusCode();
        // TODO: find out getErrorStream equivalent in HttpClient
        //if (null == (is = con.getErrorStream())) {
        //    is = con.getInputStream();
        //}
        is = con.getEntity().getContent();
        if (is != null && "gzip".equals(con.getEntity().getContentEncoding().
                    getValue())) {
            // the response is gzipped
            is = new GZIPInputStream(is);
        }
    }

    // for test purpose
    /*package*/ ApacheHttpResponseImpl(String content) {
        super();
        this.responseAsString = content;
    }

    public String getResponseHeader(String name) {
        return con.getFirstHeader(name).getValue();
    }

    @Override
    public Map<String, List<String>> getResponseHeaderFields() {
        Map<String, List<String>> ret = new HashMap<String, List<String>>();
        List<String> values = new ArrayList<String>();
        for (Header header : con.getAllHeaders()) {
            try {
                for (HeaderElement headerElement : header.getElements()) {
                    String headerName = headerElement.getName();
                    if (ret.containsKey(headerName)) {
                        List<String> curValues = ret.get(headerName);
                        curValues.add(headerElement.getValue());
                    } else {
                        List<String> curValues = new ArrayList<String>();
                        curValues.add(headerElement.getValue());
                        ret.put(headerName, curValues);
                    }
                }
            } catch (ParseException e) {
                // TODO log this, can't currently throw up stack as would break
                // interface
            }
        }

        return ret;
    }

    /**
     * {@inheritDoc}
     */
    public void disconnect() {
        //con.disconnect();
    }
}
