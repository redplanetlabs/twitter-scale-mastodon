package com.rpl.mastodonapi;

import com.rpl.mastodon.MastodonConfig;
import com.rpl.mastodonapi.pojos.*;
import com.rpl.rama.RamaClusterManager;
import com.rpl.rama.test.InProcessCluster;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static com.rpl.mastodonapi.TestHelpers.attainCondition;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
public class IntegrationTest {
    @Test
    public void integrationTest() throws NoSuchAlgorithmException, IOException, NoSuchProviderException {
        // - this is run using an actual Mastodon instance running on Rama as well as an actual Mastodon instance (the Ruby version)
        // - this tests ActivityPub communication between the instances
        // - this test will only run when it is specifically selected like this: mvn test -Dtest=IntegrationTest
        if (!"IntegrationTest".equals(System.getProperty("test"))) {
            throw new RuntimeException("Can't run integration test unless it is explicitly specified:\nmvn test -Dtest=IntegrationTest");
        }

        MastodonConfig.API_URL = "https://javamastodon.internal.redplanetlabs.com";
        MastodonConfig.API_WEB_SOCKET_URL = "https://javamastodon.internal.redplanetlabs.com";
        MastodonConfig.API_DOMAIN = "javamastodon.internal.redplanetlabs.com";
        MastodonConfig.FRONTEND_URL = "https://javamastodon.internal.redplanetlabs.com";

        String rubyExternalUrl = "https://rubymastodon.internal.redplanetlabs.com";
        String rubyDomain = "rubymastodon.internal.redplanetlabs.com";

        MastodonApiApplication.initRealCluster();

        WebClient ramaWebClient = WebClient.create("https://javamastodon.internal.redplanetlabs.com");
        WebClient rubyWebClient = WebClient.create(rubyExternalUrl);

        // bob logs in
        GetToken bobToken =
            ramaWebClient.post().uri("/oauth/token")
                         .bodyValue(new PostToken("bob", "bob"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetToken.class);
                         })
                         .block();
        String bobAuth = bobToken.token_type + " " + bobToken.access_token;

