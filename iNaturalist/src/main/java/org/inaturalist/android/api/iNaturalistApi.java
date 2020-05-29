package org.inaturalist.android.api;

import android.widget.Toast;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.inaturalist.android.INaturalistService;
import org.inaturalist.android.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class iNaturalistApi {

    private static String TAG = "ServerApi";
    private final ApiHelper mHelper;
    private final String API_HOST;

    public iNaturalistApi(String host, ApiHelper helper) {
        API_HOST = host;
        mHelper = helper;
    }

    /**
     * Temporary interface to allow the API code to call back into the service
     * for stuff that is currently tightly coupled to Android-isms making it hard
     * to extract directly into this non-Android-enabled class
     */
    public interface ApiHelper {
        String getUserAgent();
        String credentials();
        String getJWTToken();
        String getAnonymousJWTToken();
        INaturalistService.LoginType getLoginType();
        boolean ensureCredentials() throws AuthenticationException;
    }


    private JSONArray request(String url, String method, ArrayList<NameValuePair> params, JSONObject jsonContent, boolean authenticated, boolean useJWTToken, boolean allowAnonymousJWTToken) throws AuthenticationException, ServerError {
        CloseableHttpClient client = HttpClientBuilder.create()
                // Faster reading of data
                .disableContentCompression()
                // Handle redirects (301/302) for all HTTP methods (including POST)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setUserAgent(mHelper.getUserAgent())
                .build();

//        Logger.tag(TAG).debug(String.format("%s (%b - %s): %s", method, authenticated,
//                authenticated ? mCredentials : "<null>",
//                url));

        HttpRequestBase request;

        Logger.tag(TAG).debug(String.format("URL: %s - %s (params: %s / %s)", method, url, (params != null ? params.toString() : "null"), (jsonContent != null ? jsonContent.toString() : "null")));

        if (method.equalsIgnoreCase("post")) {
            request = new HttpPost(url);
        } else if (method.equalsIgnoreCase("delete")) {
            request = new HttpDelete(url);
        } else if (method.equalsIgnoreCase("put")) {
            request = new HttpPut(url);
        } else {
            request = new HttpGet(url);
        }

        // POST params
        if (jsonContent != null) {
            // JSON body content
            request.setHeader("Content-type", "application/json");
            StringEntity entity = null;
            try {
                entity = new StringEntity(jsonContent.toString(), HTTP.UTF_8);
            } catch (Exception exc) {
                Logger.tag(TAG).error(exc);
            }

            if (method.equalsIgnoreCase("put")) {
                ((HttpPut) request).setEntity(entity);
            } else {
                ((HttpPost) request).setEntity(entity);
            }

        } else if (params != null) {
            // "Standard" multipart encoding
            Charset utf8Charset = Charset.forName("UTF-8");
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).getName().equalsIgnoreCase("image") || params.get(i).getName().equalsIgnoreCase("file") || params.get(i).getName().equalsIgnoreCase("user[icon]") || params.get(i).getName().equalsIgnoreCase("audio")) {
                    // If the key equals to "image", we use FileBody to transfer the data
                    String value = params.get(i).getValue();
                    if (value != null) {
                        if (params.get(i).getName().equalsIgnoreCase("audio")) {
                            File file = new File(value);
                            entity.addPart("file", new FileBody(file, ContentType.parse("audio/" + value.substring(value.lastIndexOf(".") + 1)), file.getName()));
                            request.setHeader("Accept", "application/json");
                        } else {
                            entity.addPart(params.get(i).getName(), new FileBody(new File(value)));
                        }
                    }
                } else {
                    // Normal string data
                    try {
                        entity.addPart(params.get(i).getName(), new StringBody(params.get(i).getValue(), utf8Charset));
                    } catch (UnsupportedEncodingException e) {
                        Logger.tag(TAG).error("failed to add " + params.get(i).getName() + " to entity for a " + method + " request: " + e);
                    }
                }
            }
            if (method.equalsIgnoreCase("put")) {
                ((HttpPut) request).setEntity(entity);
            } else {
                ((HttpPost) request).setEntity(entity);
            }
        }

        if (url.startsWith(API_HOST) && (mHelper.credentials() != null)) {
            // For the node API, if we're logged in, *always* use JWT authentication
            authenticated = true;
            useJWTToken = true;
        }

        if (authenticated) {
            if (useJWTToken && allowAnonymousJWTToken && (mHelper.credentials() == null)) {
                // User not logged in, but allow using anonymous JWT
                request.setHeader("Authorization", mHelper.getAnonymousJWTToken());
            } else {
                mHelper.ensureCredentials();

                if (useJWTToken) {
                    // Use JSON Web Token for this request
                    request.setHeader("Authorization", mHelper.getJWTToken());
                } else if (mHelper.getLoginType() == INaturalistService.LoginType.PASSWORD) {
                    // Old-style password authentication
                    request.setHeader("Authorization", "Basic " + mHelper.credentials());
                } else {
                    // OAuth2 token (Facebook/G+/etc)
                    request.setHeader("Authorization", "Bearer " + mHelper.credentials());
                }
            }
        }

        try {
            mResponseErrors = null;
            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String content = entity != null ? EntityUtils.toString(entity) : null;

            Logger.tag(TAG).debug("Response: " + response.getStatusLine().toString());
            Logger.tag(TAG).debug(String.format("(for URL: %s - %s (params: %s / %s))", method, url, (params != null ? params.toString() : "null"), (jsonContent != null ? jsonContent.toString() : "null")));
            Logger.tag(TAG).debug(content);

            JSONArray json = null;
            mLastStatusCode = response.getStatusLine().getStatusCode();

            switch (mLastStatusCode) {
                //switch (response.getStatusCode()) {
                case HttpStatus.SC_UNPROCESSABLE_ENTITY:
                    // Validation error - still need to return response
                    Logger.tag(TAG).error(response.getStatusLine().toString());
                case HttpStatus.SC_OK:
                    try {
                        json = new JSONArray(content);
                    } catch (JSONException e) {
                        try {
                            JSONObject jo = new JSONObject(content);
                            json = new JSONArray();
                            json.put(jo);
                        } catch (JSONException e2) {
                        }
                    }

                    mResponseHeaders = response.getAllHeaders();

                    try {
                        if ((json != null) && (json.length() > 0)) {
                            JSONObject result = json.getJSONObject(0);
                            if (result.has("errors")) {
                                // Error response
                                Logger.tag(TAG).error("Got an error response: " + result.get("errors").toString());
                                mResponseErrors = result.getJSONArray("errors");
                                return null;
                            }
                        }
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
                    }

                    if ((content != null) && (content.length() == 0)) {
                        // In case it's just non content (but OK HTTP status code) - so there's no error
                        json = new JSONArray();
                    }

                    return json;

                case HttpStatus.SC_UNAUTHORIZED:
                    throw new AuthenticationException();
                case HttpURLConnection.HTTP_UNAVAILABLE:
                    Logger.tag(TAG).error("503 server unavailable");

                    Date mRetryAfterDate = null;

                    // Find out if there's a "Retry-After" header
                    Header[] headers = response.getHeaders("Retry-After");
                    if ((headers != null) && (headers.length > 0)) {
                        for (Header header : headers) {
                            String timestampString = header.getValue();
                            Logger.tag(TAG).error("Retry after raw string: " + timestampString);
                            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
                            try {
                                mRetryAfterDate = format.parse(timestampString);
                                Logger.tag(TAG).error("Retry after: " + mRetryAfterDate);
                                break;
                            } catch (ParseException e) {
                                Logger.tag(TAG).error(e);
                                try {
                                    // Try parsing it as a seconds-delay value
                                    int secondsDelay = Integer.valueOf(timestampString);
                                    Logger.tag(TAG).error("Retry after: " + secondsDelay);
                                    Calendar calendar = Calendar.getInstance();
                                    calendar.add(Calendar.SECOND, secondsDelay);
                                    mRetryAfterDate = calendar.getTime();

                                    break;
                                } catch (NumberFormatException exc) {
                                    Logger.tag(TAG).error(exc);
                                }
                            }
                        }
                    }
                    throw new ServerError(mRetryAfterDate);

                    return null;
                case HttpStatus.SC_GONE:
                    Logger.tag(TAG).error("GONE: " + response.getStatusLine().toString());
                    // TODO create notification that informs user some observations have been deleted on the server,
                    // click should take them to an activity that lets them decide whether to delete them locally
                    // or post them as new observations
                default:
                    Logger.tag(TAG).error(response.getStatusLine().toString());
            }
        } catch (IOException e) {
            Logger.tag(TAG).error("Error for URL " + url + ":" + e);
            Logger.tag(TAG).error(e);
        }
        return null;
    }
}
