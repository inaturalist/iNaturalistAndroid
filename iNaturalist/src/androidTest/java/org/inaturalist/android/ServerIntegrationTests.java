package org.inaturalist.android;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;

import org.inaturalist.android.api.ApiCallback;
import org.inaturalist.android.api.ApiError;
import org.inaturalist.android.api.iNaturalistApi;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@RunWith(AndroidJUnit4.class)
public class ServerIntegrationTests {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());
    private iNaturalistApi.ApiHelper mHelper;
    private iNaturalistApi mApi;
    private ApiCallback mCallback;

    @Before
    public void setupApiWithCredentials() {
        // By default we are logged in. Other tests may override this
        mHelper = Mockito.mock(iNaturalistApi.ApiHelper.class);
        when(mHelper.credentials()).thenReturn("androidTest Credentials");
        when(mHelper.getJWTToken()).thenReturn("androidTest JWT");
        when(mHelper.getAnonymousJWTToken()).thenReturn("androidTest AnonJWT");
        when(mHelper.getLoginType()).thenReturn(INaturalistService.LoginType.OAUTH_PASSWORD);
        when(mHelper.getUserAgent()).thenReturn(
                INaturalistService.getUserAgent(ApplicationProvider.getApplicationContext()));
        mCallback = Mockito.mock(ApiCallback.class);

        mApi = new iNaturalistApi(wireMockRule.baseUrl(),
                wireMockRule.baseUrl() + "/v1",
                mHelper, true);
    }

    @Test
    public void addFavorite() throws IOException {
        String testUrl = ServerStubber.stubAddFavorite();

        mApi.addFavorite(47694022, mCallback);
        // TODO get some response and assert it somehow

        //        POST /votes/vote/observation/47693827.json HTTP/1.1
        //        Authorization: Bearer ------
        //        Content-Length: 0
        //        Host: www.inaturalist.org
        //        Connection: Keep-Alive
        //        User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.10.0+ 6352195; SDK 23; generic_x86 Android SDK built for x86 sdk_google_phone_x86; OS Version 6.0)
        WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo(testUrl))
                .withRequestBody(WireMock.absent())
                .withHeader("Authorization", new AnythingPattern())
                .withHeader("User-Agent", containing("iNaturalist"))
        );
    }

    @Test
    public void removeFavorite() {
        String testUrl = ServerStubber.stubRemoveFavorite();
        mApi.removeFavorite(47693827, mCallback);
        // TODO get some response and assert it somehow

        //        DELETE /votes/unvote/observation/47693827.json HTTP/1.1
        //        Authorization: Bearer ----
        //        Host: www.inaturalist.org
        //        Connection: Keep-Alive
        //        User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.10.0+ 6352195; SDK 23; generic_x86 Android SDK built for x86 sdk_google_phone_x86; OS Version 6.0)
        WireMock.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo(testUrl))
                .withRequestBody(WireMock.absent())
                .withHeader("Authorization", new AnythingPattern())
                .withHeader("User-Agent", containing("iNaturalist"))
        );
    }

    @Test
    public void addComment() throws ApiError, IOException {
        String testUrl = ServerStubber.stubAddComment();
        mApi.addComment(47575188, "Test", mCallback);

        //    POST /comments.json HTTP/1.1
        //    Authorization: Bearer ----
        //    Content-Length: 347
        //    Content-Type: multipart/form-data; boundary=7D1GzAl3hcdBz2_jf8Q_VsJ2ks3Xhg
        //    Host: www.inaturalist.org
        //    Connection: Keep-Alive
        //    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.10.0+ 6352195; SDK 23; generic_x86 Android SDK built for x86 sdk_google_phone_x86; OS Version 6.0)
        //
        //            --7D1GzAl3hcdBz2_jf8Q_VsJ2ks3Xhg
        //    Content-Disposition: form-data; name="comment[parent_id]"
        //
        //            47575188
        //            --7D1GzAl3hcdBz2_jf8Q_VsJ2ks3Xhg
        //    Content-Disposition: form-data; name="comment[parent_type]"
        //
        //    Observation
        //--7D1GzAl3hcdBz2_jf8Q_VsJ2ks3Xhg
        //    Content-Disposition: form-data; name="comment[body]"
        //
        //    Test
        //--7D1GzAl3hcdBz2_jf8Q_VsJ2ks3Xhg--
        WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo(testUrl))
                .withHeader("Authorization", new AnythingPattern())
                .withRequestBodyPart(aMultipart().withName("comment[parent_id]").withBody(equalTo("47575188")).build())
                .withRequestBodyPart(aMultipart().withName("comment[body]").withBody(equalTo("Test")).build())
                .withRequestBodyPart(aMultipart().withName("comment[parent_type]").withBody(equalTo("Observation")).build())
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withHeader("User-Agent", containing("iNaturalist"))
        );

        verify(mCallback).onResponse(any(), any());
    }

    @Test
    public void deleteComment() throws ApiError, IOException {
        String testUrl = ServerStubber.stubDeleteComment();
        mApi.deleteComment(4713818, mCallback);

        //    DELETE /comments/4713818.json HTTP/1.1
        //    Authorization: Bearer ---
        //    Host: www.inaturalist.org
        //    Connection: Keep-Alive
        //    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.10.0+ 6352195; SDK 23; generic_x86 Android SDK built for x86 sdk_google_phone_x86; OS Version 6.0)
        WireMock.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo(testUrl))
                .withRequestBody(WireMock.absent())
                .withHeader("User-Agent", containing("iNaturalist"))
                .withHeader("Authorization", new AnythingPattern())
        );

        verify(mCallback).onResponse(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void taxonSuggestions() throws ApiError, IOException, ParseException {

        File cachedFile = copyAssetIntoExternalCache("taxon_suggestions.jpeg");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        String testUrl = ServerStubber.stubTaxonSuggestions();
        Double lat = 39.0455500388;
        Double lng = -78.153687939;
        mApi.getTaxonSuggestions(Locale.ENGLISH,
                cachedFile.getAbsolutePath(),
                lat, lng,
                new Timestamp((new SimpleDateFormat("yyyy-MM-dd"))
                        .parse("2020-05-28").getTime()),
                mCallback);

        byte[] imageContent = Files.readAllBytes(Paths.get(cachedFile.toURI()));
        WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo(testUrl))
                .withHeader("User-Agent", containing("iNaturalist"))
                .withHeader("Authorization", new AnythingPattern())
                .withHeader("Authorization", new AnythingPattern())
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withRequestBodyPart(aMultipart()
                        .withName("locale").withBody(equalTo("en")).build())
                .withRequestBodyPart(aMultipart()
                        .withName("lat").withBody(equalTo(lat.toString())).build())
                .withRequestBodyPart(aMultipart()
                        .withName("lng").withBody(equalTo(lng.toString())).build())
                .withRequestBodyPart(aMultipart()
                        .withName("observed_on").withBody(equalTo("2020-05-28")).build())
                .withRequestBodyPart(aMultipart()
                        .withName("image").withBody(binaryEqualTo(imageContent)).build())
        );

        verify(mCallback).onResponse(any(), captor.capture());
        Object resultObj = captor.getValue();
        BetterJSONObject result = ((BetterJSONObject) resultObj);
        assertTrue(result.has("results"));
    }

    @Test
    public void deletePinnedLocation() throws ApiError, IOException {
        String testUrl = ServerStubber.stubDeletePinnedLocation();
        mApi.deletePinnedLocation("93992", mCallback);

        //    DELETE https://www.inaturalist.org/saved_locations/93992.json (on Connection{www.inaturalist.org:443, proxy=DIRECT hostAddress=www.inaturalist.org/51.143.92.118:443 cipherSuite=TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 protocol=http/1.1})
        //    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.18.91+ 5455776; SDK 27; generic_x86 Android SDK built for x86 sdk_gphone_x86; OS Version 8.1.0)
        //    Authorization: Bearer -------
        //    Content-Length: 0
        //    Host: www.inaturalist.org
        //    Connection: Keep-Alive
        //    Accept-Encoding: gzip
        WireMock.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo(testUrl))
                .withHeader("User-Agent", containing("iNaturalist"))
                .withHeader("Authorization", new AnythingPattern())
        );

        verify(mCallback).onResponse(any(), any());
    }

    @Test
    public void negative_savePinnedLocation() throws ApiError, IOException {
        String testUrl = ServerStubber.stubError_savePinnedLocation();
        mApi.pinLocation(0.1, 0.2,0.3, "Open",
                "My Location", mCallback);

        //    POST https://www.inaturalist.org/saved_locations.json (on Connection{www.inaturalist.org:443, proxy=DIRECT hostAddress=www.inaturalist.org/51.143.92.118:443 cipherSuite=TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 protocol=http/1.1})
        //    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.18.91+ 5455776; SDK 27; generic_x86 Android SDK built for x86 sdk_gphone_x86; OS Version 8.1.0)
        //    Authorization: Bearer ------
        //    Content-Type: multipart/form-data; boundary=217e9e58-9f41-4b3f-a5b4-ce186dd5d76f
        //    Content-Length: 779
        //    Host: www.inaturalist.org
        //    Connection: Keep-Alive
        //    Accept-Encoding: gzip
        WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo(testUrl))
                .withHeader("User-Agent", containing("iNaturalist"))
                .withHeader("Authorization", new AnythingPattern())
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[latitude]").withBody(equalTo("0.1")).build())
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[longitude]").withBody(equalTo("0.2")).build())
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[positional_accuracy]").withBody(equalTo("0.3")).build())
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[geoprivacy]").withBody(equalTo("Open")).build())
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[title]").withBody(equalTo("My Location")).build())
        );

        // Yes, as odd as this is, it's a proper response b/c it came from network and was
        // decoded OK. onApiError is only if the network or decode fails in some unexpected way
        verify(mCallback).onResponse(any(), any());
    }


    @Test
    public void pinLocation() throws ApiError, IOException {
        String testUrl = ServerStubber.pinLocation();
        mApi.pinLocation(39.0414391318, -78.1607491896,10,
                "Open", "Chickens", mCallback);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        //    POST https://www.inaturalist.org/saved_locations.json (on Connection{www.inaturalist.org:443, proxy=DIRECT hostAddress=www.inaturalist.org/51.143.92.118:443 cipherSuite=TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 protocol=http/1.1})
        //    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.18.91+ 5455776; SDK 27; generic_x86 Android SDK built for x86 sdk_gphone_x86; OS Version 8.1.0)
        //    Authorization: Bearer ------
        //    Content-Type: multipart/form-data; boundary=217e9e58-9f41-4b3f-a5b4-ce186dd5d76f
        //    Content-Length: 779
        //    Host: www.inaturalist.org
        //    Connection: Keep-Alive
        //    Accept-Encoding: gzip
        WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo(testUrl))
                .withHeader("User-Agent", containing("iNaturalist"))
                .withHeader("Authorization", new AnythingPattern())
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[latitude]").withBody(equalTo("39.0414391318")).build())
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[longitude]").withBody(equalTo("-78.1607491896")).build())
                // Note the oddness - 10 turns into 10.0
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[positional_accuracy]").withBody(equalTo("10.0")).build())
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[geoprivacy]").withBody(equalTo("Open")).build())
                .withRequestBodyPart(aMultipart()
                        .withName("saved_location[title]").withBody(equalTo("Chickens")).build())
        );

        // Yes, as odd as this is, it's a proper response b/c it came from network and was
        // decoded OK. onApiError is only if the network or decode fails in some unexpected way
        verify(mCallback).onResponse(any(), any());
    }

    /**
     * Used for tests that need to pass a filename into the class-under-test. Those run in the app
     * context and cannot access files not hosted in the app's own external storage folder
     */
    private static File copyAssetIntoExternalCache(String assetFilename) throws IOException {
        InputStream is = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(assetFilename);
        File out = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalCacheDir(),
                assetFilename);
        OutputStream os = new FileOutputStream(out);

        try {
            // buffer size 1K
            byte[] buf = new byte[1024];

            int bytesRead;
            while ((bytesRead = is.read(buf)) > 0) {
                os.write(buf, 0, bytesRead);
            }
        } finally {
            is.close();
            os.close();
        }
        return out;
    }
}