        // bob searches for charlie's account
        GetSearch bobUrlSearch =
            ramaWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v2/search")
                                                            .queryParam("q", rubyExternalUrl + "/users/charlie")
                                                            .queryParam("resolve", "true")
                                                            .build())
                         .accept(MediaType.APPLICATION_JSON)
                         .header("Authorization", bobAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetSearch.class);
                         })
                         .block();
        assertEquals(1, bobUrlSearch.accounts.size());
        assertEquals("charlie@" + rubyDomain, bobUrlSearch.accounts.get(0).acct);

        // get charlie's remote account
        GetAccount charlieRemoteAccount =
            ramaWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                                            .queryParam("acct", "charlie@" + rubyDomain)
                                                            .build())
                         .accept(MediaType.APPLICATION_JSON)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetAccount.class);
                         })
                         .block();

        // charlie authorizes app
        HashMap app =
            rubyWebClient.post().uri("/api/v1/apps")
                         .bodyValue(new HashMap(){{
                             put("client_name", "Foo");
                             put("redirect_uris", "urn:ietf:wg:oauth:2.0:oob");
                             put("scopes", "read write follow push");
                             put("website", "");
                         }})
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(HashMap.class);
                         })
                         .block();

        // charlie gets token
        GetToken charlieToken =
            rubyWebClient.post().uri("/oauth/token")
                         .bodyValue(new PostToken((String) app.get("client_id"), (String) app.get("client_secret"), "read write follow push", "charlie@redplanetlabs.com", "charliecharlie"))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetToken.class);
                         })
                         .block();
        String charlieAuth = charlieToken.token_type + " " + charlieToken.access_token;

        // charlie searches for bob's account
        GetSearch charlieUrlSearch =
            rubyWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v2/search")
                                                            .queryParam("q", MastodonConfig.API_URL + "/users/bob")
                                                            .queryParam("resolve", "true")
                                                            .build())
                         .accept(MediaType.APPLICATION_JSON)
                         .header("Authorization", charlieAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetSearch.class);
                         })
                         .block();
        assertEquals(1, charlieUrlSearch.accounts.size());
        assertEquals("bob@" + MastodonConfig.API_DOMAIN, charlieUrlSearch.accounts.get(0).acct);

        // get bob's remote account
        GetAccount bobRemoteAccount =
            rubyWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/lookup")
                                                            .queryParam("acct", "bob@" + MastodonConfig.API_DOMAIN)
                                                            .build())
                         .accept(MediaType.APPLICATION_JSON)
                         .header("Authorization", charlieAuth)
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(GetAccount.class);
                         })
                         .block();

        // rama -> ruby follow
        {
            // bob follows charlie
            GetRelationship relationship =
                ramaWebClient.post().uri("/api/v1/accounts/" + charlieRemoteAccount.id + "/follow")
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                             .block();
            assertTrue(relationship.following);

            // charlie is followed by bob
            attainCondition(() -> {
                List<GetRelationship> relationships =
                    rubyWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", bobRemoteAccount.id)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                return relationships.size() == 1 && relationships.get(0).followed_by;
            });
        }

        // ruby -> rama follow
        {
            // charlie follows bob
            GetRelationship relationship =
                rubyWebClient.post().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/follow")
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetRelationship.class);
                             })
                             .block();
            assertTrue(relationship.following);

            // bob is followed by charlie
            attainCondition(() -> {
                List<GetRelationship> relationships =
                    ramaWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", charlieRemoteAccount.id)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                return relationships.size() == 1 && relationships.get(0).followed_by;
            });
        }

        AtomicReference<GetStatus> bobRemoteStatus = new AtomicReference<>();
        GetStatus bobLocalStatus;

        // rama -> ruby public status
        {
            String content = UUID.randomUUID().toString();

            // bob posts a public status
            bobLocalStatus =
                ramaWebClient.post().uri("/api/v1/statuses")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostStatus(content, null, "public"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // charlie received the status
            attainCondition(() -> {
                List<GetStatus> publicTimeline =
                    rubyWebClient.get().uri("/api/v1/timelines/public").accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                if (publicTimeline.size() > 0) {
                    GetStatus status = publicTimeline.get(0);
                    bobRemoteStatus.set(status);
                    return status.content.contains(content);
                }
                return false;
            });
        }

        AtomicReference<GetStatus> charlieRemoteStatus = new AtomicReference<>();
        GetStatus charlieLocalStatus;

        // ruby -> rama public status
        {
            String content = UUID.randomUUID().toString();

            // charlie writes a reply
            charlieLocalStatus =
                rubyWebClient.post().uri("/api/v1/statuses")
                             .header("Authorization", charlieAuth)
                             .bodyValue(new PostStatus(content, bobRemoteStatus.get().id, "public"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // bob received the reply
            attainCondition(() -> {
                List<GetStatus> publicTimeline =
                    ramaWebClient.get().uri("/api/v1/timelines/public").accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                if (publicTimeline.size() > 0) {
                    GetStatus status = publicTimeline.get(0);
                    charlieRemoteStatus.set(status);
                    return status.content.contains(content);
                }
                return false;
            });
        }

        // rama -> ruby edit status
        {
            String content = UUID.randomUUID().toString();

            // bob edits the status
            ramaWebClient.put().uri("/api/v1/statuses/" + bobLocalStatus.id)
                         .header("Authorization", bobAuth)
                         .bodyValue(new PutStatus(content))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

            // charlie received the edit
            attainCondition(() -> {
                List<GetStatus> publicTimeline =
                    rubyWebClient.get().uri("/api/v1/timelines/public").accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                if (publicTimeline.size() > 0) {
                    GetStatus status = publicTimeline.get(0);
                    return status.content.contains(content);
                }
                return false;
            });
        }

        // ruby -> rama edit status
        {
            String content = UUID.randomUUID().toString();

            // charlie edits the status
            rubyWebClient.put().uri("/api/v1/statuses/" + charlieLocalStatus.id)
                         .header("Authorization", charlieAuth)
                         .bodyValue(new PutStatus(content))
                         .exchangeToMono(response -> {
                             assertEquals(HttpStatus.OK, response.statusCode());
                             return response.bodyToMono(String.class);
                         })
                         .block();

            // bob received the edit
            attainCondition(() -> {
                List<GetStatus> publicTimeline =
                    ramaWebClient.get().uri("/api/v1/timelines/public").accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                 })
                                 .block();
                if (publicTimeline.size() > 0) {
                    // edits to statuses are not reflected in the global timeline
                    // on the java side, so the status will not contain the new content.
                    GetStatus status = publicTimeline.get(0);
                    return !status.content.contains(content);
                }
                return false;
            });
        }

        // rama -> ruby private status
        {
            String content = UUID.randomUUID().toString();

            // bob posts a private (followers only) status
            GetStatus localStatus =
                ramaWebClient.post().uri("/api/v1/statuses")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostStatus(content, null, "private"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // charlie received the status
            attainCondition(() -> {
                List<GetStatus> charlieTimeline =
                        rubyWebClient.get().uri("/api/v1/timelines/home").accept(MediaType.APPLICATION_JSON)
                                           .header("Authorization", charlieAuth)
                                           .exchangeToMono(response -> {
                                               assertEquals(HttpStatus.OK, response.statusCode());
                                               return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                           })
                                           .block();
                if (charlieTimeline.size() > 0) {
                    GetStatus status = charlieTimeline.get(0);
                    return status.content.contains(content);
                }
                return false;
            });
        }

        // ruby -> rama private status
        {
            String content = UUID.randomUUID().toString();

            // charlie posts a private (followers only) status
            GetStatus localStatus =
                rubyWebClient.post().uri("/api/v1/statuses")
                             .header("Authorization", charlieAuth)
                             .bodyValue(new PostStatus(content, null, "private"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // bob received the status
            attainCondition(() -> {
                List<GetStatus> bobTimeline =
                        ramaWebClient.get().uri("/api/v1/timelines/home").accept(MediaType.APPLICATION_JSON)
                                           .header("Authorization", bobAuth)
                                           .exchangeToMono(response -> {
                                               assertEquals(HttpStatus.OK, response.statusCode());
                                               return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                           })
                                           .block();
                if (bobTimeline.size() > 0) {
                    GetStatus status = bobTimeline.get(0);
                    return status.content.contains(content);
                }
                return false;
            });
        }

        // rama -> ruby direct status
        {
            String content = UUID.randomUUID().toString();

            // bob posts a direct status
            GetStatus localStatus =
                ramaWebClient.post().uri("/api/v1/statuses")
                             .header("Authorization", bobAuth)
                             .bodyValue(new PostStatus("@charlie@" + rubyDomain + " " + content, null, "direct"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // charlie received the status
            attainCondition(() -> {
                List<GetConversation> charlieConvos =
                    rubyWebClient.get().uri("/api/v1/conversations").accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetConversation>>(){});
                                 })
                                 .block();
                if (charlieConvos.size() > 0) {
                    GetStatus status = charlieConvos.get(0).last_status;
                    return status.content.contains(content);
                }
                return false;
            });
        }

        // ruby -> rama direct status
        {
            String content = UUID.randomUUID().toString();

            // charlie posts a direct status
            GetStatus localStatus =
                rubyWebClient.post().uri("/api/v1/statuses")
                             .header("Authorization", charlieAuth)
                             .bodyValue(new PostStatus("@bob@" + MastodonConfig.API_DOMAIN + " " + content, null, "direct"))
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // bob received the status
            attainCondition(() -> {
                List<GetStatus> bobTimeline =
                        ramaWebClient.get().uri("/api/v1/timelines/direct").accept(MediaType.APPLICATION_JSON)
                                           .header("Authorization", bobAuth)
                                           .exchangeToMono(response -> {
                                               assertEquals(HttpStatus.OK, response.statusCode());
                                               return response.bodyToMono(new ParameterizedTypeReference<List<GetStatus>>(){});
                                           })
                                           .block();
                if (bobTimeline.size() > 0) {
                    GetStatus status = bobTimeline.get(0);
                    return status.content.contains(content);
                }
                return false;
            });
        }

        // rama -> ruby favorite status
        {
            // bob favorites charlie's status
            GetStatus favStatus =
                ramaWebClient.post().uri("/api/v1/statuses/" + charlieRemoteStatus.get().id + "/favourite")
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // charlie receives the favorite
            attainCondition(() -> {
                GetStatus status =
                    rubyWebClient.get().uri("/api/v1/statuses/" + charlieLocalStatus.id).accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                return status.favourites_count == 1;
            });
        }

        // rama -> ruby unfavorite status
        {
            // bob unfavorites charlie's status
            GetStatus favStatus =
                ramaWebClient.post().uri("/api/v1/statuses/" + charlieRemoteStatus.get().id + "/unfavourite")
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // charlie receives the unfavorite
            attainCondition(() -> {
                GetStatus status =
                    rubyWebClient.get().uri("/api/v1/statuses/" + charlieLocalStatus.id).accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                return status.favourites_count == 0;
            });
        }

        // ruby -> rama favorite status
        {
            // charlie favorites bob's status
            GetStatus favStatus =
                rubyWebClient.post().uri("/api/v1/statuses/" + bobRemoteStatus.get().id + "/favourite")
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // bob receives the favorite
            attainCondition(() -> {
                GetStatus status =
                    ramaWebClient.get().uri("/api/v1/statuses/" + bobLocalStatus.id).accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                return status.favourites_count == 1;
            });
        }

        // ruby -> rama unfavorite status
        {
            // charlie unfavorites bob's status
            GetStatus favStatus =
                rubyWebClient.post().uri("/api/v1/statuses/" + bobRemoteStatus.get().id + "/unfavourite")
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // bob receives the unfavorite
            attainCondition(() -> {
                GetStatus status =
                    ramaWebClient.get().uri("/api/v1/statuses/" + bobLocalStatus.id).accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                return status.favourites_count == 0;
            });
        }

        // rama -> ruby boost status
        {
            // bob boosts charlie's status
            GetStatus boostStatus =
                ramaWebClient.post().uri("/api/v1/statuses/" + charlieRemoteStatus.get().id + "/reblog")
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // charlie receives the boost
            attainCondition(() -> {
                GetStatus status =
                    rubyWebClient.get().uri("/api/v1/statuses/" + charlieLocalStatus.id).accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                return status.reblogs_count == 1;
            });
        }

        // rama -> ruby unboost status
        {
            // bob unboosts charlie's status
            GetStatus boostStatus =
                ramaWebClient.post().uri("/api/v1/statuses/" + charlieRemoteStatus.get().id + "/unreblog")
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // charlie receives the unboost
            attainCondition(() -> {
                GetStatus status =
                    rubyWebClient.get().uri("/api/v1/statuses/" + charlieLocalStatus.id).accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                return status.reblogs_count == 0;
            });
        }

        // ruby -> rama boost status
        {
            // charlie boosts bob's status
            GetStatus boostStatus =
                rubyWebClient.post().uri("/api/v1/statuses/" + bobRemoteStatus.get().id + "/reblog")
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // bob receives the boost
            attainCondition(() -> {
                GetStatus status =
                    ramaWebClient.get().uri("/api/v1/statuses/" + bobLocalStatus.id).accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                return status.reblogs_count == 1;
            });
        }

        // ruby -> rama unboost status
        {
            // charlie unboosts bob's status
            GetStatus boostStatus =
                rubyWebClient.post().uri("/api/v1/statuses/" + bobRemoteStatus.get().id + "/unreblog")
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // bob receives the unboost
            attainCondition(() -> {
                GetStatus status =
                    ramaWebClient.get().uri("/api/v1/statuses/" + bobLocalStatus.id).accept(MediaType.APPLICATION_JSON)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetStatus.class);
                                 })
                                 .block();
                return status.reblogs_count == 0;
            });
        }

        // rama -> ruby delete status
        {
            // bob deletes a status
            GetStatus deletedStatus =
                ramaWebClient.delete().uri("/api/v1/statuses/" + bobLocalStatus.id)
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // charlie receives the deletion
            attainCondition(() -> {
                AtomicReference<HttpStatus> statusCode = new AtomicReference<>();
                rubyWebClient.get().uri("/api/v1/statuses/" + bobRemoteStatus.get().id).accept(MediaType.APPLICATION_JSON)
                             .exchangeToMono(response -> {
                                 statusCode.set(response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();
                return statusCode.get() == HttpStatus.NOT_FOUND;
            });
        }

        // ruby -> rama delete status
        {
            // charlie deletes a status
            GetStatus deletedStatus =
                rubyWebClient.delete().uri("/api/v1/statuses/" + charlieLocalStatus.id)
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetStatus.class);
                             })
                             .block();

            // bob receives the deletion
            attainCondition(() -> {
                AtomicReference<HttpStatus> statusCode = new AtomicReference<>();
                ramaWebClient.get().uri("/api/v1/statuses/" + charlieRemoteStatus.get().id).accept(MediaType.APPLICATION_JSON)
                             .exchangeToMono(response -> {
                                 statusCode.set(response.statusCode());
                                 return response.bodyToMono(String.class);
                             })
                             .block();
                return statusCode.get() == HttpStatus.NOT_FOUND;
            });
        }

        // rama -> ruby unfollow
        {
            // bob unfollows charlie
            GetRelationship relationship =
                ramaWebClient.post().uri("/api/v1/accounts/" + charlieRemoteAccount.id + "/unfollow")
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                             .block();
            assertFalse(relationship.following);

            // charlie is unfollowed by bob
            attainCondition(() -> {
                List<GetRelationship> relationships =
                    rubyWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", bobRemoteAccount.id)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                return relationships.size() == 1 && !relationships.get(0).followed_by;
            });
        }

        // ruby -> rama unfollow
        {
            // charlie unfollows bob
            GetRelationship relationship =
                rubyWebClient.post().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/unfollow")
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetRelationship.class);
                             })
                             .block();
            assertFalse(relationship.following);

            // bob is unfollowed by charlie
            attainCondition(() -> {
                List<GetRelationship> relationships =
                    ramaWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", charlieRemoteAccount.id)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                return relationships.size() == 1 && !relationships.get(0).followed_by;
            });
        }

        // rama -> ruby block
        {
            // bob blocks charlie
            GetRelationship relationship =
                ramaWebClient.post().uri("/api/v1/accounts/" + charlieRemoteAccount.id + "/block")
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                             .block();
            assertTrue(relationship.blocking);

            // charlie is blocked by bob
            attainCondition(() -> {
                List<GetRelationship> relationships =
                    rubyWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", bobRemoteAccount.id)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                return relationships.size() == 1 && relationships.get(0).blocked_by;
            });
        }

        // ruby -> rama block
        {
            // charlie blocks bob
            GetRelationship relationship =
                rubyWebClient.post().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/block")
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetRelationship.class);
                             })
                             .block();
            assertTrue(relationship.blocking);

            // bob is blocked by charlie
            attainCondition(() -> {
                List<GetRelationship> relationships =
                    ramaWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", charlieRemoteAccount.id)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                return relationships.size() == 1 && relationships.get(0).blocked_by;
            });
        }

        // rama -> ruby unblock
        {
            // bob unblocks charlie
            GetRelationship relationship =
                ramaWebClient.post().uri("/api/v1/accounts/" + charlieRemoteAccount.id + "/unblock")
                             .header("Authorization", bobAuth)
                             .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(GetRelationship.class);
                                 })
                             .block();
            assertFalse(relationship.blocking);

            // charlie is unblocked by bob
            attainCondition(() -> {
                List<GetRelationship> relationships =
                    rubyWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", bobRemoteAccount.id)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", charlieAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                return relationships.size() == 1 && !relationships.get(0).blocked_by;
            });
        }

        // ruby -> rama unblock
        {
            // charlie unblocks bob
            GetRelationship relationship =
                rubyWebClient.post().uri("/api/v1/accounts/" + bobRemoteAccount.id + "/unblock")
                             .header("Authorization", charlieAuth)
                             .exchangeToMono(response -> {
                                 assertEquals(HttpStatus.OK, response.statusCode());
                                 return response.bodyToMono(GetRelationship.class);
                             })
                             .block();
            assertFalse(relationship.blocking);

            // bob is unblocked by charlie
            attainCondition(() -> {
                List<GetRelationship> relationships =
                    ramaWebClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/accounts/relationships")
                                                                    .queryParam("id[]", charlieRemoteAccount.id)
                                                                    .build())
                                 .accept(MediaType.APPLICATION_JSON)
                                 .header("Authorization", bobAuth)
                                 .exchangeToMono(response -> {
                                     assertEquals(HttpStatus.OK, response.statusCode());
                                     return response.bodyToMono(new ParameterizedTypeReference<List<GetRelationship>>(){});
                                 })
                                 .block();
                return relationships.size() == 1 && !relationships.get(0).blocked_by;
            });
        }
    }
}
