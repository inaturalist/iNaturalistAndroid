package org.inaturalist.android;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;

import org.inaturalist.android.api.AuthenticationException;
import org.inaturalist.android.api.ServerError;
import org.inaturalist.android.api.iNaturalistApi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ServerIntegrationTests {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());
    private iNaturalistApi.ApiHelper mHelper;
    private iNaturalistApi mApi;


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

        mApi = new iNaturalistApi(wireMockRule.baseUrl(),
                wireMockRule.baseUrl() + "/v1",
                mHelper);
    }

    @Test
    public void addFavorite() throws AuthenticationException, ServerError, IOException {
        String testUrl = ServerStubber.stubAddFavorite();

        mApi.addFavorite(47694022);
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
    public void removeFavorite() throws AuthenticationException, ServerError {
        String testUrl = ServerStubber.stubRemoveFavorite();
        mApi.removeFavorite(47693827);
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
    public void addComment() throws AuthenticationException, ServerError, IOException {
        String testUrl = ServerStubber.stubAddComment();
        mApi.addComment(47575188, "Test");

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
    }

    @Test
    public void deleteComment() throws AuthenticationException, ServerError, IOException {
        String testUrl = ServerStubber.stubDeleteComment();
        mApi.deleteComment(4713818);

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
    }

    @Test
    public void taxonSuggestions() throws AuthenticationException, ServerError, IOException, ParseException {

        File cachedFile = copyAssetIntoExternalCache("taxon_suggestions.jpeg");

        String testUrl = ServerStubber.stubTaxonSuggestions();
        Double lat = 39.0455500388;
        Double lng = -78.153687939;
        BetterJSONObject result = mApi.getTaxonSuggestions(Locale.ENGLISH,
                cachedFile.getAbsolutePath(),
                lat, lng,
                new Timestamp((new SimpleDateFormat("yyyy-MM-dd"))
                        .parse("2020-05-28").getTime()));

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
