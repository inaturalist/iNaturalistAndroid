package org.inaturalist.android;

import android.content.res.AssetManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

import org.inaturalist.android.api.AuthenticationException;
import org.inaturalist.android.api.ServerError;
import org.inaturalist.android.api.iNaturalistApi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ServerIntegrationTests {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);
    private iNaturalistApi.ApiHelper mHelper;
    private iNaturalistApi mApi;


    @Before
    public void setupApiWithCredentials() {
        // By default we are logged in. Other tests may override this
        mHelper = Mockito.mock(iNaturalistApi.ApiHelper.class);
        when(mHelper.credentials()).thenReturn("androidTest Fake Credentials");
        when(mHelper.getJWTToken()).thenReturn("androidTest Fake JWT");
        when(mHelper.getAnonymousJWTToken()).thenReturn("androidTest Fake AnonJWT");
        when(mHelper.getLoginType()).thenReturn(INaturalistService.LoginType.OAUTH_PASSWORD);

        mApi = new iNaturalistApi(wireMockRule.baseUrl(),
                wireMockRule.baseUrl(),
                mHelper);
    }

    @Test
    public void addFavorite() throws AuthenticationException, ServerError, IOException {
        String testUrl = ServerStubber.stubAddFavorite();

        //        POST /votes/vote/observation/47693827.json HTTP/1.1
        //        Authorization: Bearer ------
        //        Content-Length: 0
        //        Host: www.inaturalist.org
        //        Connection: Keep-Alive
        //        User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.10.0+ 6352195; SDK 23; generic_x86 Android SDK built for x86 sdk_google_phone_x86; OS Version 6.0)

        mApi.addFavorite(47694022);
        // TODO get some response and assert it somehow

        WireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo(testUrl))
                .withRequestBody(WireMock.absent())
                .withHeader("Authorization", new AnythingPattern()));
    }

    @Test
    public void removeFavorite() throws AuthenticationException, ServerError {
        //        DELETE /votes/unvote/observation/47693827.json HTTP/1.1
        //        Authorization: Bearer ----
        //        Host: www.inaturalist.org
        //        Connection: Keep-Alive
        //        User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.10.0+ 6352195; SDK 23; generic_x86 Android SDK built for x86 sdk_google_phone_x86; OS Version 6.0)

        String testUrl = ServerStubber.stubRemoveFavorite();
        mApi.removeFavorite(47693827);

        // Confirm how the API used the server
        WireMock.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo(testUrl))
                .withRequestBody(WireMock.absent())
                .withHeader("Authorization", new AnythingPattern()));
    }
}
