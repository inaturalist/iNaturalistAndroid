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

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
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
                wireMockRule.baseUrl(),
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
}
