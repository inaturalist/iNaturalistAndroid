package org.inaturalist.android;

import androidx.test.platform.app.InstrumentationRegistry;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ServerStubber {

    private static String getAsset(String filename) throws IOException {
        InputStream inputStream = InstrumentationRegistry.getInstrumentation().
                getContext().getAssets().open(filename);
        BufferedReader r = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        for (String line; (line = r.readLine()) != null; ) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static String stubAddFavorite() throws IOException {

//        HTTP/1.1 200 OK
//        Server: nginx
//        Date: Fri, 29 May 2020 14:38:08 GMT
//        Content-Type: application/json; charset=utf-8
//        Transfer-Encoding: chunked
//        Status: 200 OK
//        Cache-Control: max-age=0, private, must-revalidate
//        X-XSS-Protection: 1; mode=block
//        X-Request-Id: a73a76b6-22da-4334-ad3f-66e5d4d49620
//        ETag: W/"5ba217f0c7311607e7a0b0c4bb97ac6d"
//        X-Frame-Options: SAMEORIGIN
//        X-Runtime: 1.732370
//        X-Content-Type-Options: nosniff
//        Age: 0
//        X-Cache: MISS
//        Accept-Ranges: bytes
//        Connection: keep-alive
//
//        {"id":47693827,"observed_on":"2020-05-28","description":null,"latitude":"39.0455500388","longitude":"-78.153687939","map_scale":null,"timeframe":null,"species_guess":"Bluegill","user_id":1255620,"taxon_id":49591,"created_at":"2020-05-28T22:47:39.923-04:00","updated_at":"2020-05-29T10:38:07.235-04:00","place_guess":"White Post, VA 22663, USA","id_please":false,"observed_on_string":"2020-05-28 1:36:09 PM EDT","iconic_taxon_id":47178,"num_identification_agreements":2,"num_identification_disagreements":0,"time_observed_at":"2020-05-28T13:36:09.000-04:00","time_zone":"Eastern Time (US \u0026 Canada)","location_is_exact":false,"delta":false,"positional_accuracy":24,"private_latitude":null,"private_longitude":null,"private_positional_accuracy":null,"geoprivacy":null,"quality_grade":"research","positioning_method":"gps","positioning_device":"gps","out_of_range":null,"license":"CC0","uri":"https://www.inaturalist.org/observations/47693827","observation_photos_count":1,"comments_count":0,"zic_time_zone":"America/New_York","oauth_application_id":2,"observation_sounds_count":0,"identifications_count":3,"captive":false,"community_taxon_id":49591,"site_id":1,"old_uuid":null,"public_positional_accuracy":24,"mappable":true,"cached_votes_total":1,"last_indexed_at":"2020-05-29T07:38:08.897-07:00","private_place_guess":null,"uuid":"748a8cdc-8b90-4943-92b1-b98d17a4e9fd","taxon_geoprivacy":"open","votes":{"":{"up":1,"down":0}},"created_at_utc":"2020-05-29T02:47:39.923Z","updated_at_utc":"2020-05-29T14:38:07.235Z","time_observed_at_utc":"2020-05-28T17:36:09.000Z","faves_count":1,"owners_identification_from_vision":false}
//
        String testUrl = "/votes/vote/observation/47694022.json";
        String result = getAsset("favorite_observation.json");

        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo(testUrl))
                .willReturn(new ResponseDefinitionBuilder())
                .withHeader("Authorization", new AnythingPattern())
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(result)));
        return testUrl;
    }

    static String stubRemoveFavorite() {
//        DELETE /votes/unvote/observation/47693827.json HTTP/1.1
//        Authorization: Bearer ----
//        Host: www.inaturalist.org
//        Connection: Keep-Alive
//        User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.10.0+ 6352195; SDK 23; generic_x86 Android SDK built for x86 sdk_google_phone_x86; OS Version 6.0)

//        HTTP/1.1 204 No Content
//        Server: nginx
//        Date: Fri, 29 May 2020 14:38:13 GMT
//        Status: 204 No Content
//        Cache-Control: no-cache
//        X-XSS-Protection: 1; mode=block
//        X-Request-Id: c02631af-e31f-4e56-80a2-75216339b3f0
//        X-Runtime: 1.398131
//        X-Frame-Options: SAMEORIGIN
//        X-Content-Type-Options: nosniff
//        Age: 0
//        X-Cache: MISS
//        Connection: keep-alive
        String testUrl = "/votes/unvote/observation/47693827.json";

        WireMock.stubFor(WireMock.delete(WireMock.urlEqualTo(testUrl))
                .withHeader("Authorization", new AnythingPattern())
                .willReturn(WireMock.aResponse()
                        .withStatus(204)));
        return testUrl;
    }

