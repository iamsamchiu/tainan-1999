package tn.opendata.tainan311.georeportv2;

import android.text.TextUtils;
import android.util.Log;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closer;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import tn.opendata.tainan311.georeportv2.vo.Request;
import tn.opendata.tainan311.georeportv2.vo.RequestId;
import tn.opendata.tainan311.georeportv2.vo.Service;
import tn.opendata.tainan311.georeportv2.vo.ServiceDefinition;
import tn.opendata.tainan311.postdata.RequestData;

import static tn.opendata.tainan311.utils.EasyUtil.close;

/**
 * Created by vincent on 2014/6/6.
 */
public class GeoReportV2 {

    private static final String post_prefix = "http://fixmystreet.tw/";
    private static final String prefix = "http://fixmystreet.tw/open311/v2/";
    private static final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

    private static final String PATH_SERVICE_LIST = "services.json";
    private static final String PATH_SERVICE_DEFINITION = "services/%s.json";
    private static final String PATH_REQUESTS = "requests.json";
    private static final String PATH_POST_REQUEST = "report/new/mobile";
    private static final String PATH_SERVICE_REQUEST_ID_BY_TOKEN = "requests/%s.json";
    private static final String PATH_SERVICE_BY_REQUEST_ID = "requests/%s.json";
    private static final String PATH_SIGN_OUT = "auth/sign_out";
    private static final String PATH_SIGN_IN = "auth";

    private static final String DEFAULT_JURISDICTION_ID = "tainan.fixmystreet.tw";

    public static final String INTERFACE_USED_ANDROID = "android";
    public static final String INTERFACE_USED_WEB = "Web interface";

    private GeoReportV2() {
    }

    public static ListenableFuture<List<Service>> getServiceList() {
        return getServiceList(DEFAULT_JURISDICTION_ID);
    }

    public static ListenableFuture<List<Service>> getServiceList(final String id) {
        return executor.submit(new Callable<List<Service>>() {

            @Override
            public List<Service> call() throws Exception {
                String url = prefix + PATH_SERVICE_LIST;
                if (!TextUtils.isEmpty(id)) {
                    Map<String, Object> data = AttributeBuilder.newBuilder().put("jurisdiction_id", id).build();
                    url += "?" + encodeGetParameters(convert(data), HTTP.UTF_8);
                }
                return getServiceListResponse(new URL(url));
            }
        });
    }

    // fix my street has different format...
    @Deprecated
    public static ListenableFuture<ServiceDefinition> getServiceDefinition(final String id, final String service_code) {
        return executor.submit(new Callable<ServiceDefinition>() {
            @Override
            public ServiceDefinition call() throws Exception {
                if ( TextUtils.isEmpty(service_code) ) {
                    throw new RuntimeException("service_code must not be empty.");
                }
                String url = prefix + String.format(PATH_SERVICE_DEFINITION, service_code);
                if (!TextUtils.isEmpty(id)) {
                    Map<String, Object> data = AttributeBuilder.newBuilder().put("jurisdiction_id", id).build();
                    url += "?" + encodeGetParameters(convert(data), HTTP.UTF_8);
                }
                return getServiceDefinitionResponse(new URL(url));
            }
        });
    }

    // fix my street has different format...
    @Deprecated
    public static ListenableFuture<ServiceDefinition> getServiceDefinition(String service_code) {
        return getServiceDefinition(DEFAULT_JURISDICTION_ID, service_code);
    }

    public static <T> ListenableFuture<List<Cookie>> signIn(final String email, final String password) {
        return executor.submit(new Callable<List<Cookie>>() {
            @Override
            public List<Cookie> call() throws Exception {
                return doSignIn(new URL(post_prefix + PATH_SIGN_IN), email, password);
            }
        });
    }

    public static ListenableFuture<RequestId> getRequestIdByToken(final String jurisdiction_id, final String token) {
        return executor.submit(new Callable<RequestId>() {
            @Override
            public RequestId call() throws Exception {
                if (TextUtils.isEmpty(token)) {
                    throw new RuntimeException("token must not be empty.");
                }
                String url = prefix + String.format(PATH_SERVICE_REQUEST_ID_BY_TOKEN, token);
                if (!TextUtils.isEmpty(jurisdiction_id)) {
                    Map<String, Object> data = AttributeBuilder.newBuilder().put("jurisdiction_id", jurisdiction_id).build();
                    url += "?" + encodeGetParameters(convert(data), HTTP.UTF_8);
                }
                return getRequestIdResponse(new URL(url));
            }
        });
    }

