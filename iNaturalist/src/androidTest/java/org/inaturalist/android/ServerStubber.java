package org.inaturalist.android;

import androidx.test.platform.app.InstrumentationRegistry;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;

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

    static String stubAddComment() throws IOException {
        String testUrl = "/comments.json";

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
        String result = getAsset("add_comment.json");
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo(testUrl))
                .withHeader("Authorization", new AnythingPattern())
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withMultipartRequestBody(aMultipart().withName("comment[parent_id]"))
                .withMultipartRequestBody(aMultipart().withName("comment[parent_type]"))
                .withMultipartRequestBody(aMultipart().withName("comment[body]"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(result)
                ));

        return testUrl;
    }

    static String stubDeleteComment() {
        String testUrl = "/comments/4713818.json";

        //    DELETE /comments/4713818.json HTTP/1.1
        //    Authorization: Bearer ---
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
        WireMock.stubFor(WireMock.delete(WireMock.urlEqualTo(testUrl))
                .withHeader("Authorization", new AnythingPattern())
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)));

        return testUrl;
    }

    static String stubTaxonSuggestions() throws IOException {
        String testUrl = "/v1/computervision/score_image";

        //    POST /v1/computervision/score_image HTTP/1.1
        //    Authorization: ----
        //    Content-Length: 74412
        //    Content-Type: multipart/form-data; boundary=BZPZ0_--sJdYQj79dPf0WOE23gjWtE
        //    Host: api.inaturalist.org
        //    Connection: Keep-Alive
        //    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.10.0+ 6352195; SDK 23; generic_x86 Android SDK built for x86 sdk_google_phone_x86; OS Version 6.0)
        //
        //    --BZPZ0_--sJdYQj79dPf0WOE23gjWtE
        //    Content-Disposition: form-data; name="locale"
        //
        //    en
        //            --BZPZ0_--sJdYQj79dPf0WOE23gjWtE
        //    Content-Disposition: form-data; name="lat"
        //
        //    39.0455500388
        //            --BZPZ0_--sJdYQj79dPf0WOE23gjWtE
        //    Content-Disposition: form-data; name="lng"
        //
        //            -78.153687939
        //            --BZPZ0_--sJdYQj79dPf0WOE23gjWtE
        //    Content-Disposition: form-data; name="observed_on"
        //
        //    2020-05-28
        //            --BZPZ0_--sJdYQj79dPf0WOE23gjWtE
        //    Content-Disposition: form-data; name="image"; filename="7ce53ad2-144e-46ce-b49f-64c50c64dcc8.jpeg"
        //    Content-Type: application/octet-stream
        //    <snip file contents>
        //

        //    HTTP/1.1 200 OK
        //    Server: nginx
        //    Date: Fri, 29 May 2020 22:03:22 GMT
        //    Content-Type: application/json; charset=utf-8
        //    Content-Length: 192534
        //    Access-Control-Allow-Origin: *
        //    Access-Control-Allow-Headers: Origin, X-Requested-With, Content-Type, Accept, Authorization, Access-Control-Allow-Methods, X-Via
        //    Access-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE
        //    X-Content-Type-Options: nosniff
        //    ETag: W/"2f016-8hFCtIpUrLq8qp2AJQWFxCogn10"
        //    Vary: Accept-Encoding
        //    Age: 0
        //    X-Cache: MISS
        //    Accept-Ranges: bytes
        //    Connection: keep-alive
        String result = getAsset("taxon_suggestions.json");
        WireMock.stubFor(WireMock.post(WireMock.urlEqualTo(testUrl))
                .withHeader("Authorization", new AnythingPattern())
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withMultipartRequestBody(aMultipart().withName("locale"))
                .withMultipartRequestBody(aMultipart().withName("lat"))
                .withMultipartRequestBody(aMultipart().withName("lng"))
                .withMultipartRequestBody(aMultipart().withName("observed_on"))
                .withMultipartRequestBody(aMultipart().withName("image"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(result)
                ));

        return testUrl;
    }

    private void TODO_stubGetNearbyObservations() {
        //    GET https://api.inaturalist.org/v1/observations?locale=en&page=1&per_page=30&ordered_by=observation.id&order=desc&return_bounds=true&quality_grade=needs_id,research&swlng=-78.4078574180603&swlat=38.81434382003597&nelng=-77.91347231715918&nelat=39.29104506510731
        //    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.18.91+ 5455776; SDK 27; generic_x86 Android SDK built for x86 sdk_gphone_x86; OS Version 8.1.0)
        //    Authorization: -----
        //    Host: api.inaturalist.org
        //    Connection: Keep-Alive
        //    Accept-Encoding: gzip

        //    Server: nginx
        //    Date: Sun, 31 May 2020 19:41:09 GMT
        //    Content-Type: application/json; charset=utf-8
        //    Transfer-Encoding: chunked
        //    Connection: keep-alive
        //    Access-Control-Allow-Origin: *
        //    Access-Control-Allow-Headers: Origin, X-Requested-With, Content-Type, Accept, Authorization, Access-Control-Allow-Methods, X-Via
        //    Access-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE
        //    Cache-Control: public, max-age=300
        //    X-Content-Type-Options: nosniff
        //    ETag: W/"9942f-6L6j0bXR5t7naelpezyT5lHOGic"
        //    Vary: Accept-Encoding
        //    Content-Encoding: gzip
        //    Age: 0
        //    X-Cache: MISS
        //    Accept-Ranges: bytes

        // {"total_results":21977,"total_bounds":{"swlat":38.81474793422967,"swlng":-78.40783428400755,"nelat":39.290883326902986,"nelng":-77.91358287446201},"page":1,"per_page":30,"results":[{"quality_grade":"needs_id","time_observed_at":"2020-05-31T12:12:18-04:00","taxon_geoprivacy":null,"annotations":[],"uuid":"ea346ce0-9cf0-4684-97aa-d3a5e7f8986c","observed_on_details":{"date":"2020-05-31","week":22,"month":5,"hour":12,"year":2020,"day":31},"id":48027808,"cached_votes_total":0,"identifications_most_agree":false,"created_at_details":{"date":"2020-05-31","week":22,"month":5,"hour":15,"year":2020,"day":31},"species_guess":"Myodochini","identifications_most_disagree":false,"tags":[],"positional_accuracy":null,"comments_count":0,"site_id":1,"created_time_zone":"America/New_York","license_code":null,"observed_time_zone":"America/New_York","quality_metrics":[],"public_positional_accuracy":null,"reviewed_by":[374639],"oauth_application_id":2,"flags":[],"created_at":"2020-05-31T15:35:08-04:00","description":null,"time_zone_offset":"-05:00","project_ids_with_curator_id":[],"observed_on":"2020-05-31","observed_on_string":"2020-05-31 12:12:18 PM EDT","updated_at":"2020-05-31T15:35:20-04:00","sounds":[],"place_ids":[1,7,3033,9853,50853,51894,52250,59613,61551,66741,81418,81457,82256,82257,90834,97394,111970,118683,121076,124637,128746,135019,138222,147737],"captive":false,"taxon":{"is_active":true,"ancestry":"48460/1/47120/372739/47158/184884/47744/61267/372868/466233/141732/318457","min_species_ancestry":"48460,1,47120,372739,47158,184884,47744,61267,372868,466233,141732,318457,362729","endemic":false,"iconic_taxon_id":47158,"min_species_taxon_id":362729,"threatened":false,"rank_level":25,"introduced":false,"native":false,"parent_id":318457,"name":"Myodochini","rank":"tribe","extinct":false,"id":362729,"ancestor_ids":[48460,1,47120,372739,47158,184884,47744,61267,372868,466233,141732,318457,362729],"taxon_schemes_count":1,"wikipedia_url":"http://en.wikipedia.org/wiki/Myodochini","current_synonymous_taxon_ids":null,"created_at":"2014-07-24T08:48:59+00:00","taxon_changes_count":0,"complete_species_count":null,"universal_search_rank":4292,"observations_count":4292,"flag_counts":{"unresolved":0,"resolved":0},"atlas_id":null,"default_photo":{"square_url":"https://static.inaturalist.org/photos/7176702/square.jpg?1492397266","attribution":"(c) Judy Gallagher, some rights reserved (CC BY), uploaded by Judy Gallagher","flags":[],"medium_url":"https://static.inaturalist.org/photos/7176702/medium.jpg?1492397266","id":7176702,"license_code":"cc-by","original_dimensions":{"width":968,"height":1423},"url":"https://static.inaturalist.org/photos/7176702/square.jpg?1492397266"},"iconic_taxon_name":"Insecta"},"ident_taxon_ids":[48460,1,47120,372739,47158,184884,47744,61267,372868,466233,141732,318457,362729],"outlinks":[],"faves_count":0,"ofvs":[],"num_identification_agreements":0,"preferences":{"prefers_community_taxon":null},"comments":[],"map_scale":null,"uri":"https://www.inaturalist.org/observations/48027808","project_ids":[],"community_taxon_id":null,"geojson":{"coordinates":[-78.163554,38.8887789722],"type":"Point"},"owners_identification_from_vision":false,"identifications_count":0,"obscured":false,"num_identification_disagreements":0,"geoprivacy":null,"location":"38.8887789722,-78.163554","votes":[],"spam":false,"user":{"site_id":1,"created_at":"2016-11-25T19:54:05+00:00","id":374639,"login":"mjwcarr","spam":false,"suspended":false,"preferences":{},"login_autocomplete":"mjwcarr","login_exact":"mjwcarr","name":"Michael J. W. Carr","name_autocomplete":"Michael J. W. Carr","orcid":null,"icon":"https://static.inaturalist.org/attachments/users/icons/374639/thumb.jpg?1582507262","observations_count":4234,"identifications_count":2410,"journal_posts_count":1,"activity_count":6645,"universal_search_rank":4234,"roles":[],"icon_url":"https://static.inaturalist.org/attachments/users/icons/374639/medium.jpg?1582507262"},"mappable":true,"identifications_some_agree":false,"project_ids_without_curator_id":[],"place_gues

        }

        public static String stubDeletePinnedLocation() {
            //    DELETE https://www.inaturalist.org/saved_locations/93992.json (on Connection{www.inaturalist.org:443, proxy=DIRECT hostAddress=www.inaturalist.org/51.143.92.118:443 cipherSuite=TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 protocol=http/1.1})
            //    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.18.91+ 5455776; SDK 27; generic_x86 Android SDK built for x86 sdk_gphone_x86; OS Version 8.1.0)
            //    Authorization: Bearer -------
            //    Content-Length: 0
            //    Host: www.inaturalist.org
            //    Connection: Keep-Alive
            //    Accept-Encoding: gzip
            //
            //    Server: nginx
            //    Date: Sun, 31 May 2020 20:31:37 GMT
            //    Connection: keep-alive
            //    Status: 204 No Content
            //    Cache-Control: no-cache
            //    X-XSS-Protection: 1; mode=block
            //    X-Request-Id: fb3c6ae5-1373-43b1-9053-4b58414761ea
            //    X-Runtime: 0.020967
            //    X-Frame-Options: SAMEORIGIN
            //    X-Content-Type-Options: nosniff
            //    Age: 0
            //    X-Cache: MISS

            String testUrl = "/saved_locations/93992.json";
            WireMock.stubFor(WireMock.delete(WireMock.urlEqualTo(testUrl))
                    .withHeader("Authorization", new AnythingPattern())
                    .willReturn(WireMock.aResponse()
                            .withStatus(204)
                    ));
            return testUrl;
        }

        public static String stubError_savePinnedLocation() {

//            POST https://www.inaturalist.org/saved_locations.json (on Connection{www.inaturalist.org:443, proxy=DIRECT hostAddress=www.inaturalist.org/51.143.92.118:443 cipherSuite=TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 protocol=http/1.1})
//            User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.18.91+ 5455776; SDK 27; generic_x86 Android SDK built for x86 sdk_gphone_x86; OS Version 8.1.0)
//            Authorization: Bearer ------
//            Content-Type: multipart/form-data; boundary=217e9e58-9f41-4b3f-a5b4-ce186dd5d76f
//            Content-Length: 779
//            Host: www.inaturalist.org
//            Connection: Keep-Alive
//            Accept-Encoding: gzip

//            Server: nginx
//            Date: Sun, 31 May 2020 20:32:58 GMT
//            Content-Type: application/json; charset=utf-8
//            Content-Length: 36
//            Connection: keep-alive
//            Status: 422 Unprocessable Entity
//            Cache-Control: no-cache
//            X-XSS-Protection: 1; mode=block
//            X-Request-Id: 15990869-b680-4056-ab5b-7550a151d99b
//            X-Runtime: 0.015986
//            X-Frame-Options: SAMEORIGIN
//            X-Content-Type-Options: nosniff
//            Age: 0
//            X-Cache: MISS
//            2020-05-31 16:32:58.275 24429-24491/org.inaturalist.android D/iNaturalistApi:

            String testUrl = "/saved_locations.json";
            String result = "{\"title\":[\"has already been taken\"]}";
            WireMock.stubFor(WireMock.post(WireMock.urlEqualTo(testUrl))
                    .withHeader("Authorization", new AnythingPattern())
                    .withHeader("Content-Type", containing("multipart/form-data"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[latitude]"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[longitude]"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[positional_accuracy]"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[geoprivacy]"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[title]"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json; charset=utf-8")
                            .withBody(result)
                    ));

            return testUrl;
        }

        public static String pinLocation() throws IOException {
            //    POST https://www.inaturalist.org/saved_locations.json (on Connection{www.inaturalist.org:443, proxy=DIRECT hostAddress=www.inaturalist.org/51.143.92.118:443 cipherSuite=TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 protocol=http/1.1})
            //    User-Agent: iNaturalist/1.19.0 (Build 422; Android 3.18.91+ 5455776; SDK 27; generic_x86 Android SDK built for x86 sdk_gphone_x86; OS Version 8.1.0)
            //    Authorization: Bearer -------
            //    Content-Type: multipart/form-data; boundary=f3906ba5-6fa5-4add-88e6-04bfff76c461
            //    Content-Length: 761
            //    Host: www.inaturalist.org
            //    Connection: Keep-Alive
            //    Accept-Encoding: gzip
            //
            //    Server: nginx
            //    Date: Sun, 31 May 2020 20:34:56 GMT
            //    Content-Type: application/json; charset=utf-8
            //    Transfer-Encoding: chunked
            //    Connection: keep-alive
            //    Status: 200 OK
            //    Cache-Control: max-age=0, private, must-revalidate
            //    X-XSS-Protection: 1; mode=block
            //    X-Request-Id: 2244b7b7-ed58-4d66-9603-5c688ed60367
            //    ETag: W/"2e03f12ca343020d9c3e299d824e3eb0"
            //    X-Frame-Options: SAMEORIGIN
            //    X-Runtime: 0.033279
            //    X-Content-Type-Options: nosniff
            //    Content-Encoding: gzip
            //    Vary: Accept-Encoding
            //    Age: 0
            //    X-Cache: MISS
            //    Accept-Ranges: bytes
            //    2020-05-31 16:34:55.895 24429-24491/org.inaturalist.android D/iNaturalistApi:
            String testUrl = "/saved_locations.json";
            String result = getAsset("pinned_location.json");
            WireMock.stubFor(WireMock.post(WireMock.urlEqualTo(testUrl))
                    .withHeader("Authorization", new AnythingPattern())
                    .withHeader("Content-Type", containing("multipart/form-data"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[latitude]"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[longitude]"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[positional_accuracy]"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[geoprivacy]"))
                    .withMultipartRequestBody(aMultipart().withName("saved_location[title]"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json; charset=utf-8")
                            .withBody(result)
                    ));
            return testUrl;
        }
}
