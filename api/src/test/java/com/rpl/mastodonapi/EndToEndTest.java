package com.rpl.mastodonapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.rpl.mastodon.MastodonConfig;
import com.rpl.mastodon.MastodonHelpers;
import com.rpl.mastodon.data.StatusPointer;
import com.rpl.mastodon.modules.*;
import com.rpl.mastodon.serialization.MastodonSerialization;
import com.rpl.mastodonapi.pojos.*;
import com.rpl.rama.PState;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static com.rpl.mastodonapi.TestHelpers.assertNotFound;
import static com.rpl.mastodonapi.TestHelpers.assertUnauthorized;
import static com.rpl.mastodonapi.TestHelpers.attainCondition;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "end-to-end-test"})
public class EndToEndTest {
    @LocalServerPort
    int localServerPort;
    WebClient webClient;

    @Test
    void endToEndTest() throws IOException {
        // this test will only run when it is specifically selected like this:
        // mvn test -Dtest=EndToEndTest
        //
        // this is necessary because if you just run `mvn test`, the profiles
        // for the EndToEndTest and IntegrationTest will get mixed together.
        // this is bad because we don't want scheduled methods running here,
        // but we do want them to run in the integration test.
        if (!"EndToEndTest".equals(System.getProperty("test"))) {
            throw new RuntimeException("Can't run end to end test unless it is explicitly specified:\nmvn test -Dtest=EndToEndTest");
        }

        List<Class> sers = new ArrayList<>();
        sers.add(MastodonSerialization.class);
        try (InProcessCluster ipc = InProcessCluster.create(sers)) {
            Relationships relationshipsModule = new Relationships();
            ipc.launchModule(relationshipsModule, new LaunchConfig(2, 1));

            Core coreModule = new Core();
            coreModule.enableHomeTimelineRefresh = false;
            ipc.launchModule(coreModule, new LaunchConfig(2, 1));

            TrendsAndHashtags hashtagsModule = new TrendsAndHashtags();
            hashtagsModule.decayTickTimeMillis = 60000000;
            hashtagsModule.reviewTickTimeMillis = 60000000;
            ipc.launchModule(hashtagsModule, new LaunchConfig(2, 1));

            GlobalTimelines globalTimelinesModule = new GlobalTimelines();
            ipc.launchModule(globalTimelinesModule, new LaunchConfig(2, 1));

            Notifications notificationsModule = new Notifications();
            ipc.launchModule(notificationsModule, new LaunchConfig(2, 1));

            Search searchModule = new Search();
            ipc.launchModule(searchModule, new LaunchConfig(2, 1));

            MastodonApiController.manager = new MastodonApiManager(ipc);

            int coreCount = 0;
            int otherCount = 0;
            int hashtagsCount = 0;
            int globalTimelinesCount = 0;

            MastodonConfig.API_URL = "http://localhost:" + localServerPort;
            MastodonConfig.API_DOMAIN = "localhost";
            webClient = WebClient.create(MastodonConfig.API_URL);
            PState nameToUser = ipc.clusterPState(MastodonApiManager.CORE_MODULE_NAME, "$$nameToUser");

            // wait for tick depot in TrendsAndHashtags module
            ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);

            // alice creates account
            {
                // only ASCII characters are allowed in the username
                webClient.post().uri("/api/v1/accounts")
                        .bodyValue(new PostAccount("alice💩", "alice@foo.com", "hunter2", true, "en-US", ""))
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode());
                            return response.bodyToMono(GetToken.class);
                        })
                        .block();

                webClient.post().uri("/api/v1/accounts")
                        .bodyValue(new PostAccount("alice", "alice@foo.com", "hunter2", true, "en-US", ""))
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetToken.class);
                        })
                        .block();
            }

            // get account id
            GetAccount alice =
                    webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                    .queryParam("acct", "alice")
                                    .build())
                            .accept(MediaType.APPLICATION_JSON)
                            .exchangeToMono(response -> {
                                assertEquals(HttpStatus.OK, response.statusCode());
                                return response.bodyToMono(GetAccount.class);
                            })
                            .block();
            String aliceId = alice.id;

            final String aliceAuth;

            // alice logs in
            {
                // logging in with invalid username doesn't work
                webClient.post().uri("/oauth/token")
                         .bodyValue(new PostToken("non-existent-user", "hunter2"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                        .block();

                // logging in with an invalid password doesn't work
                webClient.post().uri("/oauth/token")
                         .bodyValue(new PostToken("alice", "hunter42"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                        .block();

                aliceAuth = getAuthToken("alice", "hunter2");

                // verify credentials
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/accounts/verify_credentials")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // verify credentials
                GetAccount account =
                        webClient.get().uri("/api/v1/accounts/verify_credentials")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetAccount.class);
                                 })
                                 .block();
                assertEquals(aliceId, account.id);
            }

            // get alice's account
            {
                // non-existent account returns 404
                webClient.get().uri("/api/v1/accounts/1234-a")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(GetAccount.class);
                         })
                         .block();

                GetAccount account =
                        webClient.get().uri("/api/v1/accounts/" + aliceId)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetAccount.class);
                                 })
                                 .block();
                assertEquals(0L, MastodonHelpers.parseAccountId(account.id));
            }

            // bob creates account
            {
                // an existing username returns an error
                GetErrorDetails errorDetails =
                        webClient.post().uri("/api/v1/accounts")
                                 .bodyValue(new PostAccount("alice", "bob@foo.com", "secret", true, "en-US", ""))
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode());
                                     return response.bodyToMono(GetErrorDetails.class);
                                 })
                                 .block();
                assertEquals("ERR_TAKEN", errorDetails.details.get("username").error);

                webClient.post().uri("/api/v1/accounts")
                         .bodyValue(new PostAccount("bob", "bob@foo.com", "secret", true, "en-US", ""))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetToken.class);
                         })
                         .block();
            }

            // get account id
            GetAccount bob =
                    webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                                                .queryParam("acct", "bob")
                                                                .build())
                             .accept(MediaType.APPLICATION_JSON)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetAccount.class);
                             })
                             .block();
            String bobId = bob.id;

            // bob logs in
            String bobAuth = getAuthToken("bob", "secret");

            // alice updates her account settings
            {
                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("display_name", "Alice😈");
                builder.part("locked", "true");
                builder.part("bot", "true");
                builder.part("discoverable", "true");
                builder.part("fields_attributes[0][name]", "country");
                builder.part("fields_attributes[0][value]", "england");
                GetAccount account =
                    webClient.patch().uri("/api/v1/accounts/update_credentials")
                             .accept(MediaType.APPLICATION_JSON)
                             .contentType(MediaType.MULTIPART_FORM_DATA)
                             .header("Authorization", aliceAuth)
                             .bodyValue(builder.build())
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetAccount.class);
                             })
                             .block();
                assertEquals("Alice😈", account.display_name);
                assertTrue(account.locked);
                assertTrue(account.bot);
                assertTrue(account.discoverable);
                assertEquals(1, account.fields.size());
                assertEquals("country", account.fields.get(0).name);
                assertEquals("england", account.fields.get(0).value);

                builder = new MultipartBodyBuilder();
                builder.part("locked", "false");
                builder.part("bot", "false");
                builder.part("discoverable", "false");
                builder.part("source[privacy]", "private");
                builder.part("source[sensitive]", "true");
                builder.part("source[language]", "es");
                account =
                    webClient.patch().uri("/api/v1/accounts/update_credentials")
                             .accept(MediaType.APPLICATION_JSON)
                             .contentType(MediaType.MULTIPART_FORM_DATA)
                             .header("Authorization", aliceAuth)
                             .bodyValue(builder.build())
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetAccount.class);
                             })
                             .block();
                assertFalse(account.locked);
                assertFalse(account.bot);
                assertFalse(account.discoverable);

                HashMap<String, JsonNode> prefs =
                        webClient.get().uri("/api/v1/preferences")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<HashMap<String, JsonNode>>(){});
                                 })
                                 .block();
                assertEquals("private", prefs.get("posting:default:visibility").textValue());
                assertTrue(prefs.get("posting:default:sensitive").booleanValue());
                assertEquals("es", prefs.get("posting:default:language").textValue());
            }

            // charlie creates account
            webClient.post().uri("/api/v1/accounts")
                     .bodyValue(new PostAccount("charlie", "charlie@foo.com", "opensesame", true, "en-US", ""))
                     .exchangeToMono(response -> {
                         assertEquals(HttpStatus.OK, response.statusCode());
                         return response.bodyToMono(GetToken.class);
                     })
                     .block();

            // get account id
            GetAccount charlie =
                    webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                                                .queryParam("acct", "charlie")
                                                                .build())
                             .accept(MediaType.APPLICATION_JSON)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetAccount.class);
                             })
                             .block();
            String charlieId = charlie.id;

            // charlie logs in via 3rd party oauth
            {
                // charlie logs in
                String charlieAuth = getAuthToken("charlie", "opensesame");

                // charlie logs out
                webClient.post().uri("/oauth/revoke")
                         .bodyValue(new PostRevokeToken(charlieAuth.split(" ")[1]))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // charlie registers an app
                GetApplication app =
                    webClient.post().uri("/api/v1/apps")
                             .bodyValue(new PostApplication())
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetApplication.class);
                             })
                             .block();

                // charlie gets the authorize page
                webClient.get().uri(uriBuilder -> uriBuilder.path("/oauth/authorize")
                                                            .queryParam("client_id", app.client_id)
                                                            .queryParam("redirect_uri", "http://localhost/login/external")
                                                            .queryParam("response_type", "code")
                                                            .queryParam("scope", "read write follow push")
                                                            .build())
                         .accept(MediaType.TEXT_HTML)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                AtomicReference<String> code = new AtomicReference<>();

                // charlie posts the authorize form
                MultiValueMap<String, String> formParams = new LinkedMultiValueMap<String, String>();
                formParams.add("username", "charlie");
                formParams.add("password", "opensesame");
                formParams.add("client_id", app.client_id);
                formParams.add("redirect_uri", "http://localhost/login/external");
                formParams.add("response_type", "code");
                formParams.add("scope", "read write follow push");
                webClient.post().uri("/oauth/authorize")
                         .body(BodyInserters.fromFormData(formParams))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.FOUND, response.statusCode());
                             code.set(response.headers().header("Location").get(0).split("code=")[1]);
                             return response.bodyToMono(String.class);
                         })
                         .block();
            }

            // charlie logs in
            String charlieAuth = getAuthToken("charlie", "opensesame");

            // alice posts a status
            {
                // alice connects to the public local stream
                TestStreamClient alicePublicLocalStream = new TestStreamClient("ws://localhost:" + localServerPort + "/api/v1/streaming/?stream=public:local", aliceAuth);

                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses")
                         .bodyValue(new PostStatus("Hello, world!", null, "public"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                String statusTooLong = "Hello, world!";
                for (int i = 0; i < MastodonApiConfig.MAX_STATUS_LENGTH; i++) {
                    statusTooLong += 'x';
                }
                webClient.post().uri("/api/v1/statuses")
                         .header("Authorization", aliceAuth)
                         .bodyValue(new PostStatus(statusTooLong, null, "public"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode());
                             return response.bodyToMono(GetStatus.class);
                         })
                         .block();

                GetStatus status =
                        webClient.post().uri("/api/v1/statuses")
                                 .header("Authorization", aliceAuth)
                                 .bodyValue(new PostStatus("Hello, world!", null, "public"))
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceId, status.account.id);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                MastodonApiSchedulingConfig.refreshGlobalTimelines(MastodonApiStreamingConfig.GLOBAL_TIMELINE_CACHE_SIZE, MastodonApiStreamingConfig.GLOBAL_TIMELINE_QUERY_LIMIT, true);

                // get message from stream
                GetStatus streamStatus = alicePublicLocalStream.waitForStatus();
                assertEquals(status.id, streamStatus.id);
                alicePublicLocalStream.close();
            }

            final String aliceStatusId1;

            // get alice's statuses
            {
                List<GetStatus> statuses =
                        webClient.get().uri("/api/v1/accounts/" + aliceId + "/statuses")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();

                assertEquals(1, statuses.size());
                aliceStatusId1 = statuses.get(0).id;

                // pagination
                statuses =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/" + aliceId + "/statuses")
                                                                    .queryParam("max_id", aliceStatusId1)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(0, statuses.size());
            }

            // get alice's status
            {
                // non-existent status returns 404
                webClient.get().uri("/api/v1/statuses/123-456-sa")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(GetStatus.class);
                         })
                         .block();

                GetStatus status =
                        webClient.get().uri("/api/v1/statuses/" + aliceStatusId1)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId1, status.id);
                assertEquals(aliceId, status.account.id);
            }

            // alice edits her status
            {
                GetStatusSource statusSource =
                        webClient.get().uri("/api/v1/statuses/" + aliceStatusId1 + "/source")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatusSource.class);
                                 })
                                 .block();
                assertEquals(statusSource.id, aliceStatusId1);
                assertEquals(statusSource.text, "Hello, world!");

                // without the auth header, it returns 401
                webClient.put().uri("/api/v1/statuses/" + aliceStatusId1)
                         .bodyValue(new PutStatus("Goodbye, world!"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // with the wrong auth header, it returns 404
                webClient.put().uri("/api/v1/statuses/" + aliceStatusId1)
                         .header("Authorization", bobAuth)
                         .bodyValue(new PutStatus("Goodbye, world!"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                webClient.put().uri("/api/v1/statuses/" + aliceStatusId1)
                         .header("Authorization", aliceAuth)
                         .bodyValue(new PutStatus("Goodbye, world!"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                GetStatus status =
                        webClient.get().uri("/api/v1/statuses/" + aliceStatusId1)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals("Goodbye, world!", status.content);

                // get the edit history of the status
                // non-existent status returns 404
                webClient.get().uri("/api/v1/statuses/123-456-sa/history")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(GetStatus.class);
                         })
                         .block();

                // get the edit history of the status
                List<GetStatusEdit> statusEdits =
                        webClient.get().uri("/api/v1/statuses/" + aliceStatusId1 + "/history")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatusEdit>>(){});
                                 })
                                 .block();
                assertEquals(2, statusEdits.size());
                assertEquals("Hello, world!", statusEdits.get(0).content);
                assertEquals("Goodbye, world!", statusEdits.get(1).content);
            }

            MastodonApiSchedulingConfig.refreshGlobalTimelines(MastodonApiStreamingConfig.GLOBAL_TIMELINE_CACHE_SIZE, MastodonApiStreamingConfig.GLOBAL_TIMELINE_QUERY_LIMIT, true);

            // alice deletes her status
            {
                // without the auth header, it returns 401
                webClient.delete().uri("/api/v1/statuses/" + aliceStatusId1)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // with the wrong auth header, it returns 404
                webClient.delete().uri("/api/v1/statuses/" + aliceStatusId1)
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                GetStatus deletedStatus =
                        webClient.delete().uri("/api/v1/statuses/" + aliceStatusId1)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId1, deletedStatus.id);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                List<GetStatus> statuses =
                        webClient.get().uri("/api/v1/accounts/" + aliceId + "/statuses")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();

                assertEquals(0, statuses.size());
            }

            // alice posts a status with a poll
            {
                PostStatus postStatus = new PostStatus("Favorite color", null, "public");
                postStatus.poll = new PostStatus.Poll();
                postStatus.poll.options = Arrays.asList("red", "blue");
                postStatus.poll.multiple = true;
                postStatus.poll.expires_in = 100000;

                GetStatus status =
                        webClient.post().uri("/api/v1/statuses")
                                 .header("Authorization", aliceAuth)
                                 .bodyValue(postStatus)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceId, status.account.id);
                assertNotNull(status.poll);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                GetPoll poll =
                        webClient.get().uri("/api/v1/polls/" + status.poll.id)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetPoll.class);
                                 })
                                 .block();
                assertEquals(poll.id, status.poll.id);

                // bob votes on the poll
                poll = webClient.post().uri("/api/v1/polls/" + status.poll.id + "/votes")
                                 .header("Authorization", bobAuth)
                                 .bodyValue(new PostPollVote(Arrays.asList("0", "1")))
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetPoll.class);
                                 })
                                 .block();
                assertEquals(1, poll.voters_count);
                assertEquals(2, poll.votes_count);
                assertEquals(new HashSet<>(Arrays.asList(0, 1)), poll.own_votes);
            }

            final String aliceStatusId2;
            final String aliceReplyStatusId;

            // alice posts a status and a reply, then gets the context
            {
                GetStatus status =
                    webClient.post().uri("/api/v1/statuses")
                             .header("Authorization", aliceAuth)
                             .bodyValue(new PostStatus("Hello, this is a normal status", null, "public"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();
                aliceStatusId2 = status.id;

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                // alice writes a reply
                status =
                    webClient.post().uri("/api/v1/statuses")
                             .header("Authorization", aliceAuth)
                             .bodyValue(new PostStatus("This is a reply", aliceStatusId2, "public"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();
                aliceReplyStatusId = status.id;

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                GetContext context =
                        webClient.get().uri("/api/v1/statuses/" + aliceStatusId2 + "/context")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetContext.class);
                                 })
                                 .block();

                assertEquals(0, context.ancestors.size());
                assertEquals(1, context.descendants.size());
                assertEquals(1, context.descendants.get(0).mentions.size());
                assertTrue(context.descendants.get(0).mentions.get(0).id.equals(aliceId));
            }

            // following
            {
                // alice follows bob
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/accounts/" + bobId + "/follow")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice follows bob
                GetRelationship relationship = postFollow(aliceAuth, bobId);
                assertTrue(relationship.following);

                // alice follows charlie
                relationship = postFollow(aliceAuth, charlieId);
                assertTrue(relationship.following);

                // charlie follows bob
                relationship = postFollow(charlieAuth, bobId);
                assertTrue(relationship.following);

                // get alice's followees
                AtomicReference<MultiValueMap<String, String>> nextParams = new AtomicReference<>();
                List<GetAccount> followees =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/" + aliceId + "/following")
                                                                    .queryParam("limit", 1)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     List<String> link = response.headers().header("Link");
                                     assertTrue(link.size() > 0);
                                     nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, followees.size());
                assertEquals(charlieId, followees.get(0).id);

                // pagination
                followees =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/" + aliceId + "/following")
                                                                    .queryParams(nextParams.get())
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, followees.size());
                assertEquals(bobId, followees.get(0).id);

                // get bob's followers
                List<GetAccount> followers =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/" + bobId + "/followers")
                                                                    .queryParam("limit", 1)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     List<String> link = response.headers().header("Link");
                                     assertTrue(link.size() > 0);
                                     nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, followers.size());
                assertEquals(charlieId, followers.get(0).id);

                // pagination
                followers =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/" + bobId + "/followers")
                                                                    .queryParams(nextParams.get())
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, followers.size());
                assertEquals(aliceId, followers.get(0).id);

                // charlie unfollows bob
                relationship = postUnfollow(charlieAuth, bobId);
                assertFalse(relationship.following);
            }

            // bob posts a status and alice sees it in her home timeline
            {
                List<GetStatus> aliceTimeline =
                        webClient.get().uri("/api/v1/timelines/home")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(3, aliceTimeline.size());

                // pagination
                final String aliceTimelineStatusId = aliceTimeline.get(0).id;
                aliceTimeline =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/timelines/home")
                                                                        .queryParam("max_id", aliceTimelineStatusId)
                                                                        .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(2, aliceTimeline.size());

                // alice connects to the user stream
                TestStreamClient aliceUserStream = new TestStreamClient("ws://localhost:" + localServerPort + "/api/v1/streaming/?stream=user", aliceAuth);

                webClient.post().uri("/api/v1/statuses")
                         .header("Authorization", bobAuth)
                         .bodyValue(new PostStatus("#hello, I'm Bob!", null, "public"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                webClient.post().uri("/api/v1/statuses")
                         .header("Authorization", bobAuth)
                         .bodyValue(new PostStatus("#hello, it's Bob again! https://redplanetlabs.com", null, "public"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                // get the status
                List<GetStatus> bobStatuses =
                        webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                GetStatus bobStatusHello2 = bobStatuses.get(0);
                GetStatus bobStatusHello = bobStatuses.get(1);

                // can also restrict statuses by hashtag
                List<GetStatus> taggedBobStatuses =
                        webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses?tagged=hello&limit=1")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>() {});
                                 }).block();
                assertEquals(1, taggedBobStatuses.size());
                assertEquals(bobStatusHello2.id, taggedBobStatuses.get(0).id);

                // pagination
                taggedBobStatuses =
                        webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses?tagged=hello&max_id=" + bobStatusHello2.id)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>() {});
                                 }).block();
                assertEquals(1, taggedBobStatuses.size());
                assertEquals(bobStatusHello.id, taggedBobStatuses.get(0).id);

                // get alice's home timeline
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/timelines/home")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's home timeline
                aliceTimeline =
                        webClient.get().uri("/api/v1/timelines/home")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(5, aliceTimeline.size());
                assertEquals(bobStatusHello2.id, aliceTimeline.get(0).id);

                MastodonApiSchedulingConfig.refreshHomeTimelineProxies();

                Set<String> streamStatusIds = new HashSet<>();
                GetStatus streamStatus = aliceUserStream.waitForStatus();
                streamStatusIds.add(streamStatus.id);
                streamStatus = aliceUserStream.waitForStatus();
                streamStatusIds.add(streamStatus.id);
                assertTrue(streamStatusIds.containsAll(Arrays.asList(bobStatusHello.id, bobStatusHello2.id)));
                aliceUserStream.close();
            }

            // alice writes a private note on bob's profile
            {
                GetRelationship relationship =
                    webClient.post().uri("/api/v1/accounts/" + bobId + "/note")
                             .header("Authorization", aliceAuth)
                             .bodyValue(new PostNote("this guy is cool"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetRelationship.class);
                             })
                             .block();
                assertEquals("this guy is cool", relationship.note);
            }

            // bob reports alice
            {
                GetReport report =
                    webClient.post().uri("/api/v1/reports")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostReport(aliceId, Arrays.asList(aliceStatusId1), "not cool!", "other"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetReport.class);
                             })
                             .block();
                assertEquals("not cool!", report.comment);
            }

            // following hashtags
            {
                // charlie is not following bob
                List<GetRelationship> relationships =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", bobId)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                assertEquals(1, relationships.size());
                assertFalse(relationships.get(0).following);

                // charlie follows a hashtag
                GetTag tag =
                    webClient.post().uri("/api/v1/tags/vanlife/follow")
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetTag.class);
                             })
                             .block();
                assertTrue(tag.following);

                // bob writes a status with the hashtag
                GetStatus bobStatus =
                    webClient.post().uri("/api/v1/statuses")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostStatus("#vanlife", null, "public"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                // charlie sees the status in his timeline
                List<GetStatus> charlieTimeline =
                        webClient.get().uri("/api/v1/timelines/home")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(1, charlieTimeline.size());
                assertEquals(bobStatus.id, charlieTimeline.get(0).id);

                // charlie unfollows a hashtag
                tag = webClient.post().uri("/api/v1/tags/vanlife/unfollow")
                               .header("Authorization", charlieAuth)
                               .exchangeToMono(response -> {
                                   assertEquals(HttpStatus.OK, response.statusCode());
                                   return response.bodyToMono(GetTag.class);
                               })
                               .block();
                assertFalse(tag.following);

                // get a single tag
                tag = webClient.get().uri("/api/v1/tags/vanlife")
                               .exchangeToMono(response -> {
                                   assertEquals(HttpStatus.OK, response.statusCode());
                                   return response.bodyToMono(GetTag.class);
                               }).block();
                assertEquals("vanlife", tag.name);
            }

            // suggested hashtags
            {
                // bob gets suggested tags
                List<GetTag> bobTags =
                        webClient.get().uri("/api/v1/featured_tags/suggestions")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetTag>>() {});
                                 }).block();
                assertEquals(2, bobTags.size());

                // alice gets no suggested tags
                List<GetTag> aliceTags =
                        webClient.get().uri("/api/v1/featured_tags/suggestions")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetTag>>() {});
                                 }).block();
                assertEquals(0, aliceTags.size());
            }

            // trends
            {
                // tags
                {
                    Set<String> allTags = new HashSet<>();

                    List<GetTag> tags =
                            webClient.get().uri("/api/v1/trends/tags?limit=1")
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetTag>>() {});
                                     }).block();
                    assertEquals(1, tags.size());
                    allTags.add(tags.get(0).name);

                    // pagination
                    tags = webClient.get().uri("/api/v1/trends/tags?offset=1")
                                    .exchangeToMono(response -> {
                                        assertEquals(HttpStatus.OK, response.statusCode());
                                        return response.bodyToMono(new ParameterizedTypeReference<List<GetTag>>() {});
                                    }).block();
                    assertEquals(1, tags.size());
                    allTags.add(tags.get(0).name);

                    // pagination
                    tags = webClient.get().uri("/api/v1/trends/tags?offset=2")
                                    .exchangeToMono(response -> {
                                        assertEquals(HttpStatus.OK, response.statusCode());
                                        return response.bodyToMono(new ParameterizedTypeReference<List<GetTag>>() {});
                                    }).block();
                    assertEquals(0, tags.size());

                    assertTrue(allTags.containsAll(Arrays.asList("hello", "vanlife")));
                }

                // links
                {
                    List<GetPreviewCard> links =
                            webClient.get().uri("/api/v1/trends/links?limit=1")
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetPreviewCard>>() {});
                                     }).block();
                    assertEquals(1, links.size());
                    assertEquals("https://redplanetlabs.com", links.get(0).url);

                    // pagination
                    links = webClient.get().uri("/api/v1/trends/links?offset=1")
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetPreviewCard>>() {});
                                     }).block();
                    assertEquals(0, links.size());
                }

                // statuses
                {
                    List<GetStatus> statuses =
                            webClient.get().uri("/api/v1/trends/statuses?limit=1")
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>() {});
                                     }).block();
                    assertEquals(1, statuses.size());

                    // pagination
                    statuses = webClient.get().uri("/api/v1/trends/statuses?offset=1")
                                    .exchangeToMono(response -> {
                                        assertEquals(HttpStatus.OK, response.statusCode());
                                        return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>() {});
                                    }).block();
                    assertEquals(0, statuses.size());
                }
            }

            // get the public and hashtag timelines
            {
                MastodonApiSchedulingConfig.refreshGlobalTimelines(MastodonApiStreamingConfig.GLOBAL_TIMELINE_CACHE_SIZE, MastodonApiStreamingConfig.GLOBAL_TIMELINE_QUERY_LIMIT, true);

                // public
                List<GetStatus> publicTimeline =
                        webClient.get().uri("/api/v1/timelines/public?limit=1")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(1, publicTimeline.size());

                // pagination
                publicTimeline =
                        webClient.get().uri("/api/v1/timelines/public?max_id=" + publicTimeline.get(0).id)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(6, publicTimeline.size());

                // hashtag
                List<GetStatus> hashtagTimeline =
                        webClient.get().uri("/api/v1/timelines/tag/hello?limit=1")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(1, hashtagTimeline.size());

                // pagination
                hashtagTimeline =
                        webClient.get().uri("/api/v1/timelines/tag/hello?max_id=" + hashtagTimeline.get(0).id)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(1, hashtagTimeline.size());

                // the public timeline removes old entries when the cache size is exceeded.
                // the following verifies that this works correctly.

                // reduce the cache size
                int cacheSize = 3;
                int queryLimit = 100;
                MastodonApiSchedulingConfig.refreshGlobalTimelines(cacheSize, queryLimit, true);

                // the public timeline returns fewer results
                publicTimeline =
                        webClient.get().uri("/api/v1/timelines/public")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(cacheSize, publicTimeline.size());

                // pagination
                publicTimeline =
                        webClient.get().uri("/api/v1/timelines/public?max_id=" + publicTimeline.get(0).id)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(cacheSize-1, publicTimeline.size());

                // return the cache size to its previous value
                MastodonApiSchedulingConfig.refreshGlobalTimelines(MastodonApiStreamingConfig.GLOBAL_TIMELINE_CACHE_SIZE, queryLimit, true);
            }

            // charlie gets follow suggestions
            {
                // without the auth header, it returns 401
                webClient.get().uri("/api/v2/suggestions")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                List<GetSuggestion> suggestions =
                        webClient.get().uri("/api/v2/suggestions")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetSuggestion>>(){});
                                 })
                                 .block();

                assertEquals(1, suggestions.size());

                // charlie removes a follow suggestion
                webClient.delete().uri("/api/v1/suggestions/" + suggestions.get(0).account.id)
                         .header("Authorization", charlieAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // the suggestion was successfully removed
                suggestions =
                        webClient.get().uri("/api/v2/suggestions")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetSuggestion>>(){});
                                 })
                                 .block();
                assertEquals(0, suggestions.size());
            }

            // charlie gets familiar followers
            {

                TestStreamClient aliceUserStream = new TestStreamClient("ws://localhost:" + localServerPort + "/api/v1/streaming/?stream=user", aliceAuth);

                // charlie follows alice
                GetRelationship relationship = postFollow(charlieAuth, aliceId);
                assertTrue(relationship.following);


                GetNotification streamNotification = aliceUserStream.waitForNotification();
                assertEquals("charlie", streamNotification.account.username);
                aliceUserStream.close();

                // without the auth header, it returns 401
                webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/familiar_followers")
                                                            .queryParam("id[]", aliceId, bobId)
                                                            .build())
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // the intersection of who charlie is following, and who bob is followed by, is alice
                List<GetFamiliarFollowers> familiarFollowers =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/familiar_followers")
                                                                    .queryParam("id[]", aliceId, bobId)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetFamiliarFollowers>>(){});
                                 })
                                 .block();
                assertEquals(2, familiarFollowers.size());
                assertEquals(0, familiarFollowers.get(0).accounts.size());
                assertEquals(1, familiarFollowers.get(1).accounts.size());
            }

            // charlie can follow bob, hiding reblogs and only english statuses
            {
                // follow bob
                PostFollow postFollowParams = new PostFollow();
                postFollowParams.reblogs = false;
                postFollowParams.languages = Arrays.asList("en");
                GetRelationship charlieBobRelationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/follow")
                                 .header("Authorization", charlieAuth)
                                 .bodyValue(postFollowParams)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertTrue(charlieBobRelationship.following);
                assertEquals(1, charlieBobRelationship.languages.size());
                assertEquals("en", charlieBobRelationship.languages.get(0));
                assertFalse(charlieBobRelationship.showing_reblogs);
            }

            // bob makes charlie unfollow him
            {
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/accounts/" + charlieId + "/remove_from_followers")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                GetRelationship relationship =
                        webClient.post().uri("/api/v1/accounts/" + charlieId + "/remove_from_followers")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertFalse(relationship.followed_by);
            }

            // get all of alice's relationships
            {
                // without the auth header, it returns 401
                webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                            .queryParam("id[]", bobId, charlieId)
                                                            .build())
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                List<GetRelationship> relationships =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", bobId, charlieId,
                                                                                // pass status id just to make sure it is ignored
                                                                                aliceStatusId2)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                assertEquals(2, relationships.size());
                assertEquals(bobId, relationships.get(0).id);
                assertEquals(charlieId, relationships.get(1).id);
            }

            // alice unfollows bob
            {
                // unfollow bob
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/accounts/" + bobId + "/unfollow")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // unfollow bob
                GetRelationship relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/unfollow")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertFalse(relationship.following);

                // get alice's followees
                List<GetAccount> followees =
                        webClient.get().uri("/api/v1/accounts/" + aliceId + "/following")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, followees.size());

                // get bob's followers
                List<GetAccount> followers =
                        webClient.get().uri("/api/v1/accounts/" + bobId + "/followers")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(0, followers.size());
            }

            // alice blocks and unblocks bob
            {
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/accounts/" + bobId + "/block")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice blocks bob
                GetRelationship relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/block")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertTrue(relationship.blocking);

                // get list of accounts alice is blocking
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/blocks")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get list of accounts alice is blocking
                AtomicReference<MultiValueMap<String, String>> nextParams = new AtomicReference<>();
                List<GetAccount> blockees =
                        webClient.get().uri("/api/v1/blocks?limit=1")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     List<String> link = response.headers().header("Link");
                                     assertTrue(link.size() > 0);
                                     nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, blockees.size());

                // pagination
                blockees =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/blocks")
                                                                    .queryParams(nextParams.get())
                                                                    .build())
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(0, blockees.size());

                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/accounts/" + bobId + "/unblock")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice unblocks bob
                relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/unblock")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertFalse(relationship.blocking);
            }

            // alice mutes and unmutes bob
            {

                // alice connects to the user stream
                TestStreamClient aliceUserStream = new TestStreamClient("ws://localhost:" + localServerPort + "/api/v1/streaming/?stream=user", aliceAuth);

                // bob follows alice
                GetRelationship relationship = postFollow(bobAuth, aliceId);
                assertTrue(relationship.following);

                // alice sees the follow notification
                attainCondition(() -> {
                    List<GetNotification> results =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                                                                        .queryParam("types[]", "follow")
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetNotification>>(){});
                                     })
                                     .block();
                    return 1 == results.stream().filter(o -> o.account.username.equals("bob")).count();
                });

                // get message from stream
                GetNotification streamNotification = aliceUserStream.waitForNotification();
                assertEquals("bob", streamNotification.account.username);
                aliceUserStream.close();

                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/accounts/" + bobId + "/mute")
                         .bodyValue(new PostMute())
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice mutes bob along with notifications
                relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/mute")
                                 .header("Authorization", aliceAuth)
                                 .bodyValue(new PostMute(true, null))
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertTrue(relationship.muting);

                // alice no longer sees the follow notification
                attainCondition(() -> {
                    List<GetNotification> results =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                                                                        .queryParam("types[]", "follow")
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetNotification>>(){});
                                     })
                                     .block();
                    return 0 == results.stream().filter(o -> o.account.username.equals("bob")).count();
                });

                // alice mutes bob, but not notifications
                relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/mute")
                                 .header("Authorization", aliceAuth)
                                 .bodyValue(new PostMute(false, null))
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertTrue(relationship.muting);

                // alice sees the follow notification
                attainCondition(() -> {
                    List<GetNotification> results =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                                                                        .queryParam("types[]", "follow")
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetNotification>>(){});
                                     })
                                     .block();
                    return 1 == results.stream().filter(o -> o.account.username.equals("bob")).count();
                });

                // get list of accounts alice is muting
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/mutes")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get list of accounts alice is muting
                AtomicReference<MultiValueMap<String, String>> nextParams = new AtomicReference<>();
                List<GetAccount> mutees =
                        webClient.get().uri("/api/v1/mutes?limit=1")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     List<String> link = response.headers().header("Link");
                                     assertTrue(link.size() > 0);
                                     nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, mutees.size());

                // pagination
                mutees =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/mutes")
                                                                    .queryParams(nextParams.get())
                                                                    .build())
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(0, mutees.size());

                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/accounts/" + bobId + "/unmute")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice unmutes bob
                relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/unmute")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertFalse(relationship.muting);

                // alice can mute bob specifying notifications and duration
                PostMute muteParams = new PostMute();
                muteParams.notifications = false;
                muteParams.duration = 1000L;
                relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/mute")
                                 .header("Authorization", aliceAuth)
                                 .bodyValue(muteParams)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertTrue(relationship.muting);
                relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/unmute")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertFalse(relationship.muting);

                // bob unfollows alice
                relationship = postUnfollow(bobAuth, aliceId);
                assertFalse(relationship.following);
            }

            // get the list timeline
            {
                // alice makes first list
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/lists")
                         .bodyValue(new PostList("my first awesome list", "list"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(GetRelationship.class);
                         })
                         .block();

                // alice makes first list
                GetList list1 =
                        webClient.post().uri("/api/v1/lists")
                                 .bodyValue(new PostList("my first awesome list", "list"))
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetList.class);
                                 })
                                 .block();
                assertEquals("my first awesome list", list1.title);

                // alice makes second list
                GetList list2 =
                        webClient.post().uri("/api/v1/lists")
                                 .bodyValue(new PostList("my second awesome list", "list"))
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetList.class);
                                 })
                                 .block();
                assertEquals("my second awesome list", list2.title);

                // alice updates the first list
                // without the auth header, it returns 401
                webClient.put().uri("/api/v1/lists/" + list1.id)
                         .bodyValue(new PutList("my first awesome list!", "list"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice updates the first list
                // with the wrong auth header, it returns 404
                webClient.put().uri("/api/v1/lists/" + list1.id)
                         .bodyValue(new PutList("my first awesome list!", "list"))
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice updates the first list
                list1 = webClient.put().uri("/api/v1/lists/" + list1.id)
                                 .bodyValue(new PutList("my first awesome list!", "list"))
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetList.class);
                                 })
                                 .block();
                assertEquals("my first awesome list!", list1.title);

                // alice updates the second list
                list2 = webClient.put().uri("/api/v1/lists/" + list2.id)
                                 .bodyValue(new PutList("my second awesome list!", "list"))
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetList.class);
                                 })
                                 .block();
                assertEquals("my second awesome list!", list2.title);

                // get alice's first list
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/lists/" + list1.id)
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's first list
                // with the wrong auth header, it returns 404
                webClient.get().uri("/api/v1/lists/" + list1.id)
                         .accept(MediaType.APPLICATION_JSON)
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's first list
                list1 = webClient.get().uri("/api/v1/lists/" + list1.id)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetList.class);
                                 })
                                 .block();
                assertEquals("my first awesome list!", list1.title);

                // get alice's lists
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/lists")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's lists
                List<GetList> aliceLists =
                        webClient.get().uri("/api/v1/lists")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetList>>(){});
                                 })
                                 .block();
                assertEquals(2, aliceLists.size());

                // alice adds members to the first list
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/lists/" + list1.id + "/accounts")
                         .bodyValue(new PostListMember(Arrays.asList(bobId, charlieId)))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice adds members to the first list
                // with the wrong auth header, it returns 404
                webClient.post().uri("/api/v1/lists/" + list1.id + "/accounts")
                         .bodyValue(new PostListMember(Arrays.asList(bobId, charlieId)))
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice adds members to the first list
                webClient.post().uri("/api/v1/lists/" + list1.id + "/accounts")
                         .bodyValue(new PostListMember(Arrays.asList(bobId, charlieId)))
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice adds members to the second list
                webClient.post().uri("/api/v1/lists/" + list2.id + "/accounts")
                         .bodyValue(new PostListMember(Arrays.asList(bobId, charlieId)))
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get the members of alice's first list
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/lists/" + list1.id + "/accounts")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get the members of alice's first list
                // with the wrong auth header, it returns 404
                webClient.get().uri("/api/v1/lists/" + list1.id + "/accounts")
                         .accept(MediaType.APPLICATION_JSON)
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get the members of alice's first list
                List<GetAccount> aliceList1Members =
                        webClient.get().uri("/api/v1/lists/" + list1.id + "/accounts")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(2, aliceList1Members.size());

                // write a post to the list
                {
                    // alice connects to the list stream
                    TestStreamClient aliceList1Stream = new TestStreamClient("ws://localhost:" + localServerPort + "/api/v1/streaming/?stream=list&list=" + list1.id, aliceAuth);

                    // bob writes a post
                    GetStatus bobStatus =
                        webClient.post().uri("/api/v1/statuses")
                                 .header("Authorization", bobAuth)
                                 .bodyValue(new PostStatus("Another post from bob", null, "public"))
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                    // get a list timeline
                    // without the auth header, it returns 401
                    webClient.get().uri("/api/v1/timelines/list/12345")
                             .accept(MediaType.APPLICATION_JSON)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();

                    // get a list timeline
                    // if list doesn't exist, it returns 404
                    webClient.get().uri("/api/v1/timelines/list/12345")
                             .accept(MediaType.APPLICATION_JSON)
                             .header("Authorization", aliceAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();

                    // get a list timeline
                    // with the wrong auth header, it returns 404
                    webClient.get().uri("/api/v1/timelines/list/" + list1.id)
                             .accept(MediaType.APPLICATION_JSON)
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();

                    // get a list timeline
                    List<GetStatus> list1Timeline =
                        webClient.get().uri("/api/v1/timelines/list/" + list1.id)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                    assertEquals(1, list1Timeline.size());
                    assertEquals(bobStatus.id, list1Timeline.get(0).id);

                    // get message from stream
                    GetStatus streamStatus = aliceList1Stream.waitForStatus();
                    assertEquals(bobStatus.id, streamStatus.id);
                    aliceList1Stream.close();
                }

                String list1MembersPath = "/api/v1/lists/" + list1.id + "/accounts";

                // alice removes members from the first list
                // without the auth header, it returns 401
                webClient.delete().uri(uriBuilder -> uriBuilder.path(list1MembersPath)
                                                               .queryParam("account_ids[]", bobId)
                                                               .build())
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice removes members from the first list
                // with the wrong auth header, it returns 404
                webClient.delete().uri(uriBuilder -> uriBuilder.path(list1MembersPath)
                                                               .queryParam("account_ids[]", bobId)
                                                               .build())
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice removes members from the first list
                webClient.delete().uri(uriBuilder -> uriBuilder.path(list1MembersPath)
                                                               .queryParam("account_ids[]", bobId)
                                                               .build())
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's lists that contain bob
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/accounts/" + bobId + "/lists")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's lists that contain bob
                List<GetList> aliceListsWithBob =
                        webClient.get().uri("/api/v1/accounts/" + bobId + "/lists")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetList>>(){});
                                 })
                                 .block();
                assertEquals(1, aliceListsWithBob.size());

                // alice deletes the second list
                // without the auth header, it returns 401
                webClient.delete().uri("/api/v1/lists/" + list2.id)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice deletes the second list
                // with the wrong auth header, it returns 404
                webClient.delete().uri("/api/v1/lists/" + list2.id)
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice deletes the second list
                webClient.delete().uri("/api/v1/lists/" + list2.id)
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();
            }

            // conversations
            {
                // alice connects to the direct stream
                TestStreamClient aliceDirectStream = new TestStreamClient("ws://localhost:" + localServerPort + "/api/v1/streaming/?stream=direct", aliceAuth);

                // bob writes a direct message to alice
                webClient.post().uri("/api/v1/statuses")
                         .header("Authorization", bobAuth)
                         .bodyValue(new PostStatus("hey @alice how are you?", null, "direct"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetStatus.class);
                         })
                         .block();

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                // get alice's direct timeline
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/timelines/direct")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's direct timeline
                List<GetStatus> aliceDirectTimeline =
                        webClient.get().uri("/api/v1/timelines/direct")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(1, aliceDirectTimeline.size());

                // get alice's conversations
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/conversations")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's conversations
                List<GetConversation> aliceConvos =
                    webClient.get().uri("/api/v1/conversations")
                             .accept(MediaType.APPLICATION_JSON)
                             .header("Authorization", aliceAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(new ParameterizedTypeReference<List<GetConversation>>(){});
                             })
                             .block();
                assertEquals(1, aliceConvos.size());
                GetConversation aliceConvo = aliceConvos.get(0);
                assertTrue(aliceConvo.unread);
                assertNotNull(aliceConvo.last_status.content);
                assertEquals(1, aliceConvo.last_status.mentions.size());
                assertEquals(aliceId, aliceConvo.last_status.mentions.get(0).id);

                // alice marks the conversation as read
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/conversations/" + aliceConvo.id + "/read")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice marks the conversation as read
                aliceConvo =
                        webClient.post().uri("/api/v1/conversations/" + aliceConvo.id + "/read")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetConversation.class);
                                 })
                                 .block();
                assertFalse(aliceConvo.unread);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);

                // alice removes the conversation
                // without the auth header, it returns 401
                webClient.delete().uri("/api/v1/conversations/" + aliceConvos.get(0).id)
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice removes the conversation
                webClient.delete().uri("/api/v1/conversations/" + aliceConvos.get(0).id)
                         .accept(MediaType.APPLICATION_JSON)
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);

                // get alice's conversations
                aliceConvos =
                    webClient.get().uri("/api/v1/conversations")
                             .accept(MediaType.APPLICATION_JSON)
                             .header("Authorization", aliceAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(new ParameterizedTypeReference<List<GetConversation>>(){});
                             })
                             .block();
                assertEquals(0, aliceConvos.size());

                // get message from stream
                GetConversation streamConvo = aliceDirectStream.waitForConversation();
                assertEquals(aliceConvo.id, streamConvo.id);
                aliceDirectStream.close();
            }

            // featuring accounts
            {
                // alice features bob's account
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/accounts/" + bobId + "/pin")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice features bob's account
                GetRelationship relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/pin")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertTrue(relationship.endorsed);

                // get alice's endorsements
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/endorsements")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's endorsements
                AtomicReference<MultiValueMap<String, String>> nextParams = new AtomicReference<>();
                List<GetAccount> endorsements =
                    webClient.get().uri("/api/v1/endorsements?limit=1")
                             .accept(MediaType.APPLICATION_JSON)
                             .header("Authorization", aliceAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 List<String> link = response.headers().header("Link");
                                 assertTrue(link.size() > 0);
                                 nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                 return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                             })
                             .block();
                assertEquals(1, endorsements.size());

                // pagination
                endorsements =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/endorsements")
                                                                    .queryParams(nextParams.get())
                                                                    .build())
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(0, endorsements.size());

                // alice unfeatures bob's account
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/accounts/" + bobId + "/unpin")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice unfeatures bob's account
                relationship =
                        webClient.post().uri("/api/v1/accounts/" + bobId + "/unpin")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                                 .block();
                assertFalse(relationship.endorsed);
            }

            // featuring hashtags
            {
                // alice features a hashtag
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/featured_tags")
                         .bodyValue(new PostFeatureHashtag("foo"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice features a hashtag
                GetFeatureHashtag featureHashtag =
                        webClient.post().uri("/api/v1/featured_tags")
                                 .bodyValue(new PostFeatureHashtag("foo"))
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetFeatureHashtag.class);
                                 })
                                 .block();
                assertEquals("foo", featureHashtag.name);

                // get alice's featured hashtags
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/featured_tags")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's featured hashtags
                List<GetFeatureHashtag> aliceFeatureHashtags =
                        webClient.get().uri("/api/v1/featured_tags")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetFeatureHashtag>>(){});
                                 })
                                 .block();
                assertEquals(1, aliceFeatureHashtags.size());

                featureHashtag = aliceFeatureHashtags.get(0);

                // alice unfeatures a hashtag
                // without the auth header, it returns 401
                webClient.delete().uri("/api/v1/featured_tags/" + featureHashtag.id)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice unfeatures a hashtag
                webClient.delete().uri("/api/v1/featured_tags/" + featureHashtag.id)
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get alice's featured hashtags again
                // this endpoint is public and requires passing the account id in the URL
                aliceFeatureHashtags =
                        webClient.get().uri("/api/v1/accounts/" + aliceId + "/featured_tags")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetFeatureHashtag>>(){});
                                 })
                                 .block();
                assertEquals(0, aliceFeatureHashtags.size());
            }

            // favorite and unfavorite a status
            {
                // bob favorites alice's status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/favourite")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // bob favorites alice's status
                GetStatus status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/favourite")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertTrue(status.favourited);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);

                // charlie favorites alice's status
                status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/favourite")
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertTrue(status.favourited);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);

                // get accounts that favorited the status
                AtomicReference<MultiValueMap<String, String>> nextParams = new AtomicReference<>();
                List<GetAccount> favoriters =
                        webClient.get().uri("/api/v1/statuses/" + aliceStatusId2 + "/favourited_by?limit=1")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     List<String> link = response.headers().header("Link");
                                     assertTrue(link.size() > 0);
                                     nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, favoriters.size());

                // pagination
                favoriters =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/statuses/" + aliceStatusId2 + "/favourited_by")
                                                                    .queryParams(nextParams.get())
                                                                    .build())
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, favoriters.size());

                // get list of statuses that bob favorited
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/favourites")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get list of statuses that bob favorited
                List<GetStatus> favorites =
                        webClient.get().uri("/api/v1/favourites")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(1, favorites.size());
                assertEquals(aliceStatusId2, favorites.get(0).id);

                // bob unfavorites alice's status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unfavourite")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // bob unfavorites alice's status
                status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unfavourite")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertFalse(status.favourited);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
            }

            // boost and unboost a status
            {
                // bob boosts alice's status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/reblog")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // bob boosts alice's status
                GetStatus status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/reblog")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertTrue(status.reblogged);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                // charlie boosts alice's status
                status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/reblog")
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertTrue(status.reblogged);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                // get accounts that boosted the status
                AtomicReference<MultiValueMap<String, String>> nextParams = new AtomicReference<>();
                List<GetAccount> boosters =
                        webClient.get().uri("/api/v1/statuses/" + aliceStatusId2 + "/reblogged_by?limit=1")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     List<String> link = response.headers().header("Link");
                                     assertTrue(link.size() > 0);
                                     nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, boosters.size());

                // pagination
                boosters =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/statuses/" + aliceStatusId2 + "/reblogged_by")
                                                                    .queryParams(nextParams.get())
                                                                    .build())
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                 })
                                 .block();
                assertEquals(1, boosters.size());

                // bob unboosts alice's status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unreblog")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // bob unboosts alice's status
                status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unreblog")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertFalse(status.reblogged);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                // charlie boosts alice's reply status
                status =
                        webClient.post().uri("/api/v1/statuses/" + aliceReplyStatusId + "/reblog")
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceReplyStatusId, status.id);
                assertTrue(status.reblogged);
                assertTrue(status.mentions.stream().anyMatch(m -> m.username.equals("alice")));

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                // charlie unboosts alice's reply status
                status =
                        webClient.post().uri("/api/v1/statuses/" + aliceReplyStatusId + "/unreblog")
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceReplyStatusId, status.id);
                assertFalse(status.reblogged);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);
            }

            // bookmark and unbookmark a status
            {
                // bob bookmarks alice's status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/bookmark")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // bob bookmarks alice's status
                GetStatus status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/bookmark")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertTrue(status.bookmarked);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 1);

                // get list of statuses that bob bookmarked
                // without the auth header, it returns 401
                webClient.get().uri("/api/v1/bookmarks")
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get list of statuses that bob bookmarked
                List<GetStatus> bookmarks =
                        webClient.get().uri("/api/v1/bookmarks")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(1, bookmarks.size());
                assertEquals(aliceStatusId2, bookmarks.get(0).id);

                // bob unbookmarks alice's status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unbookmark")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // bob unbookmarks alice's status
                status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unbookmark")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertFalse(status.bookmarked);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 1);
            }

            // mute and unmute a status
            {
                // bob mutes alice's status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/mute")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // bob mutes alice's status
                GetStatus status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/mute")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertTrue(status.muted);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 1);

                // bob unmutes alice's status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unmute")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // bob unmutes alice's status
                status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unmute")
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertFalse(status.muted);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 1);
            }

            // pin and unpin a status
            {
                // bob pins alice's status
                // not allowed because you can only pin your own status
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/pin")
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice pins her status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/pin")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice pins her status
                GetStatus status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/pin")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertTrue(status.pinned);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 1);

                // query all of alice's pinned statuses
                List<GetStatus> pinnedStatuses =
                        webClient.get().uri("/api/v1/accounts/" + aliceId + "/statuses?pinned=true")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(1, pinnedStatuses.size());
                assertEquals(aliceStatusId2, pinnedStatuses.get(0).id);

                // the pinned status is in alice's timeline
                List<GetStatus> aliceTimeline =
                        webClient.get().uri("/api/v1/accounts/" + aliceId + "/statuses?pinned=true")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(1, aliceTimeline.size());

                // the pinned status is not returned from the account timeline
                // without the "pinned" param (avoiding duplication)
                aliceTimeline =
                        webClient.get().uri("/api/v1/accounts/" + aliceId + "/statuses")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(0, aliceTimeline.stream().filter(o -> o.id.equals(aliceStatusId2)).count());

                // alice unpins her status
                // without the auth header, it returns 401
                webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unpin")
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice unpins her status
                status =
                        webClient.post().uri("/api/v1/statuses/" + aliceStatusId2 + "/unpin")
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                assertEquals(aliceStatusId2, status.id);
                assertFalse(status.pinned);

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 1);
            }

            // search
            {
                // bobby creates account
                webClient.post().uri("/api/v1/accounts")
                         .bodyValue(new PostAccount("bobby", "bobby@foo.com", "opensesame", true, "en-US", ""))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetToken.class);
                         })
                         .block();

                // get account id
                GetAccount bobby =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                        .queryParam("acct", "bobby")
                                        .build())
                                .accept(MediaType.APPLICATION_JSON)
                                .exchangeToMono(response -> {
                                    assertEquals(HttpStatus.OK, response.statusCode());
                                    return response.bodyToMono(GetAccount.class);
                                })
                                .block();
                String bobbyId = bobby.id;

                // bobby logs in
                String bobbyAuth = getAuthToken("bobby", "opensesame");

                // bobby sets his display name to bob
                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("display_name", "bob");
                webClient.patch().uri("/api/v1/accounts/update_credentials")
                         .accept(MediaType.APPLICATION_JSON)
                         .contentType(MediaType.MULTIPART_FORM_DATA)
                         .header("Authorization", bobbyAuth)
                         .bodyValue(builder.build())
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetAccount.class);
                         })
                         .block();

                // alice searches for bob
                // without the auth header, it returns 401
                webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v2/search")
                                                            .queryParam("q", "bob")
                                                            .build())
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice searches for an account
                AtomicReference<MultiValueMap<String, String>> nextParams = new AtomicReference<>();
                attainCondition(() -> {
                    GetSearch search =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v2/search")
                                                                        .queryParam("q", "bob")
                                                                        .queryParam("limit", 1)
                                                                        .queryParam("type", "accounts")
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         List<String> link = response.headers().header("Link");
                                         if (link.size() > 0) nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                         return response.bodyToMono(GetSearch.class);
                                     })
                                     .block();
                    return 1 == search.accounts.size() && nextParams.get() != null;
                });

                // pagination
                attainCondition(() -> {
                    GetSearch search =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v2/search")
                                                                        .queryParams(nextParams.get())
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetSearch.class);
                                     })
                                     .block();
                    return 1 == search.accounts.size();
                });

                // alice searches for statuses and hashtags
                attainCondition(() -> {
                    GetSearch search =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v2/search")
                                                                        .queryParam("q", "hello")
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetSearch.class);
                                     })
                                     .block();
                    return 1 == search.statuses.size() && 1 == search.hashtags.size();
                });

                // alice searches for bob via dedicated account search endpoint
                // without the auth header, it returns 401
                webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/search")
                                                            .queryParam("q", "bob")
                                                            .build())
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice searches for bob via dedicated account search endpoint
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/search")
                                                                        .queryParam("q", "bob")
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 2 == accounts.size();
                });

                // alice follows bobby
                GetRelationship relationship = postFollow(aliceAuth, bobbyId);
                assertTrue(relationship.following);

                // alice searches for bob (followees-only) via dedicated account search endpoint
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/search")
                                                                        .queryParam("q", "bob")
                                                                        .queryParam("following", true)
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 1 == accounts.size();
                });
            }

            // scheduled statuses
            {
                // bob schedules a status
                GetScheduledStatus initialStatus =
                    webClient.post().uri("/api/v1/statuses")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostStatus("Hey, I'm bob", null, "public", Instant.now().plusMillis(60000).toString()))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetScheduledStatus.class);
                             })
                             .block();

                // the status isn't published yet
                assertNotFound(webClient.get()
                                        .uri("/api/v1/statuses/" + initialStatus.id)
                                        .header("Authorization", bobAuth));

                // listing scheduled statuses fails without auth header
                assertUnauthorized(webClient.get().uri("/api/v1/scheduled_statuses"));

                // schedule a few more to test pagination
                for (int i = 0; i < 4; i++) {
                    webClient.post().uri("/api/v1/statuses")
                    .header("Authorization", bobAuth)
                    .bodyValue(new PostStatus("status number" + 5, null, "public", Instant.now().plusMillis(60000 + 1000 * i).toString()))
                    .exchangeToMono(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                        return response.bodyToMono(GetScheduledStatus.class);
                    })
                    .block();
                }

                // can list scheduled statuses
                assertUnauthorized(webClient.get().uri("/api/v1/scheduled_statuses"));

                AtomicReference<MultiValueMap<String, String>> nextParams = new AtomicReference<>();
                List<GetScheduledStatus> scheduledStatuses =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/scheduled_statuses")
                                                                    .queryParam("limit", 3)
                                                                    .build())
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     List<String> link = response.headers().header("Link");
                                     assertTrue(link.size() > 0);
                                     nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetScheduledStatus>>(){});
                                 }).block();
                assertEquals(3, scheduledStatuses.size());

                // pagination
                scheduledStatuses =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/scheduled_statuses")
                                                                    .queryParams(nextParams.get())
                                                                    .build())
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetScheduledStatus>>(){});
                                 }).block();
                assertEquals(2, scheduledStatuses.size());

                // can fetch a single status
                assertUnauthorized(webClient.get().uri("/api/v1/scheduled_statuses/" + initialStatus.id));

                // fetching a status with a non-matching account id 401s
                String nonMatchingId = MastodonHelpers.serializeStatusPointer(new StatusPointer(MastodonHelpers.parseAccountId(aliceId), 0));
                assertUnauthorized(webClient.get()
                                            .uri("/api/v1/scheduled_statuses/" + nonMatchingId)
                                            .header("Authorization", bobAuth));

                // fetching a nonexistent status 404s
                String fakeId = MastodonHelpers.serializeStatusPointer(new StatusPointer(MastodonHelpers.parseAccountId(bobId), 9999));
                assertNotFound(webClient.get()
                                        .uri("/api/v1/scheduled_statuses/" + fakeId)
                                        .header("Authorization", bobAuth));

                GetScheduledStatus status = webClient.get()
                        .uri("/api/v1/scheduled_statuses/" + initialStatus.id)
                        .header("Authorization", bobAuth)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetScheduledStatus.class);
                        }).block();
                assertEquals("Hey, I'm bob", status.params.text);
                assertEquals("public", status.params.visibility);

                // can change a scheduled status's publish time
                Instant newTime = Instant.now().plusMillis(30000);
                assertUnauthorized(webClient.put()
                        .uri("/api/v1/scheduled_statuses/" + initialStatus.id)
                        .bodyValue(new PutScheduledStatus(newTime.toString())));

                GetScheduledStatus updatedStatus = webClient.put()
                        .uri("/api/v1/scheduled_statuses/" + initialStatus.id)
                        .header("Authorization", bobAuth)
                        .bodyValue(new PutScheduledStatus(newTime.toString()))
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetScheduledStatus.class);
                        })
                        .block();
                assertEquals(newTime.toString(), updatedStatus.scheduled_at);

                // can cancel a scheduled status
                assertUnauthorized(webClient.delete()
                                            .uri("/api/v1/scheduled_statuses/" + initialStatus.id));

                webClient.delete()
                         .uri("/api/v1/scheduled_statuses/" + initialStatus.id)
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         }).block();

                assertNotFound(webClient.get()
                                        .uri("/api/v1/scheduled_statuses/" + initialStatus.id)
                                        .header("Authorization", bobAuth));

                assertNotFound(webClient.get()
                                        .uri("/api/v1/scheduled_statuses/" + initialStatus.id)
                                        .header("Authorization", bobAuth));
            }

            // notifications
            {
                // alice has no markers yet
                Map<String, GetMarker> markers =
                    webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/markers")
                                                                .queryParam("timeline[]", "notifications")
                                                                .build())
                             .accept(MediaType.APPLICATION_JSON)
                             .header("Authorization", aliceAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(new ParameterizedTypeReference<Map<String, GetMarker>>(){});
                             })
                             .block();
                assertEquals(0, markers.size());

                // alice gets her first favorite notification
                AtomicReference<MultiValueMap<String, String>> nextParams = new AtomicReference<>();
                attainCondition(() -> {
                    List<GetNotification> results =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                                                                        .queryParam("types[]", "favourite")
                                                                        .queryParam("limit", 1)
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         List<String> link = response.headers().header("Link");
                                         assertTrue(link.size() > 0);
                                         nextParams.set(UriComponentsBuilder.fromUriString(link.get(0).split("[<>]")[1]).build().getQueryParams());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetNotification>>(){});
                                     })
                                     .block();
                    return results.size() == 1;
                });

                String nextNotificationId = nextParams.get().get("max_id").get(0);

                // pagination
                attainCondition(() -> {
                    List<GetNotification> results =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                                                                        .queryParam("types[]", "favourite")
                                                                        .queryParams(nextParams.get())
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetNotification>>(){});
                                     })
                                     .block();
                    return results.size() == 1;
                });

                // alice gets a single notification
                GetNotification result =
                        webClient.get().uri("/api/v1/notifications/" + nextNotificationId)
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetNotification.class);
                                 })
                                 .block();
                assertEquals(nextNotificationId, result.id);

                // alice posts a marker
                PostMarkers postMarkers = new PostMarkers();
                postMarkers.notifications = new PostMarkers.Marker();
                postMarkers.notifications.last_read_id = nextNotificationId;
                webClient.post().uri("/api/v1/markers")
                         .accept(MediaType.APPLICATION_JSON)
                         .header("Authorization", aliceAuth)
                         .bodyValue(postMarkers)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice has a marker
                markers =
                    webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/markers")
                                                                .queryParam("timeline[]", "notifications")
                                                                .build())
                             .accept(MediaType.APPLICATION_JSON)
                             .header("Authorization", aliceAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(new ParameterizedTypeReference<Map<String, GetMarker>>(){});
                             })
                             .block();
                assertEquals(1, markers.size());
                assertEquals(nextNotificationId, markers.get("notifications").last_read_id);

                // alice dismisses one notification
                webClient.post().uri("/api/v1/notifications/" + nextNotificationId + "/dismiss")
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice gets her remaining favorite notifications
                attainCondition(() -> {
                    List<GetNotification> results =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                                                                        .queryParam("types[]", "favourite")
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetNotification>>(){});
                                     })
                                     .block();
                    return results.size() == 1;
                });

                // alice dismisses all notifications
                webClient.post().uri("/api/v1/notifications/clear")
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // alice gets her notifications
                attainCondition(() -> {
                    List<GetNotification> results =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                                                                        .queryParam("types[]",
                                                                                    "follow", "follow_request",
                                                                                    "mention", "reblog", "favourite",
                                                                                    "poll", "status", "move",
                                                                                    "user_approved", "update")
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetNotification>>(){});
                                     })
                                     .block();
                    return results.size() == 0;
                });
            }

            // remote accounts
            {
                // alice searches for a remote account
                // technically it's actually just a local account, not remote,
                // but it will behave as if it is from another server.
                GetSearch urlSearch1 =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v2/search")
                                                                    .queryParam("q", "http://localhost:" + localServerPort + "/users/bob")
                                                                    .queryParam("resolve", true)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetSearch.class);
                                 })
                                 .block();
                assertEquals(1, urlSearch1.accounts.size());
                assertEquals("bob@localhost", urlSearch1.accounts.get(0).acct);

                // can't log in to remote account
                webClient.post().uri("/oauth/token")
                         .bodyValue(new PostToken("bob@localhost", ""))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                // get bob's remote account
                GetAccount bobRemoteAccount =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                                                    .queryParam("acct", "bob@localhost")
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetAccount.class);
                                 })
                                 .block();

                final GetAccount aliceRemoteAccount;

                // following
                {
                    // alice follows bob's remote account
                    GetRelationship relationship = postFollow(aliceAuth, bobRemoteAccount.id);
                    assertTrue(relationship.following);

                    // alice is following bob's remote account
                    List<GetRelationship> relationships =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                        .queryParam("id[]", bobRemoteAccount.id)
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                     })
                                     .block();
                    assertEquals(1, relationships.size());
                    assertTrue(relationships.get(0).following);

                    // get alice's remote account
                    aliceRemoteAccount =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                                                    .queryParam("acct", "alice@localhost")
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetAccount.class);
                                 })
                                 .block();

                    // bob is followed by alice's remote account
                    relationships =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                        .queryParam("id[]", aliceRemoteAccount.id)
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", bobAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                     })
                                     .block();
                    assertEquals(1, relationships.size());
                    assertTrue(relationships.get(0).followed_by);

                    // bob follows alice's remote account
                    relationship = postFollow(bobAuth, aliceRemoteAccount.id);
                    assertTrue(relationship.following);
                }

                // updating profile
                {
                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    builder.part("display_name", "Bob");
                    builder.part("fields_attributes[0][name]", "country");
                    builder.part("fields_attributes[0][value]", "usa");
                    GetAccount aliceAccount =
                        webClient.patch().uri("/api/v1/accounts/update_credentials")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .contentType(MediaType.MULTIPART_FORM_DATA)
                                 .header("Authorization", bobAuth)
                                 .bodyValue(builder.build())
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetAccount.class);
                                 })
                                 .block();
                    assertEquals("Bob", aliceAccount.display_name);
                    assertEquals(1, aliceAccount.fields.size());
                    assertEquals("country", aliceAccount.fields.get(0).name);
                    assertEquals("usa", aliceAccount.fields.get(0).value);

                    // get bob's remote account and make sure it was updated
                    attainCondition(() -> {
                        GetAccount bobAccount =
                                webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                                .queryParam("acct", "bob@localhost")
                                                .build())
                                        .accept(MediaType.APPLICATION_JSON)
                                        .exchangeToMono(response -> {
                                            assertEquals(HttpStatus.OK, response.statusCode());
                                            return response.bodyToMono(GetAccount.class);
                                        })
                                        .block();
                        return "Bob".equals(bobAccount.display_name);
                    });
                }

                GetStatus bobRemoteStatus;
                GetStatus bobLocalStatus;

                // posting a status
                {
                    // bob posts a status
                    webClient.post().uri("/api/v1/statuses")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostStatus("Hey, I am bob", null, "public"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                    // get the remote status
                    List<GetStatus> bobRemoteStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobRemoteStatus = bobRemoteStatuses.get(0);
                    String bobRemoteStatusId1 = bobRemoteStatus.id;

                    // get the local status
                    List<GetStatus> bobLocalStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobLocalStatus = bobLocalStatuses.get(0);
                    assertTrue(bobLocalStatus.content.contains("I am bob"));
                    String bobLocalStatusId1 = bobLocalStatus.id;

                    // get alice's home timeline
                    List<GetStatus> aliceTimeline =
                            webClient.get().uri("/api/v1/timelines/home")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    assertEquals(7, aliceTimeline.size());
                    assertEquals(bobRemoteStatus.id, aliceTimeline.get(0).id);

                    // bob writes a reply
                    webClient.post().uri("/api/v1/statuses")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostStatus("I said I am bob!", bobLocalStatusId1, "public"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                    // get the local status
                    bobLocalStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobLocalStatus = bobLocalStatuses.get(0);
                    assertTrue(bobLocalStatus.content.contains("I said I am bob"));
                    assertEquals(bobLocalStatusId1, bobLocalStatus.in_reply_to_id);

                    // get the remote status
                    bobRemoteStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobRemoteStatus = bobRemoteStatuses.get(0);
                    assertTrue(bobRemoteStatus.content.contains("I said I am bob"));
                    assertEquals(bobRemoteStatusId1, bobRemoteStatus.in_reply_to_id);
                }

                // editing a status
                {
                    webClient.put().uri("/api/v1/statuses/" + bobLocalStatus.id)
                             .header("Authorization", bobAuth)
                             .bodyValue(new PutStatus("I said I am not bob!"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                    // get the local status
                    List<GetStatus> bobLocalStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobLocalStatus = bobLocalStatuses.get(0);
                    assertTrue(bobLocalStatus.content.contains("I said I am not bob"));

                    // get the remote status
                    List<GetStatus> bobRemoteStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobRemoteStatus = bobRemoteStatuses.get(0);
                    assertTrue(bobRemoteStatus.content.contains("I said I am not bob"));
                }

                // favoriting and unfavoriting
                {
                    // alice favorites bob's status
                    GetStatus favStatus =
                            webClient.post().uri("/api/v1/statuses/" + bobRemoteStatus.id + "/favourite")
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetStatus.class);
                                     })
                                     .block();

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);

                    // the status on bob's end is favorited
                    List<GetStatus> bobLocalStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobLocalStatus = bobLocalStatuses.get(0);
                    assertEquals(1, bobLocalStatus.favourites_count);

                    // alice unfavorites bob's status
                    favStatus =
                            webClient.post().uri("/api/v1/statuses/" + bobRemoteStatus.id + "/unfavourite")
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetStatus.class);
                                     })
                                     .block();

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "core", otherCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);

                    // the status on bob's end is not favorited
                    bobLocalStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobLocalStatus = bobLocalStatuses.get(0);
                    assertEquals(0, bobLocalStatus.favourites_count);
                }

                // boosting and unboosting
                {
                    // alice boosts bob's status
                    GetStatus boostedStatus =
                            webClient.post().uri("/api/v1/statuses/" + bobRemoteStatus.id + "/reblog")
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetStatus.class);
                                     })
                                     .block();
                    assertEquals(bobRemoteStatus.id, boostedStatus.id);

                    final String boostedStatusId = boostedStatus.id;

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                    // the status on bob's end is boosted
                    List<GetStatus> bobLocalStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>() {
                                         });
                                     })
                                     .block();
                    bobLocalStatus = bobLocalStatuses.get(0);
                    assertEquals(1, bobLocalStatus.reblogs_count);

                    // the boost appears in alice's account timeline
                    List<GetStatus> aliceTimeline =
                            webClient.get().uri("/api/v1/accounts/" + aliceId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    Optional<GetStatus> boostMaybe = aliceTimeline.stream().filter(o -> o.reblog != null && o.reblog.id.equals(boostedStatusId)).findFirst();
                    assertTrue(boostMaybe.isPresent());

                    // ...unless boosts are excluded
                    aliceTimeline =
                            webClient.get().uri("/api/v1/accounts/" + aliceId + "/statuses?exclude_reblogs=true")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    boostMaybe = aliceTimeline.stream().filter(o -> o.reblog != null && o.reblog.id.equals(boostedStatusId)).findFirst();
                    assertFalse(boostMaybe.isPresent());

                    // alice unboosts bob's status
                    boostedStatus =
                            webClient.post().uri("/api/v1/statuses/" + bobRemoteStatus.id + "/unreblog")
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetStatus.class);
                                     })
                                     .block();
                    assertEquals(bobRemoteStatus.id, boostedStatus.id);

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                    // the status on bob's end is not boosted
                    bobLocalStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>() {
                                         });
                                     })
                                     .block();
                    bobLocalStatus = bobLocalStatuses.get(0);
                    assertEquals(0, bobLocalStatus.reblogs_count);
                }

                // polls
                {
                    PostStatus postStatus = new PostStatus("Favorite animal", null, "public");
                    postStatus.poll = new PostStatus.Poll();
                    postStatus.poll.options = Arrays.asList("dog", "cat");
                    postStatus.poll.multiple = false;
                    postStatus.poll.expires_in = 100000;

                    // bob posts a poll
                    bobLocalStatus =
                            webClient.post().uri("/api/v1/statuses")
                                     .header("Authorization", bobAuth)
                                     .bodyValue(postStatus)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetStatus.class);
                                     })
                                     .block();
                    assertTrue(bobLocalStatus.content.contains("Favorite animal"));

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                    // get the remote status
                    List<GetStatus> bobRemoteStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobRemoteStatus = bobRemoteStatuses.get(0);
                    assertTrue(bobRemoteStatus.content.contains("Favorite animal"));

                    // alice votes on the poll
                    webClient.post().uri("/api/v1/polls/" + bobRemoteStatus.poll.id + "/votes")
                             .header("Authorization", aliceAuth)
                             .bodyValue(new PostPollVote(Arrays.asList("1")))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetPoll.class);
                             })
                             .block();

                    // alice's vote was counted in the remote poll (alice's server)
                    GetPoll bobRemotePoll =
                            webClient.get().uri("/api/v1/polls/" + bobRemoteStatus.poll.id)
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetPoll.class);
                                     })
                                     .block();
                    assertEquals(1, bobRemotePoll.voters_count);
                    assertEquals(1, bobRemotePoll.votes_count);
                    assertEquals(2, bobRemotePoll.options.size());

                    // alice's vote was counted in the local poll (bob's server)
                    GetPoll bobLocalPoll =
                            webClient.get().uri("/api/v1/polls/" + bobLocalStatus.poll.id)
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetPoll.class);
                                     })
                                     .block();
                    assertEquals(1, bobLocalPoll.voters_count);
                    assertEquals(1, bobLocalPoll.votes_count);
                    assertEquals(2, bobLocalPoll.options.size());
                }

                // editing a poll
                {
                    PutStatus putStatus = new PutStatus("What is your favorite animal?");
                    putStatus.poll = new PostStatus.Poll();
                    putStatus.poll.options = Arrays.asList("dog", "cat", "reptile", "bird");
                    putStatus.poll.multiple = false;
                    putStatus.poll.expires_in = 100000;

                    webClient.put().uri("/api/v1/statuses/" + bobLocalStatus.id)
                             .header("Authorization", bobAuth)
                             .bodyValue(putStatus)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                    // get the local status
                    List<GetStatus> bobLocalStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobLocalStatus = bobLocalStatuses.get(0);
                    assertTrue(bobLocalStatus.content.contains("What is your favorite animal"));

                    // get the remote status
                    List<GetStatus> bobRemoteStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobRemoteStatus = bobRemoteStatuses.get(0);
                    assertTrue(bobLocalStatus.content.contains("What is your favorite animal"));

                    // the remote poll was updated
                    GetPoll bobRemotePoll =
                            webClient.get().uri("/api/v1/polls/" + bobRemoteStatus.poll.id)
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetPoll.class);
                                     })
                                     .block();
                    assertEquals(4, bobRemotePoll.options.size());

                    // the local poll was updated
                    GetPoll bobLocalPoll =
                            webClient.get().uri("/api/v1/polls/" + bobLocalStatus.poll.id)
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetPoll.class);
                                     })
                                     .block();
                    assertEquals(4, bobLocalPoll.options.size());
                }

                // deleting
                {
                    // bob deletes his status
                    GetStatus deletedStatus =
                            webClient.delete().uri("/api/v1/statuses/" + bobLocalStatus.id)
                                     .header("Authorization", bobAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetStatus.class);
                                     })
                                     .block();
                    assertEquals(bobLocalStatus.id, deletedStatus.id);

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                    MastodonApiSchedulingConfig.refreshGlobalTimelines(MastodonApiStreamingConfig.GLOBAL_TIMELINE_CACHE_SIZE, MastodonApiStreamingConfig.GLOBAL_TIMELINE_QUERY_LIMIT, true);

                    // get public timeline
                    // bob's status was deleted by the remote server
                    List<GetStatus> publicTimeline =
                            webClient.get().uri("/api/v1/timelines/public")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    assertNotEquals(bobRemoteStatus.id, publicTimeline.get(0).id);
                }

                // unfollowing
                {
                    // alice unfollows bob's remote account
                    GetRelationship relationship =
                            webClient.post().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/unfollow")
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetRelationship.class);
                                     })
                                     .block();
                    assertFalse(relationship.following);

                    // bob is not followed by alice's remote account
                    List<GetRelationship> relationships =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                        .queryParam("id[]", aliceRemoteAccount.id)
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", bobAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                     })
                                     .block();
                    assertEquals(1, relationships.size());
                    assertFalse(relationships.get(0).followed_by);
                }

                // posting a status with a remote mention
                {
                    // bob posts a status mentioning alice
                    webClient.post().uri("/api/v1/statuses")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostStatus("@alice@localhost hey there alice!", null, "public"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                    // get the local status
                    List<GetStatus> bobLocalStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobId + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobLocalStatus = bobLocalStatuses.get(0);
                    assertTrue(bobLocalStatus.content.contains("hey there alice"));

                    // get the remote status
                    // (even though alice isn't following him anymore, it still
                    // fanned out to her server because she was mentioned)
                    List<GetStatus> bobRemoteStatuses =
                            webClient.get().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/statuses")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                     })
                                     .block();
                    bobRemoteStatus = bobRemoteStatuses.get(0);
                    assertTrue(bobRemoteStatus.content.contains("hey there alice"));
                }

                // blocking and unblocking
                {
                    // alice blocks bob's remote account
                    GetRelationship relationship =
                            webClient.post().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/block")
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetRelationship.class);
                                     })
                                     .block();
                    assertTrue(relationship.blocking);

                    // bob is blocked by alice's remote account
                    List<GetRelationship> relationships =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                        .queryParam("id[]", aliceRemoteAccount.id)
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", bobAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                     })
                                     .block();
                    assertEquals(1, relationships.size());
                    assertTrue(relationships.get(0).blocked_by);

                    // alice unblocks bob's remote account
                    relationship =
                            webClient.post().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/unblock")
                                     .header("Authorization", aliceAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(GetRelationship.class);
                                     })
                                     .block();
                    assertFalse(relationship.blocking);

                    // bob is not blocked by alice's remote account
                    relationships =
                            webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                        .queryParam("id[]", aliceRemoteAccount.id)
                                                                        .build())
                                     .accept(MediaType.APPLICATION_JSON)
                                     .header("Authorization", bobAuth)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                     })
                                     .block();
                    assertEquals(1, relationships.size());
                    assertFalse(relationships.get(0).blocked_by);
                }

                // alice writes a direct message to bob
                webClient.post().uri("/api/v1/statuses")
                         .header("Authorization", aliceAuth)
                         .bodyValue(new PostStatus("@bob@localhost This is private!", null, "direct"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                // get bob's direct timeline
                List<GetStatus> bobDirectTimeline =
                        webClient.get().uri("/api/v1/timelines/direct")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(2, bobDirectTimeline.size());

                assertTrue(bobDirectTimeline.get(0).content.contains("This is private!"));

                // charlie doesn't have a remote account, because nobody has searched for him
                webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                                            .queryParam("acct", "charlie@localhost")
                                                            .build())
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(GetAccount.class);
                         })
                         .block();

                // bob writes a post mentioning charlie
                webClient.post().uri("/api/v1/statuses")
                         .header("Authorization", bobAuth)
                         .bodyValue(new PostStatus("@charlie@localhost what's up charlie!", null, "public"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 2);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 2);
                ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 2);

                // get charlie's remote account
                GetAccount charlieRemoteAccount =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                                                    .queryParam("acct", "charlie@localhost")
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetAccount.class);
                                 })
                                 .block();
            }

            // alice requests local/remote public timelines
            {
                MastodonApiSchedulingConfig.refreshGlobalTimelines(MastodonApiStreamingConfig.GLOBAL_TIMELINE_CACHE_SIZE, MastodonApiStreamingConfig.GLOBAL_TIMELINE_QUERY_LIMIT, true);

                // public
                List<GetStatus> publicTimeline =
                        webClient.get().uri("/api/v1/timelines/public")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(15, publicTimeline.size());

                // public (local only)
                publicTimeline =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/timelines/public")
                                                                    .queryParam("local", "true")
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(11, publicTimeline.size());

                // public (remote only)
                publicTimeline =
                        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/timelines/public")
                                                                    .queryParam("remote", "true")
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                assertEquals(4, publicTimeline.size());
            }

            // directory
            {
                // alice becomes discoverable
                // alice@localhost will also become discoverable because bob is still following her remote account
                {
                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    builder.part("discoverable", "true");
                    webClient.patch().uri("/api/v1/accounts/update_credentials")
                             .accept(MediaType.APPLICATION_JSON)
                             .contentType(MediaType.MULTIPART_FORM_DATA)
                             .header("Authorization", aliceAuth)
                             .bodyValue(builder.build())
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetAccount.class);
                             })
                             .block();
                }

                // bob becomes discoverable
                {
                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    builder.part("discoverable", "true");
                    webClient.patch().uri("/api/v1/accounts/update_credentials")
                             .accept(MediaType.APPLICATION_JSON)
                             .contentType(MediaType.MULTIPART_FORM_DATA)
                             .header("Authorization", bobAuth)
                             .bodyValue(builder.build())
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetAccount.class);
                             })
                             .block();
                }

                // all new
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?limit=1&order=new")
                                    .accept(MediaType.APPLICATION_JSON)
                                    .exchangeToMono(response -> {
                                        assertEquals(HttpStatus.OK, response.statusCode());
                                        return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>() {
                                        });
                                    })
                                    .block();
                    return 1 == accounts.size();
                });

                // pagination
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?offset=1&order=new")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 2 == accounts.size();
                });

                // pagination
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?offset=1000&order=new")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 0 == accounts.size();
                });

                // all active
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?limit=1&order=active")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 1 == accounts.size();
                });

                // pagination
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?offset=1&order=active")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 2 == accounts.size();
                });

                // local new
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?limit=1&order=new&local=true")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 1 == accounts.size();
                });

                // pagination
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?offset=1&order=new&local=true")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 1 == accounts.size();
                });

                // local active
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?limit=1&order=active&local=true")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 1 == accounts.size();
                });

                // pagination
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?offset=1&order=active&local=true")
                                     .accept(MediaType.APPLICATION_JSON)
                                     .exchangeToMono(response -> {
                                         assertEquals(HttpStatus.OK, response.statusCode());
                                         return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                                     })
                                     .block();
                    return 1 == accounts.size();
                });

                // bob becomes non-discoverable
                {
                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    builder.part("discoverable", "false");
                    webClient.patch().uri("/api/v1/accounts/update_credentials")
                             .accept(MediaType.APPLICATION_JSON)
                             .contentType(MediaType.MULTIPART_FORM_DATA)
                             .header("Authorization", bobAuth)
                             .bodyValue(builder.build())
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetAccount.class);
                             })
                             .block();
                }

                // now bob is not in the directory
                attainCondition(() -> {
                    List<GetAccount> accounts =
                            webClient.get().uri("/api/v1/directory?order=new")
                                    .accept(MediaType.APPLICATION_JSON)
                                    .exchangeToMono(response -> {
                                        assertEquals(HttpStatus.OK, response.statusCode());
                                        return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>() {
                                        });
                                    })
                                    .block();
                    return !accounts.stream().map(a -> a.username).collect(Collectors.toSet()).contains("bob");
                });
            }

            // follow requests and locked accounts
            {
                // Setup - alice unfollows bob, bob goes private
                GetRelationship aliceBobRelationship = postUnfollow(aliceAuth, bobId);
                assertFalse(aliceBobRelationship.following);
                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("locked", "true");
                String lockedAccount =
                    webClient.patch()
                             .uri("/api/v1/accounts/update_credentials")
                             .header("Authorization", bobAuth)
                             .bodyValue(builder.build())
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();

                for (String action : ImmutableList.of("/authorize", "/reject")) {
                    // accepting or rejecting a nonexistent follow request 404s
                    String errBody = webClient.post()
                        .uri("/api/v1/follow_requests/" + aliceId + action)
                        .header("Authorization", bobAuth)
                        .exchangeToMono(response -> {
                                assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                                return response.bodyToMono(String.class);
                            })
                        .block();

                    // accepting or rejecting without an auth token 401s
                    errBody = webClient.post()
                        .uri("/api/v1/follow_requests/" + aliceId + action)
                        .exchangeToMono(response -> {
                                assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
                                return response.bodyToMono(String.class);
                            })
                        .block();
                }

                // alice follows bob, resulting in a follow request
                aliceBobRelationship = postFollow(aliceAuth, bobId);
                assertFalse(aliceBobRelationship.following);
                assertTrue(aliceBobRelationship.requested);

                // bob can list follow requests
                List<GetAccount> bobRequests = getFollowRequests(bobAuth);
                assertEquals(1, bobRequests.size());
                assertEquals("" + aliceId, bobRequests.get(0).id);

                // bob can reject the request
                GetRelationship relationshipAfterReject = webClient.post()
                    .uri("/api/v1/follow_requests/" + aliceId + "/reject")
                    .header("Authorization", bobAuth)
                    .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetRelationship.class);
                        })
                    .block();
                assertFalse(relationshipAfterReject.followed_by);
                bobRequests = getFollowRequests(bobAuth);
                assertEquals(0, bobRequests.size());

                // alice follows again and bob can accept the follow request
                aliceBobRelationship = postFollow(aliceAuth, bobId);
                GetRelationship relationshipAfterAccept = webClient.post()
                    .uri("/api/v1/follow_requests/" + aliceId + "/authorize")
                    .header("Authorization", bobAuth)
                    .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetRelationship.class);
                        })
                    .block();

                assertTrue(relationshipAfterAccept.followed_by);
                bobRequests = getFollowRequests(bobAuth);
                assertEquals(0, bobRequests.size());
            }

            // Filters
            {
                PostFilterParams filterParams = new PostFilterParams();
                filterParams.title = "My Filter";
                filterParams.filter_action = "warn";
                filterParams.context = ImmutableList.of("home");
                PostFilterParams.KeywordAttributes keyword1 = new PostFilterParams.KeywordAttributes("word to mute", true, null, null);
                PostFilterParams.KeywordAttributes keyword2 = new PostFilterParams.KeywordAttributes("other word to mute", true, null, null);
                PostFilterParams.KeywordAttributes keyword3 = new PostFilterParams.KeywordAttributes("bob", true, null, null);
                filterParams.keywords_attributes = ImmutableList.of(keyword1, keyword2, keyword3);
                GetFilter filterResult = webClient.post()
                        .uri("/api/v2/filters")
                        .header("Authorization", aliceAuth)
                        .bodyValue(filterParams)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetFilter.class);
                        }).block();
                assertEquals(filterResult.title, "My Filter");

                List<GetFilter> filterResults = webClient.get()
                        .uri("/api/v2/filters")
                        .header("Authorization", aliceAuth)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(new ParameterizedTypeReference<List<GetFilter>>(){});
                        }).block();
                assertEquals(filterResult.id, filterResults.get(0).id);

                assertUnauthorized(webClient.get()
                        .uri("/api/v2/filters"));
                assertUnauthorized(webClient.get()
                        .uri("/api/v2/filters/" + filterResult.id));
                assertNotFound(webClient.get()
                        .uri("/api/v2/filters/9999")
                        .header("Authorization", aliceAuth));
                // Since filters are stored under their owners' account ids,
                // attempting to fetch someone else's filter will result in a 404.
                assertNotFound(webClient.get()
                        .uri("/api/v2/filters/" + filterResult.id)
                        .header("Authorization", bobAuth));

                GetFilter singleFilterResult = webClient.get()
                        .uri("/api/v2/filters/" + filterResult.id)
                        .header("Authorization", aliceAuth)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetFilter.class);
                        }).block();
                assertEquals(singleFilterResult.title, filterResult.title);

                // filtered statuses are marked with a warning in alice's timeline
                List<GetStatus> aliceTimeline =
                        webClient.get().uri("/api/v1/timelines/home")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                List<GetStatus> filteredStatuses = aliceTimeline.stream().filter(status -> status.filtered.size() > 0).collect(Collectors.toList());
                assertEquals(4, filteredStatuses.size());

                // streamed statuses are filtered correctly
                {
                    // alice follows bob
                    GetRelationship relationship = postFollow(aliceAuth, bobId);
                    assertTrue(relationship.following);

                    // alice connects to the user stream
                    TestStreamClient aliceUserStream = new TestStreamClient("ws://localhost:" + localServerPort + "/api/v1/streaming/?stream=user", aliceAuth);

                    // bob posts a status with "bob", a filtered word
                    webClient.post().uri("/api/v1/statuses")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostStatus("bob", null, "public"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.CORE_MODULE_NAME, "fanout", coreCount += 1);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.HASHTAGS_MODULE_NAME, "core", hashtagsCount += 1);
                    ipc.waitForMicrobatchProcessedCount(MastodonApiManager.GLOBAL_TIMELINES_MODULE_NAME, "globalTimelines", globalTimelinesCount += 1);

                    // alice receives the post via the user stream, but it is marked as filtered
                    MastodonApiSchedulingConfig.refreshHomeTimelineProxies();
                    GetStatus streamStatus = aliceUserStream.waitForStatus();
                    assertEquals("bob", streamStatus.content);
                    assertEquals(1, streamStatus.filtered.size());

                    // alice unfollows bob
                    relationship = postUnfollow(aliceAuth, bobId);
                    assertFalse(relationship.following);

                    aliceUserStream.close();
                }

                // editing filters
                PostFilterParams editFilterParams = new PostFilterParams();
                editFilterParams.title = "new title";
                editFilterParams.filter_action = "hide"; // change from warn to hide
                PostFilterParams.KeywordAttributes newKeyword = new PostFilterParams.KeywordAttributes("new kw", false, null, null);
                PostFilterParams.KeywordAttributes editedKeyword = new PostFilterParams.KeywordAttributes("edited", true, singleFilterResult.keywords.get(0).id, null);
                PostFilterParams.KeywordAttributes deletedKeyword = new PostFilterParams.KeywordAttributes(null, null, singleFilterResult.keywords.get(1).id, true);
                editFilterParams.keywords_attributes = Arrays.asList(newKeyword, editedKeyword, deletedKeyword);
                assertNotFound(webClient.put()
                        .uri("/api/v2/filters/" + filterResult.id)
                        .header("Authorization", bobAuth)
                        .bodyValue(editFilterParams));

                GetFilter editedFilter = webClient.put()
                        .uri("/api/v2/filters/" + filterResult.id)
                        .header("Authorization", aliceAuth)
                        .bodyValue(editFilterParams)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetFilter.class);
                        }).block();
                assertEquals("new title", editedFilter.title);
                assertEquals(3, editedFilter.keywords.size());
                assertEquals("edited", editedFilter.keywords.get(0).keyword);
                assertEquals("bob", editedFilter.keywords.get(1).keyword);
                assertEquals("new kw", editedFilter.keywords.get(2).keyword);

                // filtered statuses are removed from alice's timeline
                aliceTimeline =
                        webClient.get().uri("/api/v1/timelines/home")
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", aliceAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                Set<String> aliceTimelineIds = aliceTimeline.stream().map(status -> status.id).collect(Collectors.toSet());
                Set<String> filteredStatusIds = filteredStatuses.stream().map(status -> status.id).collect(Collectors.toSet());
                aliceTimelineIds.retainAll(filteredStatusIds);
                assertEquals(0, aliceTimelineIds.size());

                // filter status endpoints
                webClient.post()
                    .uri("/api/v2/filters/" + filterResult.id + "/statuses")
                    .header("Authorization", aliceAuth)
                    .bodyValue(new PostFilterStatusParams(aliceStatusId1))
                    .exchangeToMono(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                        return response.bodyToMono(GetFilter.FilterStatus.class);
                    }).block();
                List<GetFilter.FilterStatus> filterStatuses = webClient.get()
                        .uri("/api/v2/filters/" + filterResult.id + "/statuses")
                        .header("Authorization", aliceAuth)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(new ParameterizedTypeReference<List<GetFilter.FilterStatus>>(){});
                        }).block();
                assertEquals(1, filterStatuses.size());
                String filterStatusId = filterStatuses.get(0).id;

                GetFilter.FilterStatus filterStatus = webClient.get()
                        .uri("/api/v2/filters/statuses/" + filterStatusId)
                        .header("Authorization", aliceAuth)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetFilter.FilterStatus.class);
                        }).block();
                assertEquals(aliceStatusId1, filterStatus.status_id);

                webClient.delete()
                    .uri("/api/v2/filters/statuses/" + filterStatusId)
                    .header("Authorization", aliceAuth)
                    .exchangeToMono(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                        return response.bodyToMono(String.class);
                    }).block();

                assertNotFound(webClient.get()
                                        .uri("/api/v2/filters/statuses/" + filterStatusId)
                                        .header("Authorization", aliceAuth));

                List<GetFilter.FilterStatus> filterStatusesAfterDelete = webClient.get()
                        .uri("/api/v2/filters/" + filterResult.id + "/statuses")
                        .header("Authorization", aliceAuth)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(new ParameterizedTypeReference<List<GetFilter.FilterStatus>>(){});
                        }).block();
                assertEquals(0, filterStatusesAfterDelete.size());

                // filter keyword endpoints
                List<GetFilter.FilterKeyword> filterKeywords = webClient.get()
                    .uri("/api/v2/filters/" + filterResult.id + "/keywords")
                    .header("Authorization", aliceAuth)
                    .exchangeToMono(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                        return response.bodyToMono(new ParameterizedTypeReference<List<GetFilter.FilterKeyword>>(){});
                    }).block();
                assertEquals(3, filterKeywords.size());
                assertEquals("edited", filterKeywords.get(0).keyword);

                GetFilter.FilterKeyword singleKeyword = webClient.get()
                        .uri("/api/v2/filters/keywords/" + filterKeywords.get(0).id)
                        .header("Authorization", aliceAuth)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetFilter.FilterKeyword.class);
                        }).block();
                assertEquals("edited", singleKeyword.keyword);
                assertTrue(singleKeyword.whole_word);

                GetFilter.FilterKeyword secondNewKeyword = webClient.post()
                        .uri("/api/v2/filters/" + filterResult.id + "/keywords")
                        .header("Authorization", aliceAuth)
                        .bodyValue(new PostFilterKeywordParams("second new keyword", null))
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetFilter.FilterKeyword.class);
                        }).block();
                GetFilter filterWithSecondKeyword = webClient.get()
                        .uri("/api/v2/filters/" + filterResult.id)
                        .header("Authorization", aliceAuth)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetFilter.class);
                        }).block();
                assertTrue(filterWithSecondKeyword.keywords.stream().anyMatch(k -> k.keyword.equals(secondNewKeyword.keyword)));
                GetFilter.FilterKeyword editedSecondKeyword = webClient.put()
                        .uri("/api/v2/filters/keywords/" + secondNewKeyword.id)
                        .header("Authorization", aliceAuth)
                        .bodyValue(new PostFilterKeywordParams("edited second keyword", false))
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetFilter.FilterKeyword.class);
                        }).block();
                assertEquals("edited second keyword", editedSecondKeyword.keyword);

                webClient.delete()
                    .uri("/api/v2/filters/keywords/" + editedSecondKeyword.id)
                    .header("Authorization", aliceAuth)
                    .exchangeToMono(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                        return response.bodyToMono(String.class);
                    }).block();
                GetFilter filterWithSecondKeywordDeleted = webClient.get()
                        .uri("/api/v2/filters/" + filterResult.id)
                        .header("Authorization", aliceAuth)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(GetFilter.class);
                        }).block();
                assertFalse(filterWithSecondKeywordDeleted.keywords.stream().anyMatch(k -> k.keyword.equals("edited second keyword")));

                // deleting filters
                webClient.delete()
                         .uri("/api/v2/filters/" + filterResult.id)
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         }).block();
                webClient.get()
                         .uri("/api/v2/filters/" + filterResult.id)
                         .header("Authorization", aliceAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
                             return response.bodyToMono(String.class);
                         }).block();
            }
        }
    }

    GetRelationship postFollow(String authToken, long targetId) {
        return postFollow(authToken, "" + targetId);
    }

    GetRelationship postFollow(String authToken, String targetId) {
        return webClient.post().uri("/api/v1/accounts/" + targetId + "/follow")
            .header("Authorization", authToken)
            .exchangeToMono(response -> {
                    assertEquals(HttpStatus.OK, response.statusCode());
                    return response.bodyToMono(GetRelationship.class);
                })
            .block();
    }

    List<GetAccount> getFollowRequests(String authToken) {
        return webClient.get()
                        .uri("/api/v1/follow_requests")
                        .header("Authorization", authToken)
                        .exchangeToMono(response -> {
                            assertEquals(HttpStatus.OK, response.statusCode());
                            return response.bodyToMono(new ParameterizedTypeReference<List<GetAccount>>(){});
                        }).block();
    }

    GetRelationship postUnfollow(String authToken, long targetId) {
        return postUnfollow(authToken, "" + targetId);
    }

    GetRelationship postUnfollow(String authToken, String targetId) {
        return webClient.post().uri("/api/v1/accounts/" + targetId + "/unfollow")
            .header("Authorization", authToken)
            .exchangeToMono(response -> {
                    assertEquals(HttpStatus.OK, response.statusCode());
                    return response.bodyToMono(GetRelationship.class);
                })
            .block();
    }

    String getAuthToken(String username, String password) {
        GetToken getToken =
                webClient.post().uri("/oauth/token")
                         .bodyValue(new PostToken(username, password))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetToken.class);
                         })
                        .block();
        return getToken.token_type + " " + getToken.access_token;
    }
}