    public static ListenableFuture<RequestId> getRequestIdByToken(String token) {
        return getRequestIdByToken(DEFAULT_JURISDICTION_ID, token);
    }

    public static ListenableFuture<Service> getServiceByRequestId(final String jurisdiction_id, final String requestId) {
        return executor.submit(new Callable<Service>() {
            @Override
            public Service call() throws Exception {
                if (TextUtils.isEmpty(requestId)) {
                    throw new RuntimeException("token must not be empty.");
                }
                String url = prefix + String.format(PATH_SERVICE_BY_REQUEST_ID, requestId);
                if (!TextUtils.isEmpty(jurisdiction_id)) {
                    Map<String, Object> data = AttributeBuilder.newBuilder().put("jurisdiction_id", jurisdiction_id).build();
                    url += "?" + new UrlEncodedFormEntity(convert(data), HTTP.UTF_8);
                }
                return getServiceByRequestIdResponse(new URL(url));
            }
        });
    }

    public static ListenableFuture<Service> getServiceByRequestId(String requestId) {
        return getServiceByRequestId(DEFAULT_JURISDICTION_ID, requestId);
    }

    private static <V> FMSResponse getPostResponse(URL url, Map<String, V> postData, List<Cookie> cookies) {
        Closer closer = Closer.create();
        try {
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(url.toURI());

            if (cookies != null) {
                for(Cookie c : cookies) {
                    post.setHeader("Cookie", c.getName() + "=" + c.getValue());
                }
            }
            boolean hasFile = false;
            Charset charset = Charset.forName(HTTP.UTF_8);
            if (postData.containsKey("photo")) {

            }
            String path = (String)postData.get("photo");
            File file = null;
            if ( !TextUtils.isEmpty(path) ) {
                file = new File(path);
            }
            MultipartEntityBuilder entity = MultipartEntityBuilder.create();
            entity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            entity.setCharset(charset);
            if (file != null && file.exists()) {
                entity.addBinaryBody("photo", file);
            }
            for (String k : postData.keySet()) {
                if (!"photo".equals(k)) {
                    entity.addTextBody(k, String.valueOf(postData.get(k)), ContentType.create("text/plain", charset));
                }
            }
            post.setEntity(entity.build());


            HttpResponse response = client.execute(post);
            int status = response.getStatusLine().getStatusCode();
//            if ( status != 200 && status != 201 ) {
//                throw new RuntimeException("Failed : HTTP error code : " + status);
//            }

            BufferedReader br = closer.register(new BufferedReader(new InputStreamReader(response.getEntity().getContent())));
            Gson gson = new Gson();
            Type type = new TypeToken<FMSResponse>(){}.getType();

            return gson.fromJson(br, type);
        } catch (Throwable e) {
            try {
                e.printStackTrace();
                closer.rethrow(e);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return null;
        } finally {
            close(closer);
        }
    }

    // will return null if error occurred

    private static Service getServiceByRequestIdResponse(URL url) {
        Closer closer = Closer.create();
        try {
            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
            int status = urlConnection.getResponseCode();

            if ( status != 200 && status != 201 ) {
                throw new RuntimeException("Failed : HTTP error code : " + status);
            }

            BufferedReader br = closer.register(new BufferedReader(new InputStreamReader(urlConnection.getInputStream())));
            Gson gson = new Gson();
            Type type = new TypeToken<Service>(){}.getType();

            return gson.fromJson(br, type);
        } catch (Throwable e) {
            try {
                closer.rethrow(e);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return null;
        } finally {
            close(closer);
        }
    }

    private static RequestId getRequestIdResponse(URL url) {
        Closer closer = Closer.create();
        try {
            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
            int status = urlConnection.getResponseCode();

            if ( status != 200 && status != 201 ) {
                throw new RuntimeException("Failed : HTTP error code : " + status);
            }

            BufferedReader br = closer.register(new BufferedReader(new InputStreamReader(urlConnection.getInputStream())));
            Gson gson = new Gson();
            Type type = new TypeToken<RequestId>(){}.getType();

            return gson.fromJson(br, type);
        } catch (Throwable e) {
            try {
                closer.rethrow(e);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return null;
        } finally {
            close(closer);
        }
    }

    private static <V> List<Cookie> doSignIn(URL url, String email, String password) {
        Closer closer = Closer.create();
        try {
            DefaultHttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(url.toURI());

//            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
//            int status = urlConnection.getResponseCode();

//            if ( status != 200 && status != 201 ) {
//                throw new RuntimeException("Failed : HTTP error code : " + status);
//            }

            HashMap<String, String> postData = Maps.newHashMap();
            postData.put("email", email);
            postData.put("password_sign_in", password);
            postData.put("name", "");
            postData.put("sign_in", "登入");
            postData.put("password_register", "");
            postData.put("remember_me", "1");

            post.setEntity(new UrlEncodedFormEntity(convert(postData), HTTP.UTF_8));

            HttpResponse response = client.execute(post);

            BufferedReader br = closer.register(new BufferedReader(new InputStreamReader(response.getEntity().getContent())));
            String read;
            while((read=br.readLine()) !=null) {
                // do nothing
                read = read;
            }
            CookieStore cookieStore = client.getCookieStore();
            return cookieStore.getCookies();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(closer);
        }
        return null;
    }

    private static ServiceDefinition getServiceDefinitionResponse(URL url) {
        Closer closer = Closer.create();
        try {
            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
            int status = urlConnection.getResponseCode();

            if ( status != 200 && status != 201 ) {
                throw new RuntimeException("Failed : HTTP error code : " + status);
            }

            BufferedReader br = closer.register(new BufferedReader(new InputStreamReader(urlConnection.getInputStream())));
            Gson gson = new Gson();
            Type type = new TypeToken<ServiceDefinition>(){}.getType();

            return gson.fromJson(br, type);
        } catch (Throwable e) {
            try {
                closer.rethrow(e);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return null;
        } finally {
            close(closer);
        }
    }

    private static List<Service> getServiceListResponse(URL url) {
        Closer closer = Closer.create();
        try {
            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
            int status = urlConnection.getResponseCode();

            if ( status != 200 && status != 201 ) {
                throw new RuntimeException("Failed : HTTP error code : " + status);
            }

            String data = Json311.fromFixMyStreet(closer.register(urlConnection.getInputStream()));

            Gson gson = new Gson();
            Type type = new TypeToken<List<Service>>() {
            }.getType();

            return gson.fromJson(data, type);
        } catch (Throwable e) {
            try {
                closer.rethrow(e);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return null;
        } finally {
            close(closer);
        }
    }

    private static List<Request> getRequestListResponse(URL url) {
        Closer closer = Closer.create();
        try {
            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
            int status = urlConnection.getResponseCode();

            if ( status != 200 && status != 201 ) {
                throw new RuntimeException("Failed : HTTP error code : " + status);
            }

            String data = Json311.fromFixMyStreet(closer.register(urlConnection.getInputStream()));
            Gson gson = new Gson();
            Type type = new TypeToken<List<Request>>(){}.getType();

            return gson.fromJson(data, type);
        } catch (Throwable e) {
            try {
                closer.rethrow(e);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return Lists.newArrayList();
        } finally {
            close(closer);
        }
    }

    private static class AttributeBuilder {
        private Map<String, Object> attribute;
        AttributeBuilder() {
            attribute = Maps.newHashMap();
        }

        public static AttributeBuilder newBuilder() {
            return new AttributeBuilder();
        }

        public <T> AttributeBuilder put(String key, T obj) {
            attribute.put(key, obj);
            return this;
        }

        public Map<String, Object> build() {
            return Collections.unmodifiableMap(attribute);
        }
    }

    private static <K, V> List<NameValuePair> convert(Map<K,V> args){
        List<NameValuePair> params = Lists.newArrayList();
        for(K k:args.keySet()){
            params.add(new BasicNameValuePair(String.valueOf(k), String.valueOf(args.get(k))));
        }

        return params;
    }

    private static String encodeGetParameters(List<NameValuePair> list, String charset) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        for(NameValuePair pair :list) {
            sb.append(pair.getName());
            sb.append("=");
            sb.append(URLEncoder.encode(pair.getValue(), charset));
            sb.append("&");
        }
        return sb.toString();
    }

    public static class QueryRequestBuilder {
        private AttributeBuilder data;
        private List<String> requestIdList;

        private QueryRequestBuilder(String jurisdiction_id) {
            requestIdList = Lists.newArrayList();
            data = AttributeBuilder.newBuilder();
            data.put("jurisdiction_id", jurisdiction_id);
        }

        private QueryRequestBuilder() {
            this(DEFAULT_JURISDICTION_ID);
        }

        public static QueryRequestBuilder create(String jurisdiction_id) {
            return new QueryRequestBuilder(jurisdiction_id);
        }

        public static QueryRequestBuilder create() {
            return new QueryRequestBuilder();
        }

        public QueryRequestBuilder requestId(String request_id) {
            requestIdList.add(request_id);
            return this;
        }

        public QueryRequestBuilder serviceCode(String service_code) {
            data.put("service_code", service_code);
            return this;
        }

        public QueryRequestBuilder startDate(Date start) {
            data.put("start_date", start.toString());
            return this;
        }

        public QueryRequestBuilder endDate(Date end) {
            data.put("end_date", end.toString());
            return this;
        }

        public QueryRequestBuilder endDate(String end) {
            data.put("end_date", end);
            return this;
        }

        public QueryRequestBuilder open() {
            data.put("status", "open");
            return this;
        }

        public QueryRequestBuilder closed() {
            data.put("status", "closed");
            return this;
        }

        /**
         * FixMyStreet Specific
         * @param limit
         * @return
         */
        public QueryRequestBuilder max_requests(int limit) {
            data.put("max_requests", limit);
            return this;
        }

        /**
         * FixMyStreet Specific
         * report via android/web/iphone...
         * available value : INTERFACE_USED_ANDROID or INTERFACE_USED_WEB
         * @return
         */
        public QueryRequestBuilder interface_used(String interfaceUsed) {
            data.put("interface_used", interfaceUsed);
            return this;
        }

        /**
         * FixMyStreet Specific
         * @param hasPhoto , true if need photo link in returned data
         * @return
         */
        public QueryRequestBuilder has_photo(boolean hasPhoto) {
            data.put("has_photo", hasPhoto ? 1 : 0);
            return this;
        }

        /**
         * FixMyStreet Specific
         * @param agencyId , ID of government body receiving the request.
         *                 Several IDs can be specified with | as a separator.
         * @return
         */
        public QueryRequestBuilder agency_responsible(String agencyId) {
            data.put("agency_responsible", agencyId);
            return this;
        }

        public QueryRequestBuilder build() {
            if ( requestIdList.size() > 0 ) {
                String requestList = TextUtils.join(",", requestIdList);
                data.put("service_request_id", requestList);
            }
            return this;
        }

        public ListenableFuture<List<Request>> execute() {
            return executor.submit(new Callable<List<Request>>() {
                @Override
                public List<Request> call() throws Exception {
                    Map<String, Object> map = data.build();
                    String path = prefix + PATH_REQUESTS + "?" + encodeGetParameters(convert(map), HTTP.UTF_8);
                    return getRequestListResponse(new URL(path));
                }
            });
        }

        @Override
        public String toString() {
            String path = "";
            try {
                Map<String, Object> map = data.build();
                path = prefix + PATH_REQUESTS + "?" + encodeGetParameters(convert(map), HTTP.UTF_8);
            } catch (Exception e) {
                e.printStackTrace();
                path = super.toString();
            }
            return path;
        }
    }

    public static class PostRequestBuilder {
        // required
        private AttributeBuilder data;
        private List<Cookie> cookies;

        private PostRequestBuilder(String jurisdiction_id, String service_code) {
            data = AttributeBuilder.newBuilder();
            data.put("category", service_code).put("pc", 0).put("may_show_name", 1);

            service();
        }

        private PostRequestBuilder(String jurisdiction_id, String service_code, double lat, double lon) {
            this(jurisdiction_id, service_code);
            location(lat, lon);
        }

        private PostRequestBuilder(String jurisdiction_id, String service_code, String address_string) {
            this(jurisdiction_id, service_code);
            location(address_string);
        }

        private PostRequestBuilder(String jurisdiction_id, String service_code, int location_id) {
            this(jurisdiction_id, service_code);
            location(location_id);
        }

        public PostRequestBuilder location(double lat, double lon) {
            data.put("lat", lat).put("lon", lon);
            return this;
        }

        public PostRequestBuilder location(String address_string) {
            data.put("address_string",address_string);
            return this;
        }

        public PostRequestBuilder location(int location_id) {
            data.put("location_id",location_id);
            return this;
        }

        public PostRequestBuilder title(String title) {
            data.put("title", title);
            return this;
        }

        public PostRequestBuilder service() {
            data.put("service", "android");
            return  this;
        }

        public PostRequestBuilder email(String email) {
            data.put("email", email);
            return this;
        }

        public PostRequestBuilder deviceId(String device_id) {
            data.put("device_id",device_id);
            return this;
        }

        public PostRequestBuilder accountId(String account_id) {
            data.put("account_id",account_id);
            return this;
        }

        public PostRequestBuilder firstName(String first_name) {
            data.put("first_name", first_name);
            return this;
        }

        public PostRequestBuilder lastName(String last_name) {
            data.put("last_name", last_name);
            return this;
        }

        public PostRequestBuilder description(String description) {
            data.put("detail", description);
            return this;
        }

        public PostRequestBuilder mediaUrl(String media_url) {
            data.put("media_url", media_url);
            return this;
        }

        public PostRequestBuilder phone(String phone) {
            data.put("phone", phone);
            return this;
        }

        public PostRequestBuilder name(String name) {
            if (!TextUtils.isEmpty(name)) {
                data.put("name", name);
                data.put("may_show_name", 1);
            }
            return this;
        }

        public PostRequestBuilder photo(String path) {
            data.put("photo", path);
            return this;
        }

        public PostRequestBuilder password(boolean needRegister, String password) {
            if (needRegister) {
                data.put("password_register", password);
                data.put("submit_register", "Submit");
            } else {
                //data.put("password_register", password);
                data.put("password_sign_in", password);
                data.put("submit_sign_in", 1);
                data.put("remember_me", 1);
                data.put("submit_problem", 1);
            }
            return this;
        }

        public PostRequestBuilder build() {

            return this;
        }

        public PostRequestBuilder addCookie(List<Cookie> cookie) {
            if ( cookie != null ) {
                cookies = new ArrayList<Cookie>(cookie);
            }
            return this;
        }

        public ListenableFuture<FMSResponse> execute() {
            return executor.submit(new Callable<FMSResponse>(){

                @Override
                public FMSResponse call() throws Exception {
                    return getPostResponse(new URL(post_prefix + PATH_POST_REQUEST), data.build(), cookies);
                }
            });

        }

        public static PostRequestBuilder create(RequestData data) {
            PostRequestBuilder builder = new PostRequestBuilder(DEFAULT_JURISDICTION_ID, data.getService_code(), data.getLatitude(), data.getLongitude());
            return builder.accountId(data.getAccount_id())
                    .description(data.getDescription())
                    .deviceId(data.getDevice_id())
                    .email(data.getEmail())
                    .firstName(data.getFist_name())
                    .lastName(data.getLast_name())
                    .mediaUrl(data.getMedia_url())
                    .phone(data.getPhone())
                    .name(data.getName());
        }

        public static PostRequestBuilder create(String jurisdiction_id, String service_code, double lat, double lon) {
            return new PostRequestBuilder(jurisdiction_id, service_code, lat, lon);
        }

        public static PostRequestBuilder create(String service_code, double lat, double lon, String title, String detail, String name, String email) {
            return new PostRequestBuilder(DEFAULT_JURISDICTION_ID, service_code, lat, lon)
                    .title(title).description(detail).name(name).email(email);
        }

        public static PostRequestBuilder create(String service_code, String address_string) {
            return new PostRequestBuilder(DEFAULT_JURISDICTION_ID, service_code, address_string);
        }

        public static PostRequestBuilder create(String service_code, int address_id) {
            return new PostRequestBuilder(DEFAULT_JURISDICTION_ID, service_code, address_id);
        }
    }
}