//    POST /comments.json HTTP/1.1
//    Authorization: Bearer 3b4626dc2b3ce1e19e90b54846bf7f2823ffb48677686e0b3f2882ea9ee19c75
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
//
//
//
//    HTTP/1.1 200 OK
//    Server: nginx
//    Date: Fri, 29 May 2020 14:51:46 GMT
//    Content-Type: application/json; charset=utf-8
//    Content-Length: 1144
//    Status: 200 OK
//    Cache-Control: max-age=0, private, must-revalidate
//    X-XSS-Protection: 1; mode=block
//    X-Request-Id: 8d51f921-ddf3-4bf8-8713-55606f84b293
//    ETag: W/"3f1db39c7691c5d00c332ccefe046e05"
//    X-Frame-Options: SAMEORIGIN
//    X-Runtime: 3.408420
//    X-Content-Type-Options: nosniff
//    Age: 0
//    X-Cache: MISS
//    Accept-Ranges: bytes
//    Connection: keep-alive
//
//    {"id":4713818,"user_id":1255620,"parent_id":47575188,"parent_type":"Observation","body":"Test","created_at":"2020-05-29T10:51:42.725-04:00","updated_at":"2020-05-29T10:51:42.725-04:00","uuid":"2a2b11fb-c471-496b-8b7c-7f4e99fb03a1","html":"\u003cdiv id=\"comment-4713818\" class=\"comment clear\"\u003e\n    \u003cdiv class=\"meta\"\u003e\n      \u003ca href=\"/users/1255620\"\u003eYou\u003c/a\u003e said\n        \u003cspan class=\"when\"\u003e\n          \u003cspan class=\"date\"\u003eless than 1 minute\u003c/span\u003e ago,\n        \u003c/span\u003e\n        \u003cdiv class=\"comment_actions\"\u003e\n          (\u003ca href=\"/comments/4713818/edit\"\u003eEdit\u003c/a\u003e | \u003ca data-confirm=\"Are you sure you want to delete this comment?\" id=\"delete_comment_4713818_button\" rel=\"nofollow\" data-method=\"delete\" href=\"/comments/4713818\"\u003eDelete\u003c/a\u003e)\n        \u003c/div\u003e\n    \u003c/div\u003e\n  \u003cdiv class=\"readable body\"\u003e\n    \u003cp\u003eTest\u003c/p\u003e\n  \u003c/div\u003e\n\u003c/div\u003e\n","created_at_utc":"2020-05-29T14:51:42.725Z","updated_at_utc":"2020-05-29T14:51:42.725Z"}
//
//    {
//        "id": 4713818,
//            "user_id": 1255620,
//            "parent_id": 47575188,
//            "parent_type": "Observation",
//            "body": "Test",
//            "created_at": "2020-05-29T10:51:42.725-04:00",
//            "updated_at": "2020-05-29T10:51:42.725-04:00",
//            "uuid": "2a2b11fb-c471-496b-8b7c-7f4e99fb03a1",
//            "html": "\u003cdiv id=\"comment-4713818\" class=\"comment clear\"\u003e\n    \u003cdiv class=\"meta\"\u003e\n      \u003ca href=\"/users/1255620\"\u003eYou\u003c/a\u003e said\n        \u003cspan class=\"when\"\u003e\n          \u003cspan class=\"date\"\u003eless than 1 minute\u003c/span\u003e ago,\n        \u003c/span\u003e\n        \u003cdiv class=\"comment_actions\"\u003e\n          (\u003ca href=\"/comments/4713818/edit\"\u003eEdit\u003c/a\u003e | \u003ca data-confirm=\"Are you sure you want to delete this comment?\" id=\"delete_comment_4713818_button\" rel=\"nofollow\" data-method=\"delete\" href=\"/comments/4713818\"\u003eDelete\u003c/a\u003e)\n        \u003c/div\u003e\n    \u003c/div\u003e\n  \u003cdiv class=\"readable body\"\u003e\n    \u003cp\u003eTest\u003c/p\u003e\n  \u003c/div\u003e\n\u003c/div\u003e\n",
//            "created_at_utc": "2020-05-29T14:51:42.725Z",
//            "updated_at_utc": "2020-05-29T14:51:42.725Z"
//    }
//
//
//
//
//
//    DELETE /comments/4713818.json HTTP/1.1
//    Authorization: Bearer 3b4626dc2b3ce1e19e90b54846bf7f2823ffb48677686e0b3f2882ea9ee19c75
//    Host: www.inaturalist.org
//    Connection: Keep-Alive
//    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.10.0+ 6352195; SDK 23; generic_x86 Android SDK built for x86 sdk_google_phone_x86; OS Version 6.0)
//
//    HTTP/1.1 200 OK
//    Server: nginx
//    Date: Fri, 29 May 2020 14:52:34 GMT
//    Content-Type: application/json
//    Transfer-Encoding: chunked
//    Status: 200 OK
//    Cache-Control: no-cache
//    X-XSS-Protection: 1; mode=block
//    X-Request-Id: 3a87f758-a7da-4216-9f58-d5c57e0dd013
//    X-Runtime: 2.398660
//    X-Frame-Options: SAMEORIGIN
//    X-Content-Type-Options: nosniff
//    Age: 0
//    X-Cache: MISS
//    Accept-Ranges: bytes
//    Connection: keep-alive


}
