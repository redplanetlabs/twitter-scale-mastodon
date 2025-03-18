package com.rpl.mastodonapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.rpl.mastodon.*;
import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.pojos.*;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.AbstractMap.SimpleEntry;

@RestController
@CrossOrigin(exposedHeaders = {"Link"})
public class MastodonApiController {
    private static final int QUERY_PARAM_ARRAY_SIZE_LIMIT = 200;

    public static MastodonApiManager manager;

    private static long getMandatoryAccountId(WebSession session) {
      Long requestAccountId = (Long) session.getAttributes().get("accountId");
      if (requestAccountId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
      return requestAccountId;
    }

    private Mono<GetActivityPubProfile> getRemoteAccountProfile(String url) {
        WebClient webClient = WebClient.create(url);
        return webClient.get().uri("")
                        .accept(MediaType.parseMediaType("application/activity+json"), MediaType.parseMediaType("application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\""))
                        .exchangeToMono(response -> {
                            if (HttpStatus.OK == response.statusCode()) return response.bodyToMono(GetActivityPubProfile.class);
                            else throw new ResponseStatusException(response.statusCode());
                        });
    }

    private Mono<AccountWithId> getRemoteAccountWithId(GetActivityPubProfile profile, String host) {
        String nameWithHost = String.format("%s@%s", profile.preferredUsername, host);
        return Mono.fromFuture(manager.getAccountId(nameWithHost))
                   .switchIfEmpty(Mono.just(-1L))
                   .flatMap(existingAccountId -> {
                       if (existingAccountId == -1L) {
                           return Mono.fromFuture(manager.postRemoteAccount(nameWithHost, new RemoteAccount(profile.id, profile.inbox, profile.endpoints.getOrDefault("sharedInbox", null)), profile.publicKey.publicKeyPem))
                                      .flatMap(result -> Mono.fromFuture(manager.getAccountId(nameWithHost)))
                                      .flatMap(accountId ->
                                              Mono.fromFuture(manager.getAccountWithId(accountId))
                                                  // edit the account with info from the profile
                                                  .flatMap(accountWithId -> Mono.fromFuture(manager.postEditAccount(accountWithId.accountId, MastodonApiHelpers.getEditsFromProfile(accountWithId, profile))))
                                                  .flatMap(result -> Mono.fromFuture(manager.getAccountWithId(accountId))));
                       } else return Mono.fromFuture(manager.getAccountWithId(existingAccountId));
                   })
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    private Mono<StatusQueryResult> getRemoteOrLocalStatus(long requestAccountId, String target) {
        return Mono.fromFuture(
                manager.getRemoteStatus(requestAccountId, target)
                       .thenCompose(statusQueryResult -> {
                           // if we couldn't find the status, and it is from our server,
                           // we can just parse the id out of the path
                           if (statusQueryResult == null) {
                               final URL targetUrl;
                               try {
                                   targetUrl = new URL(target);
                               } catch (MalformedURLException e) {
                                   throw new RuntimeException(e);
                               }
                               String targetHost = targetUrl.getHost();
                               if (targetHost.equals(MastodonConfig.API_DOMAIN)) {
                                   String[] parts = targetUrl.getPath().split("/");
                                   String comboId = parts[parts.length - 1];
                                   StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(comboId);
                                   return manager.getStatus(requestAccountId, statusPointer);
                               } else return CompletableFuture.completedFuture(null);
                           }
                           return CompletableFuture.completedFuture(statusQueryResult);
                       }));
    }

    private Mono<StatusPointer> getRemoteOrLocalStatusPointer(long requestAccountId, String target) {
        return this.getRemoteOrLocalStatus(requestAccountId, target)
                   .map(statusQueryResult -> {
                       if (statusQueryResult == null) return null;
                       return new StatusPointer(statusQueryResult.result.status.author.accountId, statusQueryResult.result.statusId);
                   });
    }

    private Mono<AccountWithId> resolveUrl(String target) throws MalformedURLException {
        URL targetUrl = new URL(target);
        String targetHost = targetUrl.getHost();
        if (targetHost.equals(MastodonConfig.API_DOMAIN) && !targetHost.equals("localhost")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "URL search only works for external URLs");
        }
        WebClient webClient = WebClient.create(target);
        return webClient.get().uri("")
                        .accept(MediaType.APPLICATION_JSON)
                        .exchangeToMono(response -> {
                            if (HttpStatus.OK == response.statusCode()) return response.bodyToMono(GetActivityPubProfile.class);
                            else throw new ResponseStatusException(response.statusCode());
                        })
                        .flatMap(profile -> this.getRemoteAccountWithId(profile, targetHost));
    }

    private Mono<GetToken> loginWithAccount(WebSession session, String scope, AccountWithId accountWithId) {
        // update session
        session.getAttributes().put("accountId", accountWithId.accountId);
        session.getAttributes().put("accountName", accountWithId.account.name);
        // store the session id in the backend and return token
        return Mono.fromFuture(manager.postAuthCode(accountWithId.accountId, session.getId())).map(res -> new GetToken(session.getId(), scope));
    }

    @PostMapping(value = "/api/v1/apps", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetApplication> postApplication(@RequestBody(required = true) PostApplication params) throws NoSuchAlgorithmException {
        // currently, the application isn't saved anywhere
        GetApplication app = new GetApplication();
        app.redirect_uri = params.redirect_uris;
        app.client_secret = "secret_" + MastodonApiHelpers.randomString(16);
        return Mono.just(app);
    }

    @PostMapping(value = "/api/v1/apps", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<GetApplication> postApplication(ServerWebExchange exchange) {
        return exchange.getFormData()
                       .flatMap(formParams -> {
                           PostApplication params = MastodonApiFormParser.parseParams(formParams, new PostApplication());
                           try {
                               return this.postApplication(params);
                           } catch (NoSuchAlgorithmException e) {
                               throw new RuntimeException(e);
                           }
                       });
    }

    @GetMapping(value = "/oauth/authorize", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> getOauthAuthorize(@RequestParam(required = true) String client_id,
                                          @RequestParam(required = true) String redirect_uri,
                                          @RequestParam(required = true) String response_type,
                                          @RequestParam(required = true) String scope) throws IOException {
        File htmlFile = new ClassPathResource("public/authorize.html").getFile();
        String htmlContent = new String(Files.readAllBytes(htmlFile.toPath()));
        htmlContent = String.format(htmlContent, client_id, redirect_uri, scope);
        return Mono.just(htmlContent);
    }

    @PostMapping(value = "/oauth/authorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono postOauthAuthorize(ServerWebExchange exchange) throws NoSuchAlgorithmException {
        String code = MastodonApiHelpers.randomString(32);
        return exchange.getFormData()
                       // parse the params
                       .map(formParams -> {
                           PostOauthAuthorize params = MastodonApiFormParser.parseParams(formParams, new PostOauthAuthorize());
                           if (params.redirect_uri == null || params.scope == null) {
                               exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                               return null;
                           } else {
                               exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                               exchange.getResponse().getHeaders().set("Location", params.redirect_uri + "?code=" + code);
                               return params;
                           }
                       })
                       .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST)))
                       .flatMap(params ->
                                Mono.fromFuture(manager.getAccountId(params.username))
                                    .switchIfEmpty(Mono.just(-1L))
                                    .map(accountId -> {
                                        if (accountId == -1L) {
                                            // redirect browser clients to error page
                                            if (exchange.getRequest().getHeaders().getAccept().contains(MediaType.TEXT_HTML)) {
                                                exchange.getResponse().getHeaders().set("Location", "/auth/error");
                                                throw new ResponseStatusException(HttpStatus.FOUND);
                                            }
                                        }
                                        return accountId;
                                    })
                                    .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username not found")))
                                    // update the session
                                    .flatMap(accountWithId -> {
                                        if (accountWithId.account.content.isSetRemote()) {
                                            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Can't log in to external account");
                                        } else if (!MastodonApiHelpers.matchesPassword(params.password, accountWithId.account.pwdHash)) {
                                            // redirect browser clients to error page
                                            if (exchange.getRequest().getHeaders().getAccept().contains(MediaType.TEXT_HTML)) {
                                                exchange.getResponse().getHeaders().set("Location", "/auth/error");
                                                throw new ResponseStatusException(HttpStatus.FOUND);
                                            } else {
                                                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password does not match");
                                            }
                                        }
                                        return Mono.fromFuture(manager.postAuthCode(accountWithId.accountId, code));
                                    })
                                    .map(res -> new HashMap()));

    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetToken> postOauthToken(WebSession session, @RequestBody(required = true) PostToken params) {
        if ("password".equals(params.grant_type)) {
            return Mono.fromFuture(manager.getAccountId(params.username))
                       .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username not found")))
                       // get the account
                       .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                       .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username not found")))
                       // update the session
                       .flatMap(accountWithId -> {
                           if (accountWithId.account.content.isSetRemote()) {
                               throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Can't log in to external account");
                           } else if (!MastodonApiHelpers.matchesPassword(params.password, accountWithId.account.pwdHash)) {
                               throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password does not match");
                           }
                           return this.loginWithAccount(session, params.scope, accountWithId);
                       });
        } else if ("client_credentials".equals(params.grant_type)) return Mono.just(new GetToken(session.getId(), params.scope));
        else if ("authorization_code".equals(params.grant_type)) {
            if (params.code == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            return Mono.fromFuture(manager.getAccountIdFromAuthCode(params.code))
                       .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                       // get the account
                       .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                       .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                       .flatMap(accountWithId ->
                               Mono.fromFuture(manager.postRemoveAuthCode(params.code))
                                   .flatMap(res -> this.loginWithAccount(session, params.scope, accountWithId)));
        } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<GetToken> postOauthToken(WebSession session, ServerWebExchange exchange) {
        return exchange.getFormData()
                       .flatMap(formParams -> {
                           PostToken params = MastodonApiFormParser.parseParams(formParams, new PostToken());
                           return this.postOauthToken(session, params);
                       });
    }

    @PostMapping(value = "/oauth/revoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono postRevokeOauthToken(@RequestBody(required = true) PostRevokeToken params) {
        return Mono.fromFuture(manager.postRemoveAuthCode(params.token)).map(res -> new HashMap());
    }

    @PostMapping(value = "/oauth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<GetToken> postRevokeOauthToken(ServerWebExchange exchange) {
        return exchange.getFormData()
                       .flatMap(formParams -> {
                           PostRevokeToken params = MastodonApiFormParser.parseParams(formParams, new PostRevokeToken());
                           return this.postRevokeOauthToken(params);
                       });
    }

    @GetMapping(value = "/auth/edit", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> getAuthEdit() throws IOException {
        File htmlFile = new ClassPathResource("public/auth_edit.html").getFile();
        String htmlContent = new String(Files.readAllBytes(htmlFile.toPath()));
        return Mono.just(htmlContent);
    }

    @GetMapping(value = "/auth/error", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> getOauthError() throws IOException {
        File htmlFile = new ClassPathResource("public/auth_error.html").getFile();
        String htmlContent = new String(Files.readAllBytes(htmlFile.toPath()));
        return Mono.just(htmlContent);
    }

    @PostMapping("/api/v1/accounts")
    public Mono postAccount(WebSession session, ServerHttpResponse response, @RequestBody(required = true) PostAccount params) {
        if (!params.username.matches("[a-zA-Z0-9]*")) {
            response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
            return Mono.just(new GetErrorDetails("Username must contain only a-z or numbers", new HashMap<String, GetErrorDetails.Error>(){{
                put("username", new GetErrorDetails.Error("ERR_INVALID", "Username must contain only a-z or numbers"));
            }}));
        } else if(params.username.length() > MastodonApiConfig.MAX_USERNAME_LENGTH) {
          response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
          return Mono.just(new GetErrorDetails("Username too long", new HashMap<String, GetErrorDetails.Error>(){{
              put("agreement", new GetErrorDetails.Error("ERR_INVALID", "Username cannot be greater than " + MastodonApiConfig.MAX_USERNAME_LENGTH + " characters"));
          }}));
        } else if (!params.agreement) {
            response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
            return Mono.just(new GetErrorDetails("The agreement has not been accepted", new HashMap<String, GetErrorDetails.Error>(){{
                put("agreement", new GetErrorDetails.Error("ERR_ACCEPTED", "The agreement has not been accepted"));
            }}));
        }
        return Mono.fromFuture(manager.postAccount(params))
                   .flatMap(success -> {
                       if (success) {
                           return Mono.fromFuture(manager.getAccountId(params.username))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                      // get the account
                                      .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                      // update the session
                                      .flatMap(accountWithId -> this.loginWithAccount(session, "read write follow push", accountWithId));
                       }
                       else {
                           response.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
                           return Mono.just(new GetErrorDetails("Validation failed", new HashMap<String, GetErrorDetails.Error>(){{
                               put("username", new GetErrorDetails.Error("ERR_TAKEN", "Username already in use"));
                           }}));
                       }
                   });
    }

    private void validateStatus(String content, String spoilerText, String language, PostStatus.Poll poll) {
      if (content.length() > MastodonApiConfig.MAX_STATUS_LENGTH) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Status too long");
      if (spoilerText != null && spoilerText.length() > MastodonApiConfig.MAX_STATUS_LENGTH) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Spoiler text too long");
      if (language != null && language.length() > MastodonApiConfig.MAX_STATUS_LENGTH) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Language string too long");
      if(poll!=null && poll.options != null) {
        if(poll.options.size() > 4) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Too many poll choices");
        for(String option: poll.options) {
          if(option.length() > MastodonApiConfig.MAX_POLL_CHOICE_LENGTH) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Poll choice too long");
        }
      }
    }

    @PostMapping(value = "/api/v1/statuses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Object> postStatus(WebSession session, @RequestBody(required = true) PostStatus params) {
        long requestAccountId = getMandatoryAccountId(session);
        validateStatus(params.status, params.spoiler_text, params.language, params.poll);
        if (params.scheduled_at != null) {
          return Mono.fromFuture(manager.postScheduledStatus(requestAccountId, params, null))
                     .map(GetScheduledStatus::new);
        } else return Mono.fromFuture(manager.postStatus(requestAccountId, params, null)).map(GetStatus::new);
    }

    @PostMapping(value = "/api/v1/statuses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Object> postStatus(
            WebSession session,
            @RequestPart(value = "status", required = false) String status,
            @RequestPart(value = "in_reply_to_id", required = false) String in_reply_to_id,
            @RequestPart(value = "sensitive", required = false) String sensitive,
            @RequestPart(value = "spoiler_text", required = false) String spoiler_text,
            @RequestPart(value = "visibility", required = false) String visibility,
            @RequestPart(value = "language", required = false) String language,
            @RequestPart(value = "scheduled_at", required = false) String scheduled_at,
            @RequestPart(value = "media_ids[]", required = false) List<Part> media_ids,
            @RequestPart(value = "poll[options][]", required = false) List<Part> poll_options,
            @RequestPart(value = "poll[expires_in]", required = false) String poll_expires_in,
            @RequestPart(value = "poll[multiple]", required = false) String poll_multiple
    ) {
        List<Mono<DataBuffer>> mediaContent = media_ids.stream().map(part -> part.content().single()).collect(Collectors.toList());
        List<Mono<DataBuffer>> pollOptions = poll_options.stream().map(part -> part.content().single()).collect(Collectors.toList());
        return (mediaContent.size() > 0 ? Mono.zip(mediaContent, res -> Arrays.stream(res).map(b -> ((DataBuffer) b).toString(Charset.defaultCharset())).collect(Collectors.toList())) : Mono.just(new ArrayList<>()))
                .flatMap(mediaContentResults -> (pollOptions.size() > 0 ? Mono.zip(pollOptions, res -> Arrays.stream(res).map(b -> ((DataBuffer) b).toString(Charset.defaultCharset())).collect(Collectors.toList())) : Mono.just(new ArrayList<>()))
                        .flatMap(pollOptionsResults -> {
                            PostStatus postStatus = new PostStatus(status, in_reply_to_id, visibility, scheduled_at);
                            postStatus.spoiler_text = spoiler_text;
                            postStatus.language = language;
                            postStatus.sensitive = sensitive != null ? Boolean.parseBoolean(sensitive) : null;
                            postStatus.media_ids = (List<String>) mediaContentResults;
                            if (pollOptionsResults.size() > 0 && poll_expires_in != null) {
                                postStatus.poll = new PostStatus.Poll();
                                postStatus.poll.options = (List<String>) pollOptionsResults;
                                postStatus.poll.expires_in = Long.parseLong(poll_expires_in);
                                postStatus.poll.multiple = poll_multiple != null ? Boolean.parseBoolean(poll_multiple) : false;
                            }
                            return this.postStatus(session, postStatus);
                        }));
    }

    @GetMapping("/api/v1/scheduled_statuses")
    public Mono<List<GetScheduledStatus>> getScheduledStatuses(ServerWebExchange exchange, WebSession session,
                                                               @RequestParam(required = false) String max_id,
                                                               @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getScheduledStatuses(requestAccountId, statusPointer, limit))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return queryResults.results.stream()
                                                  .map(GetScheduledStatus::new)
                                                  .collect(Collectors.toList());
                   });
    }

    @GetMapping("/api/v1/scheduled_statuses/{id}")
    public Mono<GetScheduledStatus> getScheduledStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Mono.fromFuture(manager.getScheduledStatus(statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetScheduledStatus::new);
    }

    @PutMapping("/api/v1/scheduled_statuses/{id}")
    public Mono<GetScheduledStatus> updateScheduledStatus(WebSession session,
                                                          @PathVariable("id") String id,
                                                          @RequestBody PutScheduledStatus putScheduledStatus) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Mono.fromFuture(manager.updateScheduledStatus(statusPointer, putScheduledStatus.scheduled_at))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetScheduledStatus::new);
    }

    @DeleteMapping("/api/v1/scheduled_statuses/{id}")
    public Mono<Map<String, Object>> cancelScheduledStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Mono.fromFuture(manager.cancelScheduledStatus(statusPointer));
    }

    @PostMapping("/api/v1/accounts/{id}/follow")
    public Mono<GetRelationship> postFollowAccount(WebSession session, @PathVariable("id") String id, @RequestBody(required = false) PostFollow params) {
        long requestAccountId = getMandatoryAccountId(session);
        long followeeId = MastodonHelpers.parseAccountId(id);
        String requestAccountName = (String) session.getAttributes().get("accountName");
        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, followeeId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(accountWithIdPair -> {
                       AccountWithId requester = accountWithIdPair.getKey();
                       AccountWithId followee = accountWithIdPair.getValue();
                       if (followee.account.content.isSetRemote()) {
                           String sourceUrl = String.format("%s/users/%s", MastodonConfig.API_URL, requestAccountName);
                           String targetUrl = followee.account.content.getRemote().mainUrl;
                           return Mono.fromFuture(MastodonWebHelpers.buildRequest(requester.account, followee.account.content.getRemote().inboxUrl, new PostActivityPubInbox("Follow", sourceUrl, targetUrl)))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
                       } else return Mono.just(true);
                   })
                   .flatMap(result -> Mono.fromFuture(manager.postFollowAccount(requestAccountId, followeeId, null, params)))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, followeeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/accounts/{id}/unfollow")
    public Mono<GetRelationship> postUnfollowAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long followeeId = MastodonHelpers.parseAccountId(id);
        String requestAccountName = (String) session.getAttributes().get("accountName");
        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, followeeId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(accountWithIdPair -> {
                       AccountWithId requester = accountWithIdPair.getKey();
                       AccountWithId followee = accountWithIdPair.getValue();
                       if (followee.account.content.isSetRemote()) {
                           String sourceUrl = String.format("%s/users/%s", MastodonConfig.API_URL, requestAccountName);
                           String targetUrl = followee.account.content.getRemote().mainUrl;
                           return Mono.fromFuture(MastodonWebHelpers.buildRequest(requester.account, followee.account.content.getRemote().inboxUrl, new PostActivityPubInbox("Undo", sourceUrl, new PostActivityPubInbox("Follow", sourceUrl, targetUrl))))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
                       } else return Mono.just(true);
                   })
                   .flatMap(result -> Mono.fromFuture(manager.postRemoveFollowAccount(requestAccountId, followeeId, null)))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, followeeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/accounts/{id}/mute")
    public Mono<GetRelationship> postMuteAccount(WebSession session, @PathVariable("id") String id, @RequestBody(required = true) PostMute params) {
        long requestAccountId = getMandatoryAccountId(session);
        long muteeId = MastodonHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postMuteAccount(requestAccountId, muteeId, params))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, muteeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/accounts/{id}/unmute")
    public Mono<GetRelationship> postUnmuteAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long muteeId = MastodonHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postRemoveMuteAccount(requestAccountId, muteeId))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, muteeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/accounts/{id}/remove_from_followers")
    public Mono<GetRelationship> postRemoveFromFollowers(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long followerId = MastodonHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postRemoveFollowAccount(followerId, requestAccountId, null))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, followerId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/accounts/{id}/block")
    public Mono<GetRelationship> postBlockAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long blockeeId = MastodonHelpers.parseAccountId(id);
        String requestAccountName = (String) session.getAttributes().get("accountName");
        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, blockeeId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(accountWithIdPair -> {
                       AccountWithId requester = accountWithIdPair.getKey();
                       AccountWithId blockee = accountWithIdPair.getValue();
                       if (blockee.account.content.isSetRemote()) {
                           String sourceUrl = String.format("%s/users/%s", MastodonConfig.API_URL, requestAccountName);
                           String targetUrl = blockee.account.content.getRemote().mainUrl;
                           return Mono.fromFuture(MastodonWebHelpers.buildRequest(requester.account, blockee.account.content.getRemote().inboxUrl, new PostActivityPubInbox("Block", sourceUrl, targetUrl)))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
                       } else return Mono.just(true);
                   })
                   .flatMap(result -> Mono.fromFuture(manager.postBlockAccount(requestAccountId, blockeeId)))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, blockeeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/accounts/{id}/unblock")
    public Mono<GetRelationship> postUnblockAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long blockeeId = MastodonHelpers.parseAccountId(id);
        String requestAccountName = (String) session.getAttributes().get("accountName");
        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, blockeeId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(accountWithIdPair -> {
                       AccountWithId requester = accountWithIdPair.getKey();
                       AccountWithId blockee = accountWithIdPair.getValue();
                       if (blockee.account.content.isSetRemote()) {
                           String sourceUrl = String.format("%s/users/%s", MastodonConfig.API_URL, requestAccountName);
                           String targetUrl = blockee.account.content.getRemote().mainUrl;
                           return Mono.fromFuture(MastodonWebHelpers.buildRequest(requester.account, blockee.account.content.getRemote().inboxUrl, new PostActivityPubInbox("Undo", sourceUrl, new PostActivityPubInbox("Block", sourceUrl, targetUrl))))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
                       } else return Mono.just(true);
                   })
                   .flatMap(result -> Mono.fromFuture(manager.postRemoveBlockAccount(requestAccountId, blockeeId)))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, blockeeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/accounts/{id}/pin")
    public Mono<GetRelationship> postFeatureAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long featureeId = MastodonHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postFeatureAccount(requestAccountId, featureeId))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, featureeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/accounts/{id}/unpin")
    public Mono<GetRelationship> postRemoveFeatureAccount(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long featureeId = MastodonHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.postRemoveFeatureAccount(requestAccountId, featureeId))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, featureeId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/lists")
    public Mono<GetList> postList(WebSession session, @RequestBody(required = true) PostList params) {
        long requestAccountId = getMandatoryAccountId(session);
        if(params.title != null && params.title.length() > MastodonApiConfig.MAX_LIST_LENGTH) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "List title too long");
        return Mono.fromFuture(manager.postList(requestAccountId, params.title, params.replies_policy))
                   .flatMap(result -> Mono.fromFuture(manager.getLatestList(requestAccountId)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetList::new);
    }

    @PostMapping("/api/v1/lists/{id}/accounts")
    public Mono postListMember(WebSession session, @PathVariable("id") Long listId, @RequestBody(required = true) PostListMember params) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.hasListId(requestAccountId, listId)) // ensure the list exists and is owned by this user
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(hasListId -> {
                       if(params.account_ids.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                       List<Mono<Boolean>> futures = new ArrayList<>();
                       for(String memberId : params.account_ids) futures.add(Mono.fromFuture(manager.postListMember(listId, MastodonHelpers.parseAccountId(memberId))));
                       return Mono.zip(futures, results -> new HashMap());
                   });
    }

    @PostMapping("/api/v1/conversations/{id}/read")
    public Mono<GetConversation> postConversation(WebSession session, @PathVariable("id") Long conversationId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postConversation(requestAccountId, conversationId, false))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetConversation::new);
    }

    @PostMapping("/api/v1/featured_tags")
    public Mono<GetFeatureHashtag> postFeatureHashtag(WebSession session, @RequestBody PostFeatureHashtag params) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postFeatureHashtag(requestAccountId, params.name))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(featuredHashtagInfo -> new GetFeatureHashtag(featuredHashtagInfo.hashtag, featuredHashtagInfo.numStatuses, featuredHashtagInfo.timestamp));
    }

    @PostMapping("/api/v1/accounts/{id}/note")
    public Mono<GetRelationship> postNote(WebSession session, @PathVariable("id") String id, @RequestBody PostNote params) {
        long requestAccountId = getMandatoryAccountId(session);
        long targetId = MastodonHelpers.parseAccountId(id);
        if (params.comment !=null && params.comment.length() > MastodonApiConfig.MAX_NOTE_LENGTH) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Note too long");
        return Mono.fromFuture(manager.postNote(requestAccountId, targetId, params.comment))
                   .flatMap(res -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, targetId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @GetMapping("/api/v1/featured_tags/suggestions")
    public Mono<List<GetTag>> getSuggestedFeaturedHashtags(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getSuggestedFeaturedHashtags(requestAccountId)).map(MastodonApiHelpers::createGetTags);
    }

    @GetMapping("/api/v1/trends")
    public Mono<List<GetTag>> getTrendingTagsDeprecated(@RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {
        return getTrendingTags(limit, offset);
    }

    @GetMapping("/api/v1/trends/tags")
    public Mono<List<GetTag>> getTrendingTags(@RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {
        return Mono.fromFuture(manager.getTrendingHashtags(limit, offset)).map(MastodonApiHelpers::createGetTags);
    }

    @GetMapping("/api/v1/trends/links")
    public Mono<List<GetPreviewCard>> getTrendingLinks(@RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {
        return Mono.fromFuture(manager.getTrendingLinks(limit, offset)).map(MastodonApiHelpers::createGetPreviewCards);
    }

    @GetMapping("/api/v1/trends/statuses")
    public Mono<List<GetStatus>> getTrendingStatuses(WebSession session, @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        return Mono.fromFuture(manager.getTrendingStatuses(requestAccountId, limit, offset)).map(MastodonApiHelpers::createGetStatuses);
    }

    @GetMapping("/api/v1/tags/{id}")
    public Mono<GetTag> getTag(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        return Mono.fromFuture(manager.getHashtagStats(id))
                   .flatMap(stats ->
                       (requestAccountId == null ? Mono.just(false)
                                                 : Mono.fromFuture(manager.isFollowingHashtag(requestAccountId, id)))
                        .map(isFollowing -> MastodonApiHelpers.createGetTag(id, stats, isFollowing)));
    }

    @PostMapping("/api/v1/tags/{id}/follow")
    public Mono<GetTag> postFollowTag(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postFollowHashtag(requestAccountId, id)).flatMap(res -> this.getTag(session, id));
    }

    @PostMapping("/api/v1/tags/{id}/unfollow")
    public Mono<GetTag> postUnfollowTag(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postRemoveFollowHashtag(requestAccountId, id)).flatMap(res -> this.getTag(session, id));
    }

    @PostMapping("/api/v1/statuses/{id}/favourite")
    public Mono<GetStatus> postFavoriteStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        String requestAccountName = (String) session.getAttributes().get("accountName");
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, statusPointer.authorId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(accountWithIdPair -> {
                       AccountWithId requester = accountWithIdPair.getKey();
                       AccountWithId author = accountWithIdPair.getValue();
                       if (author.account.content.isSetRemote()) {
                           return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                      .flatMap(statusQueryResult -> {
                                          if (!statusQueryResult.result.status.isSetRemoteUrl()) throw new RuntimeException("No remote URL for status: " + id);
                                          String sourceUrl = String.format("%s/users/%s", MastodonConfig.API_URL, requestAccountName);
                                          String targetUrl = statusQueryResult.result.status.remoteUrl;
                                          return Mono.fromFuture(MastodonWebHelpers.buildRequest(requester.account, author.account.content.getRemote().sharedInboxUrl, new PostActivityPubInbox("Like", sourceUrl, targetUrl)))
                                                     .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
                                      });
                       } else return Mono.just(true);
                   })
                   .flatMap(result -> Mono.fromFuture(manager.postFavoriteStatus(requestAccountId, statusPointer)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/statuses/{id}/unfavourite")
    public Mono<GetStatus> postRemoveFavoriteStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        String requestAccountName = (String) session.getAttributes().get("accountName");
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, statusPointer.authorId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(accountWithIdPair -> {
                       AccountWithId requester = accountWithIdPair.getKey();
                       AccountWithId author = accountWithIdPair.getValue();
                       if (author.account.content.isSetRemote()) {
                           return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                                      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                      .flatMap(statusQueryResult -> {
                                          if (!statusQueryResult.result.status.isSetRemoteUrl()) throw new RuntimeException("No remote URL for status: " + id);
                                          String sourceUrl = String.format("%s/users/%s", MastodonConfig.API_URL, requestAccountName);
                                          String targetUrl = statusQueryResult.result.status.remoteUrl;
                                          return Mono.fromFuture(MastodonWebHelpers.buildRequest(requester.account, author.account.content.getRemote().sharedInboxUrl, new PostActivityPubInbox("Undo", sourceUrl, new PostActivityPubInbox("Like", sourceUrl, targetUrl))))
                                                     .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
                                      });
                       } else return Mono.just(true);
                   })
                   .flatMap(result -> Mono.fromFuture(manager.postRemoveFavoriteStatus(requestAccountId, statusPointer)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/statuses/{id}/reblog")
    public Mono<GetStatus> postBoostStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postBoostStatus(requestAccountId, statusPointer, null))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/statuses/{id}/unreblog")
    public Mono<GetStatus> postRemoveBoostStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postRemoveBoostStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/statuses/{id}/bookmark")
    public Mono<GetStatus> postBookmarkStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postBookmarkStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/statuses/{id}/unbookmark")
    public Mono<GetStatus> postRemoveBookmarkStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postRemoveBookmarkStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/statuses/{id}/mute")
    public Mono<GetStatus> postMuteStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postMuteStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/statuses/{id}/unmute")
    public Mono<GetStatus> postRemoveMuteStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.postRemoveMuteStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/statuses/{id}/pin")
    public Mono<GetStatus> postPinStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        return Mono.fromFuture(manager.postPinStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/statuses/{id}/unpin")
    public Mono<GetStatus> postRemovePinStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        return Mono.fromFuture(manager.postRemovePinStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY)))
                   .map(GetStatus::new);
    }

    @PostMapping("/api/v1/polls/{id}/votes")
    public Mono<GetPoll> postPollVote(WebSession session, @PathVariable("id") String id, @RequestBody(required = true) PostPollVote params) {
        long requestAccountId = getMandatoryAccountId(session);
        String requestAccountName = (String) session.getAttributes().get("accountName");
        Set<Integer> choices = params.choices.stream().map(Integer::parseInt).collect(Collectors.toSet());
        if (choices.size() == 0 || choices.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return this.getPoll(session, id)
                   .flatMap(poll -> {
                       if (!poll.multiple && choices.size() > 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only vote for one thing in non-multi-choice poll");
                       else if (poll.expired) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can't vote on an expired poll");

                       StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
                       return Mono.fromFuture(manager.getAccountWithIdPair(requestAccountId, statusPointer.authorId))
                                  .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                  .flatMap(accountWithIdPair -> {
                                      AccountWithId requester = accountWithIdPair.getKey();
                                      AccountWithId author = accountWithIdPair.getValue();
                                      if (author.account.content.isSetRemote()) {
                                          return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                                                     .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                                     .flatMap(statusQueryResult -> {
                                                         if (!statusQueryResult.result.status.isSetRemoteUrl()) {
                                                             throw new RuntimeException("No remote URL for status: " + id);
                                                         }
                                                         Set<String> choiceNames = choices.stream()
                                                                                          .map(choice -> poll.options.get(choice).title)
                                                                                          .collect(Collectors.toSet());
                                                         String sourceUrl = String.format("%s/users/%s", MastodonConfig.API_URL, requestAccountName);
                                                         String targetUrl = statusQueryResult.result.status.remoteUrl;
                                                         List<Mono<Boolean>> futures = new ArrayList<>();
                                                         for (String choiceName : choiceNames) futures.add(Mono.fromFuture(MastodonWebHelpers.buildRequest(requester.account, author.account.content.getRemote().sharedInboxUrl, new PostActivityPubInbox("Create", sourceUrl, new PostActivityPubVote(choiceName, sourceUrl, targetUrl, author.account.content.getRemote().mainUrl)))));
                                                         return Mono.zip(futures, result -> true)
                                                                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)));
                                                     });
                                      }
                                      else return Mono.just(true);
                                  })
                                  .flatMap(result -> Mono.fromFuture(manager.postPollVote(requestAccountId, statusPointer, choices)));
                   })
                   .flatMap(result -> this.getPoll(session, id));
    }

    @PostMapping("/api/v2/media")
    public Mono<GetAttachment> postMedia(WebSession session, @RequestPart("file") FilePart file) throws IOException {
        long requestAccountId = getMandatoryAccountId(session);
        // determine the file type
        String ext = FilenameUtils.getExtension(file.filename()).toLowerCase();
        final String kind;
        if (MastodonApiConfig.IMAGE_EXTS.contains(ext)) kind = "image";
        else if (MastodonApiConfig.VIDEO_EXTS.contains(ext)) kind = "video";
        else throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unrecognized file type");
        // transfer to static file dir
        File destDir = new File(MastodonApiConfig.STATIC_FILE_DIR, requestAccountId+"");
        destDir.mkdirs();
        String uuid = UUID.randomUUID().toString();
        File destFile = new File(destDir, String.format("%s.%s", uuid, ext));
        return file.transferTo(destFile)
                   .then(Mono.just(true))
                   .flatMap(res -> {
                       // validate
                       if (!MastodonApiHelpers.isValidFile(kind, destFile)) {
                           destFile.delete();
                           throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to validate file");
                       }
                       String path = String.format("%s/%s.%s", requestAccountId, uuid, ext);
                       AttachmentWithId attachmentWithId = new AttachmentWithId(uuid, new Attachment(MastodonApiHelpers.createAttachmentKind(kind), path, ""));
                       // upload to s3 if enabled
                       if (MastodonApiConfig.S3_OPTIONS != null) {
                           return Mono.fromFuture(MastodonApiHelpers.uploadToS3(MastodonApiConfig.S3_OPTIONS.bucketName, path, destFile))
                                      .map(resp -> {
                                          destFile.delete();
                                          if (resp.sdkHttpResponse().isSuccessful()) return attachmentWithId;
                                          else throw new RuntimeException(resp.sdkHttpResponse().statusText().orElse("Failed to connect to S3"));
                                      });
                       } else return Mono.just(attachmentWithId);
                   })
                   .flatMap(attachmentWithId -> Mono.fromFuture(manager.postAttachment(attachmentWithId)))
                   .map(GetAttachment::new);
    }

    @PostMapping("/api/v1/notifications/clear")
    public Mono dismissAllNotifications(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.dismissNotification(requestAccountId, null))
                   .map(res -> new HashMap());
    }

    @PostMapping("/api/v1/notifications/{id}/dismiss")
    public Mono dismissNotification(WebSession session, @PathVariable("id") String notificationId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.dismissNotification(requestAccountId, MastodonHelpers.parseNotificationId(notificationId)))
                   .map(res -> new HashMap());
    }

    @PostMapping("/api/v1/reports")
    public Mono<GetReport> postReport(WebSession session, @RequestBody(required = false) PostReport params) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAccountWithId(MastodonHelpers.parseAccountId(params.account_id)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(accountWithId -> {
                       LoggerFactory.getLogger(MastodonApiController.class).info("Report from account {}:\n{}", requestAccountId, params);
                       return MastodonApiHelpers.createGetReport(params, accountWithId);
                   });
    }

    @PutMapping("/api/v1/lists/{id}")
    public Mono<GetList> putList(WebSession session, @PathVariable("id") Long listId, @RequestBody(required = true) PutList params) {
        long requestAccountId = getMandatoryAccountId(session);
        if (params.title != null && params.title.length() > MastodonApiConfig.MAX_LIST_LENGTH) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "List title too long");
        return Mono.fromFuture(manager.hasListId(requestAccountId, listId)) // ensure the list exists and is owned by this user
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   // update the list
                   .flatMap(hasListId -> Mono.fromFuture(manager.putList(listId, requestAccountId, params.title, params.replies_policy)))
                   // get the list
                   .flatMap(result -> Mono.fromFuture(manager.getList(listId)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetList::new);
    }

    @PutMapping(value = "/api/v1/statuses/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetStatus> putStatus(WebSession session, @PathVariable("id") String id, @RequestBody(required = true) PutStatus params) {
        long requestAccountId = getMandatoryAccountId(session);
        validateStatus(params.status, params.spoiler_text, params.language, params.poll);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Mono.fromFuture(manager.putStatus(statusPointer, params))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @PutMapping(value = "/api/v1/statuses/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<GetStatus> putStatus(
            WebSession session,
            @PathVariable("id") String id,
            @RequestPart(value = "status", required = false) String status,
            @RequestPart(value = "sensitive", required = false) String sensitive,
            @RequestPart(value = "spoiler_text", required = false) String spoiler_text,
            @RequestPart(value = "language", required = false) String language,
            @RequestPart(value = "media_ids[]", required = false) List<Part> media_ids
    ) {
        List<Mono<DataBuffer>> mediaContent = media_ids.stream().map(part -> part.content().single()).collect(Collectors.toList());
        return (mediaContent.size() > 0 ? Mono.zip(mediaContent, res -> Arrays.stream(res).map(b -> ((DataBuffer) b).toString(Charset.defaultCharset())).collect(Collectors.toList())) : Mono.just(new ArrayList<>()))
                .flatMap(mediaContentResults -> {
                    PutStatus putStatus = new PutStatus(status);
                    putStatus.spoiler_text = spoiler_text;
                    putStatus.language = language;
                    putStatus.sensitive = sensitive != null ? Boolean.parseBoolean(sensitive) : null;
                    putStatus.media_ids = (List<String>) mediaContentResults;
                    return this.putStatus(session, id, putStatus);
                });
    }

    @PutMapping(value = "/api/v1/media/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetAttachment> putMedia(WebSession session, @PathVariable("id") String attachmentId, @RequestBody(required = true) PutAttachment params) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAttachment(attachmentId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(attachmentWithId -> {
                       if (params.description != null) attachmentWithId.attachment.description = params.description;
                       return Mono.fromFuture(manager.postAttachment(attachmentWithId));
                   })
                   .map(GetAttachment::new);
    }

    @PutMapping(value = "/api/v1/media/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<GetAttachment> putMedia(
            WebSession session,
            @PathVariable("id") String attachmentId,
            @RequestPart(value = "description", required = false) String description
    ) {
        PutAttachment putAttachment = new PutAttachment();
        putAttachment.description = description;
        return this.putMedia(session, attachmentId, putAttachment);
    }

    @DeleteMapping("/api/v1/statuses/{id}")
    public Mono<GetStatus> deleteStatus(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        if (statusPointer.authorId != requestAccountId) return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(status -> Mono.zip(Mono.just(status), Mono.fromFuture(manager.deleteStatus(requestAccountId, statusPointer.statusId))))
                   .map(Tuple2::getT1)
                   .map(GetStatus::new);
    }

    @DeleteMapping("/api/v1/lists/{id}/accounts")
    public Mono deleteListMember(WebSession session, @PathVariable("id") Long listId, @RequestParam(required = true, value = "account_ids[]") List<String> account_ids) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.hasListId(requestAccountId, listId)) // ensure the list exists and is owned by this user
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   // add the members
                   .flatMap(hasListId -> {
                       if (account_ids.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                       List<Mono<Boolean>> futures = new ArrayList<>();
                       for (String memberId : account_ids) futures.add(Mono.fromFuture(manager.deleteListMember(listId, MastodonHelpers.parseAccountId(memberId))));
                       return Mono.zip(futures, results -> new HashMap());
                   });
    }

    @DeleteMapping("/api/v1/lists/{id}")
    public Mono deleteList(WebSession session, @PathVariable("id") Long listId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.hasListId(requestAccountId, listId)) // ensure the list exists and is owned by this user
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(result -> Mono.fromFuture(manager.deleteList(listId)))
                   .map(result -> new HashMap());
    }

    @DeleteMapping("/api/v1/conversations/{id}")
    public Mono deleteConversation(WebSession session, @PathVariable("id") Long conversationId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.deleteConversation(requestAccountId, conversationId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(result -> new HashMap());
    }

    @DeleteMapping("/api/v1/featured_tags/{id}")
    public Mono deleteFeatureHashtag(WebSession session, @PathVariable("id") String featureHashtag) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.postRemoveFeatureHashtag(requestAccountId, featureHashtag))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(result -> new HashMap());
    }

    private static class AccountAttachment {
        public AttachmentWithId attachmentWithId;
        public File file;
        public Part part;
    }

    @PatchMapping(value = "/api/v1/accounts/update_credentials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<GetAccount> patchAccountUpdateCredentials(
            WebSession session,
            @RequestPart(value = "display_name", required = false) String display_name,
            @RequestPart(value = "note", required = false) String note,
            @RequestPart(value = "avatar", required = false) Part avatar,
            @RequestPart(value = "header", required = false) Part header,
            @RequestPart(value = "locked", required = false) String locked,
            @RequestPart(value = "bot", required = false) String bot,
            @RequestPart(value = "discoverable", required = false) String discoverable,
            @RequestPart(value = "fields_attributes[0][name]", required = false) String field0_name,
            @RequestPart(value = "fields_attributes[0][value]", required = false) String field0_value,
            @RequestPart(value = "fields_attributes[1][name]", required = false) String field1_name,
            @RequestPart(value = "fields_attributes[1][value]", required = false) String field1_value,
            @RequestPart(value = "fields_attributes[2][name]", required = false) String field2_name,
            @RequestPart(value = "fields_attributes[2][value]", required = false) String field2_value,
            @RequestPart(value = "fields_attributes[3][name]", required = false) String field3_name,
            @RequestPart(value = "fields_attributes[3][value]", required = false) String field3_value,
            @RequestPart(value = "source[privacy]", required = false) String privacy,
            @RequestPart(value = "source[sensitive]", required = false) String sensitive,
            @RequestPart(value = "source[language]", required = false) String language) throws JsonProcessingException {
        long requestAccountId = getMandatoryAccountId(session);
        // parse params
        Boolean isLocked = locked != null ? Boolean.parseBoolean(locked) : null;
        Boolean isBot = bot != null ? Boolean.parseBoolean(bot) : null;
        Boolean isDiscoverable = discoverable != null ? Boolean.parseBoolean(discoverable) : null;
        List<KeyValuePair> fields = new ArrayList<>();
        {
            if (field0_name != null && field0_value != null)
                fields.add(new KeyValuePair(MastodonApiHelpers.sanitizeField(field0_name), MastodonApiHelpers.sanitizeField(field0_value)));
            if (field1_name != null && field1_value != null)
                fields.add(new KeyValuePair(MastodonApiHelpers.sanitizeField(field1_name), MastodonApiHelpers.sanitizeField(field1_value)));
            if (field2_name != null && field2_value != null)
                fields.add(new KeyValuePair(MastodonApiHelpers.sanitizeField(field2_name), MastodonApiHelpers.sanitizeField(field2_value)));
            if (field3_name != null && field3_value != null)
                fields.add(new KeyValuePair(MastodonApiHelpers.sanitizeField(field3_name), MastodonApiHelpers.sanitizeField(field3_value)));
        }
        Map<String, String> newPrefs = new HashMap<>();
        {
            ObjectMapper objectMapper = new ObjectMapper();
            if (privacy != null) {
                Set<String> options = new HashSet<>(Arrays.asList("public", "unlisted", "private"));
                if (!options.contains(privacy)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                newPrefs.put("posting:default:visibility", objectMapper.writeValueAsString(privacy));
            }
            if (sensitive != null) newPrefs.put("posting:default:sensitive", objectMapper.writeValueAsString(Boolean.parseBoolean(sensitive)));
            if (language != null) {
                if (language.length() > 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST); // ISO 6391
                newPrefs.put("posting:default:language", objectMapper.writeValueAsString(language));
            }
        }
        // start the uploads
        List<Mono<Boolean>> uploads = new ArrayList<>();
        List<AccountAttachment> accountAttachments = new ArrayList<>();
        for (Part part : Arrays.asList(avatar, header)) {
            if (part == null) continue;
            // an empty upload is interpreted by Spring as a FormFieldPart
            // instead of a FilePart. in that case, we just make it blank.
            else if (part instanceof FormFieldPart) {
                AccountAttachment attachment = new AccountAttachment();
                attachment.attachmentWithId = new AttachmentWithId("", new Attachment(AttachmentKind.Image, "", ""));
                attachment.part = part;
                accountAttachments.add(attachment);
            } else {
                FilePart filePart = (FilePart) part;
                // determine the file type
                String ext = FilenameUtils.getExtension(filePart.filename()).toLowerCase();
                if (!MastodonApiConfig.IMAGE_EXTS.contains(ext)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unrecognized file type");
                // transfer to static file dir
                File destDir = new File(MastodonApiConfig.STATIC_FILE_DIR, requestAccountId + "");
                destDir.mkdirs();
                String uuid = UUID.randomUUID().toString();
                File destFile = new File(destDir, String.format("%s.%s", uuid, ext));
                uploads.add(filePart.transferTo(destFile).then(Mono.just(true)));
                AccountAttachment attachment = new AccountAttachment();
                String path = String.format("%s/%s.%s", requestAccountId, uuid, ext);
                attachment.attachmentWithId = new AttachmentWithId(uuid, new Attachment(AttachmentKind.Image, path, ""));
                attachment.file = destFile;
                attachment.part = filePart;
                accountAttachments.add(attachment);
            }
        }
        // ensure there's at least one mono so zip works correctly
        if (uploads.size() == 0) uploads.add(Mono.just(true));
        return Mono.zip(uploads, results -> true)
                   // upload to s3 if enabled
                   .flatMap(result -> {
                       if (MastodonApiConfig.S3_OPTIONS != null) {
                           List<Mono<Boolean>> s3Uploads = new ArrayList<>();
                           for (AccountAttachment accountAttachment : accountAttachments) {
                               if (accountAttachment.file == null) continue;
                               String path = accountAttachment.attachmentWithId.attachment.path;
                               s3Uploads.add(
                                       Mono.fromFuture(MastodonApiHelpers.uploadToS3(MastodonApiConfig.S3_OPTIONS.bucketName, path, accountAttachment.file))
                                           .map(resp -> {
                                               accountAttachment.file.delete();
                                               return resp.sdkHttpResponse().isSuccessful();
                                           }));
                           }
                           // ensure there's at least one mono so zip works correctly
                           if (s3Uploads.size() > 0) return Mono.zip(s3Uploads, results -> Arrays.stream(results).allMatch(success -> (boolean) success));
                       }
                       return Mono.just(true);
                   })
                   // check upload success and get account
                   .flatMap(success -> {
                       if (!success) throw new RuntimeException("Failed to connect to S3");
                       return Mono.fromFuture(manager.getAccountWithId(requestAccountId));
                   })
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   // update the account
                   .flatMap(accountWithId -> {
                       List<EditAccountField> edits = new ArrayList<>();
                       if (display_name != null) edits.add(EditAccountField.displayName(MastodonApiHelpers.sanitize(display_name, MastodonApiConfig.MAX_DISPLAY_NAME_LENGTH)));
                       if (note != null) edits.add(EditAccountField.bio(MastodonApiHelpers.sanitize(note, MastodonApiConfig.MAX_BIO_LENGTH)));
                       if (isLocked != null) edits.add(EditAccountField.locked(isLocked));
                       if (isBot != null) edits.add(EditAccountField.bot(isBot));
                       if (isDiscoverable != null) edits.add(EditAccountField.discoverable(isDiscoverable));
                       if (fields.size() > 0) edits.add(EditAccountField.fields(fields));
                       if (newPrefs.size() > 0) {
                           Map<String, String> prefs = new HashMap<>();
                           if (accountWithId.account.preferences != null) prefs.putAll(accountWithId.account.preferences);
                           prefs.putAll(newPrefs);
                           edits.add(EditAccountField.preferences(prefs));
                       }
                       for (AccountAttachment accountAttachment : accountAttachments) {
                           if ("header".equals(accountAttachment.part.name())) edits.add(EditAccountField.header(accountAttachment.attachmentWithId));
                           else if ("avatar".equals(accountAttachment.part.name())) edits.add(EditAccountField.avatar(accountAttachment.attachmentWithId));
                       }
                       return Mono.fromFuture(manager.postEditAccount(requestAccountId, edits));
                   })
                   // query and return the updated account
                   .flatMap(result -> Mono.fromFuture(manager.getAccountWithId(requestAccountId)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   // federate account update to remote servers
                   .flatMap(accountWithId ->
                           Mono.fromFuture(manager.getRemoteServerFollowers(requestAccountId))
                               .flatMap(followers -> {
                                   if (followers.size() > 0) {
                                       String sourceUrl = String.format("%s/users/%s", MastodonConfig.API_URL, accountWithId.account.name);
                                       List<Mono<Boolean>> futures = new ArrayList<>();
                                       for (String follower : followers) {
                                           futures.add(Mono.fromFuture(MastodonWebHelpers.buildRequest(accountWithId.account, follower, new PostActivityPubInbox("Update", sourceUrl, MastodonApiHelpers.createProfile(accountWithId)))
                                                                                         .exceptionally(t -> true)));
                                       }
                                       return Mono.zip(futures, results -> accountWithId);
                                   } else {
                                       return Mono.just(accountWithId);
                                   }
                               }))
                   // return account
                   .map(GetAccount::new);
    }

    @PatchMapping(value = "/api/v1/accounts/update_credentials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GetAccount> patchAccountUpdateCredentials(WebSession session, @RequestBody(required = false) PatchUpdateCredentials params) throws JsonProcessingException {
        return patchAccountUpdateCredentials(session, params.display_name, params.note, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @GetMapping("/api/v1/preferences")
    public Mono<HashMap<String, JsonNode>> getPreferences(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                .map(accountWithId -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    HashMap<String, String> encodedPrefs = new HashMap<>();
                    // set the defaults
                    try {
                        encodedPrefs.put("posting:default:visibility", objectMapper.writeValueAsString("public"));
                        encodedPrefs.put("posting:default:sensitive", objectMapper.writeValueAsString(false));
                        encodedPrefs.put("posting:default:language", objectMapper.writeValueAsString("en"));
                        encodedPrefs.put("reading:expand:media", objectMapper.writeValueAsString("default"));
                        encodedPrefs.put("reading:expand:spoilers", objectMapper.writeValueAsString(false));
                        // merge the prefs from the account
                        if (accountWithId.account.preferences != null) encodedPrefs.putAll(accountWithId.account.preferences);
                        // decode the prefs
                        HashMap<String, JsonNode> decodedPrefs = new HashMap<>();
                        for (Map.Entry<String, String> entry : encodedPrefs.entrySet()) {
                            // the pref value is stored as serialized JSON
                            decodedPrefs.put(entry.getKey(), objectMapper.readValue(entry.getValue(), JsonNode.class));
                        }
                        return decodedPrefs;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @GetMapping("/api/v1/instance")
    public Mono<GetInstanceV1> getInstanceV1() {
        return Mono.just(new GetInstanceV1());
    }

    @GetMapping("/api/v2/instance")
    public Mono<GetInstance> getInstanceV2() {
        return Mono.just(new GetInstance());
    }

    @GetMapping("/api/v1/instance/rules")
    public Mono<List<GetRule>> getInstanceRules() {
        return Mono.just(new ArrayList<>());
    }

    @GetMapping("/api/v1/instance/activity")
    public Mono<List<GetActivity>> getInstanceActivity() {
        return Mono.just(new ArrayList<>());
    }

    @GetMapping("/api/v1/instance/domain_blocks")
    public Mono<List<GetDomainBlock>> getInstanceDomainBlocks() {
        return Mono.just(new ArrayList<>());
    }

    @GetMapping("/api/v1/instance/peers")
    public Mono<List<String>> getInstancePeers() {
        return Mono.just(new ArrayList<>());
    }

    @GetMapping("/api/v1/accounts/verify_credentials")
    public Mono<GetAccount> getAccountVerifyCredentials(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetAccount::new);
    }

    @GetMapping("/api/v1/accounts/{id}")
    public Mono<GetAccount> getAccount(@PathVariable("id") String accountId) {
        return Mono.fromFuture(manager.getAccountWithId(MastodonHelpers.parseAccountId(accountId)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetAccount::new);
    }

    @GetMapping("/api/v1/accounts/{id}/statuses")
    public Mono<List<GetStatus>> getAccountStatuses(WebSession session, ServerWebExchange exchange,
                                                    @PathVariable("id") String id,
                                                    @RequestParam(required = false) String max_id,
                                                    @RequestParam(required = false) Integer limit,
                                                    @RequestParam(required = false) Boolean only_media,
                                                    @RequestParam(required = false) Boolean exclude_replies,
                                                    @RequestParam(required = false) Boolean exclude_reblogs,
                                                    @RequestParam(required = false) Boolean pinned,
                                                    @RequestParam(required = false) String tagged) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(max_id);
        long timelineAccountId = MastodonHelpers.parseAccountId(id);
        final CompletableFuture<StatusQueryResults> future;
        if (pinned != null && pinned) future = manager.getPinnedStatuses(requestAccountId, timelineAccountId);
        else if (only_media != null && only_media) future = manager.getAttachmentStatuses(requestAccountId, timelineAccountId, statusPointer, limit);
        else if (tagged != null) future = manager.getTaggedStatuses(requestAccountId, timelineAccountId, tagged, statusPointer, limit);
        else future = manager.getAccountTimeline(requestAccountId, timelineAccountId, statusPointer, limit, exclude_replies == null || !exclude_replies, exclude_reblogs == null || !exclude_reblogs);
        return Mono.fromFuture(future)
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(statusQueryResults -> {
                       MastodonApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                       return MastodonApiHelpers.createGetStatuses(statusQueryResults);
                   });
    }

    @GetMapping("/api/v1/statuses/{id}")
    public Mono<GetStatus> getStatus(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatus::new);
    }

    @GetMapping("/api/v1/statuses/{id}/context")
    public Mono<GetContext> getContext(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.zip(Mono.fromFuture(manager.getAncestors(requestAccountId, statusPointer)),
                        Mono.fromFuture(manager.getDescendants(requestAccountId, statusPointer)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(result -> new GetContext(
                           MastodonApiHelpers.createGetStatuses(result.getT1()),
                           MastodonApiHelpers.createGetStatuses(result.getT2())
                   ));
    }

    @GetMapping("/api/v1/statuses/{id}/reblogged_by")
    public Mono<List<GetAccount>> getStatusBoosters(ServerWebExchange exchange, WebSession session, @PathVariable("id") String id, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getStatusBoosters(requestAccountId, statusPointer.authorId, statusPointer.statusId, MastodonHelpers.parseAccountId(max_id), limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/statuses/{id}/favourited_by")
    public Mono<List<GetAccount>> getStatusFavoriters(ServerWebExchange exchange, WebSession session, @PathVariable("id") String id, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getStatusFavoriters(requestAccountId, statusPointer.authorId, statusPointer.statusId, MastodonHelpers.parseAccountId(max_id), limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/statuses/{id}/history")
    public Mono<List<GetStatusEdit>> getStatusHistory(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
                   // make sure requester is allowed to see this status
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   // get the author's account and the status history
                   .flatMap(statusQueryResult -> Mono.zip(
                           Mono.fromFuture(manager.getAccountWithId(statusQueryResult.result.status.author.accountId)),
                           Mono.fromFuture(manager.getStatusHistory(statusQueryResult.result.status.author.accountId, statusPointer.statusId))
                   ))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(result -> MastodonApiHelpers.createGetStatusEdits(result.getT1(), result.getT2()));
    }

    @GetMapping("/api/v1/statuses/{id}/source")
    public Mono<GetStatusSource> getStatusSource(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetStatusSource::new);
    }

    @GetMapping("/api/v1/accounts/{id}/following")
    public Mono<List<GetAccount>> getAccountFollowees(ServerWebExchange exchange, @PathVariable("id") String accountId, @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
        return Mono.fromFuture(manager.getAccountFollowees(MastodonHelpers.parseAccountId(accountId), max_id, limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/accounts/{id}/followers")
    public Mono<List<GetAccount>> getAccountFollowers(ServerWebExchange exchange, @PathVariable("id") String accountId, @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
        return Mono.fromFuture(manager.getAccountFollowers(MastodonHelpers.parseAccountId(accountId), max_id, limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/accounts/familiar_followers")
    public Mono<List<GetFamiliarFollowers>> getFamiliarFollowers(WebSession session, @RequestParam(value = "id[]", required = false) List<String> idList, @RequestParam(value = "id", required = false) String id) {
        List<String> ids = new ArrayList<>();
        if (idList != null) ids.addAll(idList);
        if (id != null) ids.add(id);
        long requestAccountId = getMandatoryAccountId(session);
        if (ids.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        List<Mono<List>> familiarFollowerIds = new ArrayList<>();
        for (String targetId: ids) {
          long parsed = MastodonHelpers.parseAccountId(targetId);
          if(parsed != requestAccountId) familiarFollowerIds.add(Mono.fromFuture(manager.getFamiliarFollowers(requestAccountId, parsed)));
        }
        return Mono.zip(familiarFollowerIds, results -> {
            List<GetFamiliarFollowers> familiarFollowers = new ArrayList<>();
            for (int i = 0; i < results.length; i++) {
                familiarFollowers.add(new GetFamiliarFollowers(ids.get(i), MastodonApiHelpers.createGetAccounts((List<AccountWithId>) results[i])));
            }
            return familiarFollowers;
        });
    }

    @GetMapping("/api/v1/accounts/search")
    public Mono<List<GetAccount>> getAccountSearch(
            ServerWebExchange exchange,
            WebSession session,
            @RequestParam(required = true) String q,
            @RequestParam(required = false) Boolean resolve,
            @RequestParam(required = false) Boolean following,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @RequestParam(required = false) Long start_next_id,
            @RequestParam(required = false) String start_term
    ) throws MalformedURLException {
        long requestAccountId = getMandatoryAccountId(session);
        boolean canResolve = resolve != null && resolve;
        List<String> terms = Arrays.asList(q.toLowerCase().trim().split("\\s+"));
        if (canResolve && terms.size() == 1) {
            String url = null;
            if (MastodonApiHelpers.isValidURL(terms.get(0))) url = terms.get(0);
            else url = MastodonApiHelpers.remoteMentionToUrl(terms.get(0));

            if (url != null) {
                return this.resolveUrl(url)
                           .map(accountWithId -> MastodonApiHelpers.createGetAccounts(Arrays.asList(accountWithId)));
            }
        }

        Map startParams = MastodonApiHelpers.createSearchParams(start_next_id, start_term);
        return Mono.fromFuture((offset == null || offset == 0L)
                               ? manager.getProfileSearch(requestAccountId, terms, startParams, limit, following != null && following)
                               : CompletableFuture.completedFuture(new MastodonApiManager.QueryResults<AccountWithId, Map>(new ArrayList<>(), true, null, null)))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/accounts/relationships")
    public Mono<List<GetRelationship>> getAccountRelationships(WebSession session, @RequestParam(value = "id[]", required = false) List<String> idList, @RequestParam(value = "id", required = false) String id) {
        List<String> ids = new ArrayList<>();
        if (idList != null) ids.addAll(idList);
        if (id != null) ids.add(id);
        long requestAccountId = getMandatoryAccountId(session);
        if (ids.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        List<Mono<GetRelationship>> relationshipResults = new ArrayList<>();
        for (String targetIdStr : ids) {
            final long targetId;
            try { targetId = MastodonHelpers.parseAccountId(targetIdStr); }
            catch (RuntimeException e) { continue; }
            relationshipResults.add(
                    Mono.fromFuture(manager.getAccountRelationship(requestAccountId, targetId))
                        .map(result -> new GetRelationship(targetIdStr, result)));
        }
        return relationshipResults.size() > 0 ? Mono.zip(relationshipResults, results -> Arrays.stream(results).map(result -> (GetRelationship) result).collect(Collectors.toList())) : Mono.just(new ArrayList<>());
    }

    @GetMapping("/api/v1/accounts/{id}/lists")
    public Mono<List<GetList>> getMemberLists(WebSession session, @PathVariable("id") String memberId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getListsFromAuthor(requestAccountId, MastodonHelpers.parseAccountId(memberId)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(MastodonApiHelpers::createGetLists);
    }

    @GetMapping("/api/v1/accounts/{id}/featured_tags")
    public Mono<List<GetFeatureHashtag>> getAccountFeatureHashtags(@PathVariable("id") String accountId) {
        return Mono.fromFuture(manager.getFeaturedHashtags(MastodonHelpers.parseAccountId(accountId))).map(MastodonApiHelpers::createGetFeatureHashtags);
    }

    @GetMapping("/api/v1/accounts/lookup")
    public Mono<GetAccount> getAccountLookup(@RequestParam(required = false) String acct) {
        return Mono.fromFuture(manager.getAccountId(acct))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetAccount::new);
    }

    @GetMapping("/api/v1/timelines/home")
    public Mono<List<GetStatus>> getHomeTimeline(WebSession session, ServerWebExchange exchange, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getHomeTimeline(requestAccountId, statusPointer, limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(statusQueryResults -> {
                       MastodonApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                       return MastodonApiHelpers.createGetStatuses(statusQueryResults);
                   });
    }

    @GetMapping("/api/v1/timelines/direct")
    public Mono<List<GetStatus>> getDirectTimeline(WebSession session, ServerWebExchange exchange, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getDirectTimeline(requestAccountId, statusPointer, limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(statusQueryResults -> {
                       MastodonApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                       return MastodonApiHelpers.createGetStatuses(statusQueryResults);
                   });
    }

    @GetMapping("/api/v1/timelines/public")
    public Mono<List<GetStatus>> getPublicTimeline(WebSession session, ServerWebExchange exchange,
                                                   @RequestParam(required = false) String max_id,
                                                   @RequestParam(required = false) Integer limit,
                                                   @RequestParam(required = false) Boolean local,
                                                   @RequestParam(required = false) Boolean remote) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(max_id);
        boolean onlyLocal = local != null && local;
        boolean onlyRemote = remote != null && remote;
        final GlobalTimeline timeline;
        if (onlyLocal && !onlyRemote) timeline = GlobalTimeline.PublicLocal;
        else if (onlyRemote && !onlyLocal) timeline = GlobalTimeline.PublicRemote;
        else timeline = GlobalTimeline.Public;
        return Mono.fromFuture(manager.getGlobalTimeline(timeline, requestAccountId, statusPointer, limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(statusQueryResults -> {
                       MastodonApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                       return MastodonApiHelpers.createGetStatuses(statusQueryResults);
                   });
    }

    @GetMapping("/api/v1/timelines/tag/{hashtag}")
    public Mono<List<GetStatus>> getHashtagTimeline(WebSession session, ServerWebExchange exchange, @PathVariable("hashtag") String hashtag, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getHashtagTimeline(hashtag, requestAccountId, statusPointer, limit))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(statusQueryResults -> {
                       MastodonApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                       return MastodonApiHelpers.createGetStatuses(statusQueryResults);
                   });
    }

    @GetMapping("/api/v1/timelines/list/{id}")
    public Mono<List<GetStatus>> getListTimeline(WebSession session, ServerWebExchange exchange, @PathVariable("id") Long listId, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.hasListId(requestAccountId, listId)) // ensure the list exists and is owned by this user
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(hasListId -> Mono.fromFuture(manager.getListTimeline(listId, statusPointer, limit)))
                   .map(statusQueryResults -> {
                       MastodonApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                       return MastodonApiHelpers.createGetStatuses(statusQueryResults);
                   });
    }

    @GetMapping("/api/v1/lists")
    public Mono<List<GetList>> getLists(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getListsFromAuthor(requestAccountId, null))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(MastodonApiHelpers::createGetLists);
    }

    @GetMapping("/api/v1/lists/{id}")
    public Mono<GetList> getLists(WebSession session, @PathVariable("id") Long listId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.hasListId(requestAccountId, listId)) // ensure the list exists and is owned by this user
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(hasListId -> Mono.fromFuture(manager.getList(listId)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetList::new);
    }

    @GetMapping("/api/v1/lists/{id}/accounts")
    public Mono<List<GetAccount>> getListMembers(ServerWebExchange exchange, WebSession session, @PathVariable("id") Long listId, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.hasListId(requestAccountId, listId)) // ensure the list exists and is owned by this user
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(hasListId -> Mono.fromFuture(manager.getListMembers(listId, MastodonHelpers.parseAccountId(max_id), limit)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/conversations")
    public Mono<List<GetConversation>> getConversations(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getConversationTimeline(requestAccountId, max_id, limit))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetConversations(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/endorsements")
    public Mono<List<GetAccount>> getEndorsements(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getEndorsements(requestAccountId, MastodonHelpers.parseAccountId(max_id), limit))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/blocks")
    public Mono<List<GetAccount>> getBlocks(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getBlocks(requestAccountId, MastodonHelpers.parseAccountId(max_id), limit))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/mutes")
    public Mono<List<GetAccount>> getMutes(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getMutes(requestAccountId, MastodonHelpers.parseAccountId(max_id), limit))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/favourites")
    public Mono<List<GetStatus>> getFavorites(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getFavorites(requestAccountId, statusPointer, limit))
                   .map(statusQueryResults -> {
                       MastodonApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                       return MastodonApiHelpers.createGetStatuses(statusQueryResults);
                   });
    }

    @GetMapping("/api/v1/bookmarks")
    public Mono<List<GetStatus>> getBookmarks(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) String max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(max_id);
        return Mono.fromFuture(manager.getBookmarks(requestAccountId, statusPointer, limit))
                   .map(statusQueryResults -> {
                       MastodonApiHelpers.setStatusLinkHeader(exchange, statusQueryResults);
                       return MastodonApiHelpers.createGetStatuses(statusQueryResults);
                   });
    }

    @GetMapping("/api/v1/featured_tags")
    public Mono<List<GetFeatureHashtag>> getFeatureHashtags(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getFeaturedHashtags(requestAccountId)).map(MastodonApiHelpers::createGetFeatureHashtags);
    }

    @GetMapping("/api/v1/follow_requests")
    public Mono<List<GetAccount>> getFollowRequests(ServerWebExchange exchange, WebSession session, @RequestParam(required = false) Long max_id, @RequestParam(required = false) Integer limit) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getFollowRequests(requestAccountId, max_id, limit))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetAccounts(queryResults.results);
                   });
    }

    @PostMapping("/api/v1/follow_requests/{id}/authorize")
    public Mono<GetRelationship> acceptFollowRequest(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long requesterId = MastodonHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.acceptFollowRequest(requestAccountId, requesterId))
                   .filter(isRequestExists -> isRequestExists)
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, requesterId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @PostMapping("/api/v1/follow_requests/{id}/reject")
    public Mono<GetRelationship> rejectFollowRequest(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        long requesterId = MastodonHelpers.parseAccountId(id);
        return Mono.fromFuture(manager.rejectFollowRequest(requestAccountId, requesterId))
                   .filter(isRequestExists -> isRequestExists)
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(result -> Mono.fromFuture(manager.getAccountRelationship(requestAccountId, requesterId)))
                   .map(result -> new GetRelationship(id, result));
    }

    @GetMapping("/api/v1/markers")
    public Mono<Map<String, GetMarker>> getMarkers(WebSession session, @RequestParam(value = "timeline[]", required = true) List<String> timelines) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(accountWithId -> {
                       if (timelines.size() > QUERY_PARAM_ARRAY_SIZE_LIMIT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                       Map<String, Marker> markers = new HashMap<>();
                       if (accountWithId.account.markers != null) markers.putAll(accountWithId.account.markers);
                       return MastodonApiHelpers.createGetMarkers(markers);
                   });
    }

    @PostMapping("/api/v1/markers")
    public Mono<Map<String, GetMarker>> postMarkers(WebSession session, @RequestBody PostMarkers params) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getAccountWithId(requestAccountId))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(accountWithId -> {
                       // update map of markers
                       Map<String, Marker> markers = new HashMap<>();
                       if (accountWithId.account.markers != null) markers.putAll(accountWithId.account.markers);
                       if (params.home != null) {
                           Marker marker = new Marker(params.home.last_read_id, 0, System.currentTimeMillis());
                           if (markers.get("home") != null) marker.version = markers.get("home").version + 1;
                           markers.put("home", marker);
                       }
                       if (params.notifications != null) {
                           Marker marker = new Marker(params.notifications.last_read_id, 0, System.currentTimeMillis());
                           if (markers.get("notifications") != null) marker.version = markers.get("notifications").version + 1;
                           markers.put("notifications", marker);
                       }
                       // update account
                       List<EditAccountField> edits = new ArrayList<>();
                       edits.add(EditAccountField.markers(markers));
                       return Mono.fromFuture(manager.postEditAccount(requestAccountId, edits))
                                  .map(res -> MastodonApiHelpers.createGetMarkers(markers));
                   });
    }

    @GetMapping("/api/v1/custom_emojis")
    public Mono<List<GetCustomEmoji>> getCustomEmojis() {
        return Mono.just(new ArrayList<>());
    }

    @GetMapping("/api/v1/notifications")
    public Mono<List<GetNotification>> getNotifications(
            ServerWebExchange exchange,
            WebSession session,
            @RequestParam(required = false) String max_id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, value = "types[]") List<String> types,
            @RequestParam(required = false, value = "exclude_types[]") List<String> exclude_types
    ) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getNotificationsTimeline(requestAccountId, MastodonHelpers.parseNotificationId(max_id), limit, types, exclude_types))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(queryResults -> {
                       MastodonApiHelpers.setLinkHeader(exchange, queryResults);
                       return MastodonApiHelpers.createGetNotifications(queryResults.results);
                   });
    }

    @GetMapping("/api/v1/notifications/{id}")
    public Mono<GetNotification> getNotification(WebSession session, @PathVariable("id") String notificationId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getNotification(requestAccountId, MastodonHelpers.parseNotificationId(notificationId)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(GetNotification::new);
    }

    @GetMapping("/api/v2/search")
    public Mono<GetSearch> getSearch(
            WebSession session,
            ServerWebExchange exchange,
            @RequestParam(required = true) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean resolve,
            @RequestParam(required = false) Boolean following,
            @RequestParam(required = false) String account_id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @RequestParam(required = false) Long start_next_id,
            @RequestParam(required = false) String start_term
    ) throws MalformedURLException {
        long requestAccountId = getMandatoryAccountId(session);
        boolean canResolve = resolve != null && resolve;
        List<String> terms = Arrays.asList(q.toLowerCase().trim().split("\\s+"));
        if ((type == null || type.equals("accounts")) && canResolve && terms.size() == 1) {
            String url = null;
            if (MastodonApiHelpers.isValidURL(terms.get(0))) url = terms.get(0);
            else url = MastodonApiHelpers.remoteMentionToUrl(terms.get(0));

            if (url != null) {
                return this.resolveUrl(url)
                           .map(accountWithId -> new GetSearch(
                                   MastodonApiHelpers.createGetAccounts(Arrays.asList(accountWithId)),
                                   MastodonApiHelpers.createGetStatuses(new StatusQueryResults(new ArrayList<>(), new HashMap<>(), true, false)),
                                   MastodonApiHelpers.createGetTags(new HashMap<>())
                           ));
            }
        }

        Map startParams = MastodonApiHelpers.createSearchParams(start_next_id, start_term);
        return Mono.zip(Mono.fromFuture((type == null || type.equals("accounts")) && (offset == null || offset == 0L)
                                        ? manager.getProfileSearch(requestAccountId, terms, startParams, limit, following != null && following)
                                        : CompletableFuture.completedFuture(new MastodonApiManager.QueryResults<AccountWithId, Map>(new ArrayList<>(), true, null, null))),
                        Mono.fromFuture((type == null || type.equals("statuses")) && (offset == null || offset == 0L)
                                        ? manager.getStatusSearch(requestAccountId, MastodonHelpers.parseAccountId(account_id), terms, startParams, limit)
                                        : CompletableFuture.completedFuture(new MastodonApiManager.QueryResults<StatusQueryResult, Map>(new ArrayList<>(), true, null, null))),
                        Mono.fromFuture((type == null || type.equals("hashtags")) && (offset == null || offset == 0L)
                                        ? manager.getHashtagSearch(terms.get(0), startParams, limit)
                                        : CompletableFuture.completedFuture(new MastodonApiManager.QueryResults<SimpleEntry<String, ItemStats>, Map>(new ArrayList<>(), true, null, null))))
                   .map(results -> {
                       MastodonApiManager.QueryResults<AccountWithId, Map> accounts = results.getT1();
                       MastodonApiManager.QueryResults<StatusQueryResult, Map> statuses = results.getT2();
                       MastodonApiManager.QueryResults<SimpleEntry<String, ItemStats>, Map> hashtags = results.getT3();
                       if ("accounts".equals(type)) MastodonApiHelpers.setLinkHeader(exchange, accounts);
                       else if ("statuses".equals(type)) MastodonApiHelpers.setLinkHeader(exchange, statuses);
                       else if ("hashtags".equals(type)) MastodonApiHelpers.setLinkHeader(exchange, hashtags);
                       return new GetSearch(MastodonApiHelpers.createGetAccounts(accounts.results),
                                            MastodonApiHelpers.createGetStatuses(statuses.results),
                                            MastodonApiHelpers.createGetTags(hashtags.results));
                   });
    }

    @GetMapping("/api/v1/announcements")
    public Mono<List<GetAnnouncement>> getAnnouncements(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.just(new ArrayList<>());
    }

    @GetMapping("/api/v2/suggestions")
    public Mono<List<GetSuggestion>> getSuggestions(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getWhoToFollowSuggestions(requestAccountId))
                   .map(accountWithIds -> accountWithIds.stream().map(a -> new GetSuggestion("global", a)).collect(Collectors.toList()));
    }

    @DeleteMapping("/api/v1/suggestions/{id}")
    public Mono deleteSuggestion(WebSession session, @PathVariable("id") String id) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.removeFollowSuggestion(requestAccountId, MastodonHelpers.parseAccountId(id)))
                   .map(res -> new HashMap());
    }

    @GetMapping("/api/v1/directory")
    public Mono<List<GetAccount>> getDirectory(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Boolean local
    ) {
        HashSet<String> orders = new HashSet<>(Arrays.asList(null, "active", "new"));
        if (!orders.contains(order)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        boolean showAll = local == null || !local;
        boolean sortByActive = order == null || order.equals("active");
        return Mono.fromFuture(manager.getDirectory(showAll, sortByActive, limit, offset))
                   .map(MastodonApiHelpers::createGetAccounts);
    }

    @GetMapping("/api/v1/polls/{id}")
    public Mono<GetPoll> getPoll(WebSession session, @PathVariable("id") String id) {
        Long requestAccountId = (Long) session.getAttributes().get("accountId"); // allowed to be null
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(id);
        return Mono.fromFuture(manager.getStatus(requestAccountId, statusPointer))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(statusQueryResult -> {
                       StatusResult statusResult = statusQueryResult.result.status;
                       final PollContent pollContent;
                       if (statusResult.content.isSetNormal()) {
                           NormalStatusContent content = statusResult.content.getNormal();
                           if (!statusResult.isSetPollInfo() || !content.isSetPollContent()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                           pollContent = content.pollContent;
                       } else if (statusResult.content.isSetReply()) {
                           ReplyStatusContent content = statusResult.content.getReply();
                           if (!statusResult.isSetPollInfo() || !content.isSetPollContent()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                           pollContent = content.pollContent;
                       } else throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                       return new GetPoll(id, statusResult.pollInfo, pollContent);
                   });
    }

    // Filters

    @GetMapping("/api/v2/filters")
    public Mono<List<GetFilter>> getFilters(WebSession session) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getFilters(requestAccountId))
                   .map(filterList -> filterList.stream().map(GetFilter::new).collect(Collectors.toList()));
    }

    @GetMapping("/api/v2/filters/{id}")
    public Mono<GetFilter> getFilter(WebSession session, @PathVariable("id") Long filterId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getFilter(requestAccountId, filterId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetFilter::new);
    }

    @PostMapping("/api/v2/filters")
    public Mono<GetFilter> postFilter(WebSession session, @RequestBody PostFilterParams params) {
        long requestAccountId = getMandatoryAccountId(session);
        Filter filter = new Filter();
        filter.setAccountId(requestAccountId);
        filter.setTitle(params.title);
        filter.setAction(params.parseAction());
        filter.setContexts(params.parseContexts());
        // you can't directly create a filter with statuses; they must be added later
        filter.setStatuses(new HashSet<>());

        if (params.expires_in != null && !params.expires_in.isNull()) {
            long expiresIn;
            if (params.expires_in.isTextual()) expiresIn = Long.parseLong(params.expires_in.asText());
            else if (params.expires_in.isNumber()) expiresIn = params.expires_in.asLong();
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            filter.setExpirationMillis(System.currentTimeMillis() + expiresIn * 1000);
        }
        filter.setKeywords(params.parseKeywordsAsCreates());

        return Mono.fromFuture(manager.postFilter(filter)).map(GetFilter::new);
    }

    @RequestMapping(value="/api/v2/filters/{id}", method={RequestMethod.PUT, RequestMethod.PATCH})
    public Mono<GetFilter> putFilter(WebSession session, @PathVariable("id") Long filterId, @RequestBody PostFilterParams params) {
        long requestAccountId = getMandatoryAccountId(session);
        EditFilter edit = (new EditFilter())
                .setAccountId(requestAccountId)
                .setFilterId(filterId)
                .setTimestamp(System.currentTimeMillis());
        if (params.title != null) edit.setTitle(params.title);
        if (params.context != null) edit.setContext(params.parseContexts());
        if (params.expires_in != null && !params.expires_in.isNull()) {
            long expiresIn;
            if (params.expires_in.isTextual()) expiresIn = Long.parseLong(params.expires_in.asText());
            else if (params.expires_in.isNumber()) expiresIn = params.expires_in.asLong();
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            edit.setExpirationMillis(System.currentTimeMillis() + expiresIn * 1000);
        }
        edit.setKeywords(params.parseKeywordsAsUpdates());
        if (params.filter_action != null) edit.setAction(params.parseAction());
        return Mono.fromFuture(manager.putFilter(edit))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetFilter::new);

    }

    @DeleteMapping("/api/v2/filters/{id}")
    public Mono<Map<String, Object>> deleteFilter(WebSession session, @PathVariable("id") Long filterId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.deleteFilter(requestAccountId, filterId));
    }

    @GetMapping("/api/v2/filters/{filter_id}/keywords")
    public Mono<List<GetFilter.FilterKeyword>> getFilterKeywords(WebSession session, @PathVariable("filter_id") Long filterId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getFilter(requestAccountId, filterId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetFilter::new)
                .map((GetFilter filter) -> filter.keywords);
    }

    @GetMapping("/api/v2/filters/keywords/{id}")
    public Mono<GetFilter.FilterKeyword> getFilterKeyword(WebSession session, @PathVariable("id") String keywordId) {
        long requestAccountId = getMandatoryAccountId(session);
        Long filterId = GetFilter.FilterKeyword.filterIdFromKeywordId(keywordId);
        return Mono.fromFuture(manager.getFilter(requestAccountId, filterId))
                .map(GetFilter::new)
                .flatMap((GetFilter filter) -> {
                    Optional<GetFilter.FilterKeyword> maybeKeyword = filter.keywords.stream().filter(k -> k.id.equals(keywordId)).findFirst();
                    if (maybeKeyword.isPresent()) return Mono.just(maybeKeyword.get());
                    return Mono.empty();
                }).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @PostMapping("/api/v2/filters/{filter_id}/keywords")
    public Mono<GetFilter.FilterKeyword> postFilterKeyword(WebSession session, @PathVariable("filter_id") Long filterId, @RequestBody PostFilterKeywordParams params) {
        long requestAccountId = getMandatoryAccountId(session);
        EditFilter edit = (new EditFilter())
                .setAccountId(requestAccountId)
                .setFilterId(filterId)
                .setKeywords(Arrays.asList(EditFilterKeyword.addKeyword(params.toKeywordFilter())));
        return Mono.fromFuture(manager.putFilter(edit))
                .flatMap(result -> Mono.fromFuture(manager.getFilter(requestAccountId, filterId)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(filter -> {
                    Optional<GetFilter.FilterKeyword> maybeKeyword = (new GetFilter(filter)).keywords.stream()
                            .filter(kw -> kw.keyword.equals(params.keyword))
                            .findFirst();
                    if (maybeKeyword.isPresent()) return Mono.just(maybeKeyword.get());
                    return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
                });
    }

    @RequestMapping(value="/api/v2/filters/keywords/{id}", method={RequestMethod.PUT, RequestMethod.PATCH})
    public Mono<GetFilter.FilterKeyword> putFilterKeyword(WebSession session, @PathVariable("id") String keywordId, @RequestBody PostFilterKeywordParams params) {
        long requestAccountId = getMandatoryAccountId(session);
        Long filterId = GetFilter.FilterKeyword.filterIdFromKeywordId(keywordId);
        String currentWord = GetFilter.FilterKeyword.currentWordFromKeywordId(keywordId);
        EditFilter edit = (new EditFilter())
                .setAccountId(requestAccountId)
                .setFilterId(filterId)
                .setKeywords(Arrays.asList(EditFilterKeyword.updateKeyword((new UpdateKeyword(currentWord, params.keyword, params.whole_word)))));
        return Mono.fromFuture(manager.putFilter(edit))
                .flatMap(result -> Mono.fromFuture(manager.getFilter(requestAccountId, filterId)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(filter -> {
                    Optional<GetFilter.FilterKeyword> maybeKeyword = (new GetFilter(filter)).keywords.stream()
                            .filter(kw -> {
                                if (params.keyword != null) return kw.keyword.equals(params.keyword);
                                return kw.id.equals(keywordId);
                            })
                            .findFirst();
                    if (maybeKeyword.isPresent()) return Mono.just(maybeKeyword.get());
                    return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
                });
    }

    @DeleteMapping("/api/v2/filters/keywords/{id}")
    public Mono<Void> deleteFilterKeyword(WebSession session, @PathVariable("id") String keywordId) {
        long requestAccountId = getMandatoryAccountId(session);
        Long filterId = GetFilter.FilterKeyword.filterIdFromKeywordId(keywordId);
        String currentWord = GetFilter.FilterKeyword.currentWordFromKeywordId(keywordId);
        EditFilter edit = (new EditFilter())
                .setAccountId(requestAccountId)
                .setFilterId(filterId)
                .setKeywords(Arrays.asList(EditFilterKeyword.destroyKeyword(currentWord)));
        return Mono.fromFuture(manager.putFilter(edit)).flatMap(result -> Mono.empty());
    }

    @GetMapping("/api/v2/filters/{filter_id}/statuses")
    public Mono<List<GetFilter.FilterStatus>> getFilterStatuses(WebSession session, @PathVariable("filter_id") Long filterId) {
        long requestAccountId = getMandatoryAccountId(session);
        return Mono.fromFuture(manager.getFilter(requestAccountId, filterId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetFilter::new)
                .map((GetFilter filter) -> filter.statuses);
    }

    @PostMapping("/api/v2/filters/{filter_id}/statuses")
    public Mono<GetFilter.FilterStatus> addStatusToFilter(WebSession session, @PathVariable("filter_id") Long filterId, @RequestBody PostFilterStatusParams params) {
        long requestAccountId = getMandatoryAccountId(session);
        StatusPointer statusPointer = MastodonHelpers.parseStatusPointer(params.status_id);
        return Mono.fromFuture(manager.addStatusToFilter(requestAccountId, filterId, statusPointer))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetFilter::new)
                .map((GetFilter filter) -> filter.statuses.stream().filter(status -> status.status_id.equals(params.status_id)).findFirst().get());
    }

    @GetMapping("/api/v2/filters/statuses/{id}")
    public Mono<GetFilter.FilterStatus> getFilterStatus(WebSession session, @PathVariable("id") String statusFilterId) {
        long requestAccountId = getMandatoryAccountId(session);
        Long filterId = MastodonHelpers.getFilterIdFromStatusFilterId(statusFilterId);
        return Mono.fromFuture(manager.getFilter(requestAccountId, filterId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(GetFilter::new)
                .flatMap((GetFilter filter) -> {
                    Optional<GetFilter.FilterStatus> maybeStatus = filter.statuses.stream().filter(statusFilter -> statusFilter.id.equals(statusFilterId)).findFirst();
                    if (maybeStatus.isPresent()) return Mono.just(maybeStatus.get());
                    else return Mono.empty();
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @DeleteMapping("/api/v2/filters/statuses/{id}")
    public Mono<Map<String, Object>> deleteFilterStatus(WebSession session, @PathVariable("id") String statusFilterId) {
        long requestAccountId = getMandatoryAccountId(session);
        Long filterId = MastodonHelpers.getFilterIdFromStatusFilterId(statusFilterId);
        StatusPointer statusPointer = MastodonHelpers.getStatusPointerFromStatusFilterId(statusFilterId);
        return Mono.fromFuture(manager.removeStatusFromFilter(requestAccountId, filterId, statusPointer));
    }

    // Webfinger + ActivityPub

    @GetMapping("/.well-known/webfinger")
    public Mono<GetWebFinger> getWebFinger(@RequestParam(required = true) String resource) {
        String[] schemeAndRemoteName = resource.split(":");
        if (schemeAndRemoteName.length != 2 || !schemeAndRemoteName[0].equals("acct")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resource");
        String[] remoteName = schemeAndRemoteName[1].split("@");
        if (remoteName.length != 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resource");
        String localName = remoteName[0];
        return Mono.fromFuture(manager.getAccountId(localName))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(accountId -> {
                       String profileUrlShort = String.format("%s/@%s", MastodonConfig.API_URL, localName);
                       String profileUrlLong = String.format("%s/users/%s", MastodonConfig.API_URL, localName);
                       GetWebFinger webFinger = new GetWebFinger();
                       webFinger.subject = resource;
                       webFinger.aliases = Arrays.asList(profileUrlShort, profileUrlLong);
                       webFinger.links = new ArrayList<>();
                       webFinger.links.add(new HashMap(){{
                           put("rel", "http://webfinger.net/rel/profile-page");
                           put("type", "text/html");
                           put("href", profileUrlShort);
                       }});
                       webFinger.links.add(new HashMap(){{
                           put("rel", "self");
                           put("type", "application/activity+json");
                           put("href", profileUrlLong);
                       }});
                       return webFinger;
                   });
    }

    @GetMapping("/users/{name}")
    public Mono<GetActivityPubProfile> getProfile(@PathVariable("name") String name) {
        return Mono.fromFuture(manager.getAccountId(name))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                   .map(MastodonApiHelpers::createProfile);
    }

    @GetMapping("/@{name}")
    public Mono<GetActivityPubProfile> getProfileShort(@PathVariable("name") String name) {
        return this.getProfile(name);
    }

    @PostMapping("/users/{name}/inbox")
    public Mono postInbox(ServerWebExchange exchange, @PathVariable("name") String name, @RequestBody String requestBody) throws MalformedURLException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        PostActivityPubInbox params = objectMapper.readValue(requestBody, PostActivityPubInbox.class);
        switch (params.type) {
            case "Create":
            case "Update": {
                String source = params.actor;
                URL sourceUrl = new URL(source);
                String sourceHost = sourceUrl.getHost();
                if (sourceHost.equals(MastodonConfig.API_DOMAIN) && !sourceHost.equals("localhost")) throw new RuntimeException("Actor is on this server");
                HashMap object = (HashMap) params.object;
                String objectId = (String) object.get("id");
                URL objectUrl = new URL(objectId);
                String objectHost = objectUrl.getHost();
                if (!objectHost.equals(sourceHost)) {
                    throw new RuntimeException("The id indicates a different host than the actor sending it: " + sourceHost + " vs " + objectHost);
                }
                String objectType = (String) object.get("type");
                switch (objectType) {
                    case "Note":
                    case "Question":
                        return this.getRemoteAccountProfile(source)
                                   .flatMap(sourceProfile ->
                                       this.getRemoteAccountWithId(sourceProfile, sourceHost)
                                           .map(sourceAccountWithId -> MastodonWebHelpers.verifyHeaders(exchange.getRequest().getHeaders().entrySet(), exchange.getRequest().getPath().toString(), requestBody, sourceAccountWithId))
                                           .flatMap(sourceAccountWithId -> {
                                               if ("Create".equals(params.type)) {
                                                   String parentUrl = (String) object.get("inReplyTo");
                                                   if (parentUrl != null) { // this is a reply
                                                       return this.getRemoteOrLocalStatus(sourceAccountWithId.accountId, parentUrl)
                                                                  .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                                                  .flatMap(parentStatus -> {
                                                                      PollContent pollContent = MastodonApiHelpers.getPollContent(parentStatus);
                                                                      // this is a poll vote
                                                                      if ("Note".equals(objectType) && pollContent != null && object.containsKey("name")) {
                                                                          String choiceName = (String) object.get("name");
                                                                          return Mono.fromFuture(
                                                                                  manager.getPollVote(sourceAccountWithId.accountId, parentStatus.result.statusId)
                                                                                         .thenCompose(choicesMaybe -> {
                                                                                             Set<Integer> choices = new HashSet<>();
                                                                                             if (choicesMaybe != null) choices.addAll(choicesMaybe);
                                                                                             int newChoice = pollContent.choices.indexOf(choiceName);
                                                                                             if (newChoice == -1) throw new RuntimeException(String.format("Couldn't find '%s' in choices for poll %s", choiceName, parentStatus.result.statusId));
                                                                                             choices.add(newChoice);
                                                                                             return manager.postPollVote(sourceAccountWithId.accountId, new StatusPointer(parentStatus.result.status.author.accountId, parentStatus.result.statusId), choices);
                                                                                         }))
                                                                                  .map(result -> new HashMap());
                                                                      }
                                                                      // this is a normal reply
                                                                      else {
                                                                          return Mono.fromFuture(manager.postStatus(sourceAccountWithId.accountId, MastodonApiHelpers.createPostStatus(sourceProfile, object, parentStatus), objectId))
                                                                                     .map(result -> new HashMap());
                                                                      }
                                                                  });
                                                   } else { // this is a normal status
                                                       return Mono.fromFuture(manager.postStatus(sourceAccountWithId.accountId, MastodonApiHelpers.createPostStatus(sourceProfile, object), objectId))
                                                                  .map(result -> new HashMap());
                                                   }
                                               } else if ("Update".equals(params.type)) {
                                                   return Mono.fromFuture(manager.getRemoteStatus(sourceAccountWithId.accountId, objectId))
                                                              .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                                              .flatMap(statusQueryResult -> Mono.fromFuture(manager.putStatus(new StatusPointer(statusQueryResult.result.status.author.accountId, statusQueryResult.result.statusId), MastodonApiHelpers.createPutStatus(object))))
                                                              .map(result -> new HashMap());
                                               } else throw new RuntimeException("Unexpected type");
                                           }));
                    case "Person": {
                        // re-parse the body to a more specialized type so we don't have to deal with a HashMap
                        PostActivityPubInbox<GetActivityPubProfile> profileParams = objectMapper.readValue(requestBody, new TypeReference<PostActivityPubInbox<GetActivityPubProfile>>() {});
                        String nameWithHost = String.format("%s@%s", profileParams.object.preferredUsername, sourceHost);
                        return Mono.fromFuture(manager.getAccountId(nameWithHost))
                                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                   .flatMap(accountId -> Mono.fromFuture(manager.getAccountWithId(accountId)))
                                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                   .map(sourceAccountWithId -> MastodonWebHelpers.verifyHeaders(exchange.getRequest().getHeaders().entrySet(), exchange.getRequest().getPath().toString(), requestBody, sourceAccountWithId))
                                   .flatMap(sourceAccountWithId ->
                                           Mono.fromFuture(manager.postEditAccount(sourceAccountWithId.accountId, MastodonApiHelpers.getEditsFromProfile(sourceAccountWithId, profileParams.object)))
                                               .map(result -> new HashMap()));
                    }
                    default: throw new RuntimeException("Unhandled object type: " + objectType);
                }
            }
            case "Follow": {
                String source = params.actor;
                URL sourceUrl = new URL(source);
                String sourceHost = sourceUrl.getHost();
                if (sourceHost.equals(MastodonConfig.API_DOMAIN) && !sourceHost.equals("localhost")) throw new RuntimeException("Actor is on this server");
                String target = (String) params.object;
                URL targetUrl = new URL(target);
                String targetHost = targetUrl.getHost();
                if (!targetHost.equals(MastodonConfig.API_DOMAIN)) throw new RuntimeException("Object is not on this server");
                return this.getRemoteAccountProfile(source)
                           .flatMap(sourceProfile ->
                               this.getProfile(MastodonApiHelpers.getUsernameFromProfileUrl(targetUrl))
                                   .flatMap(targetProfile ->
                                       this.getRemoteAccountWithId(sourceProfile, sourceHost)
                                           .map(sourceAccountWithId -> MastodonWebHelpers.verifyHeaders(exchange.getRequest().getHeaders().entrySet(), exchange.getRequest().getPath().toString(), requestBody, sourceAccountWithId))
                                           .flatMap(sourceAccountWithId ->
                                               Mono.fromFuture(manager.getAccountId(targetProfile.preferredUsername))
                                                   .flatMap(targetAccountId -> Mono.fromFuture(manager.getAccountWithId(targetAccountId)))
                                                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                                   .flatMap(targetAccountWithId ->
                                                       Mono.fromFuture(manager.postFollowAccount(sourceAccountWithId.accountId, targetAccountWithId.accountId, sourceAccountWithId.account.content.getRemote().sharedInboxUrl, null))
                                                           .flatMap(result -> {
                                                               // if the account isn't locked, send Accept immediately
                                                               if (!targetAccountWithId.account.isSetLocked() || !targetAccountWithId.account.locked) {
                                                                   return Mono.fromFuture(MastodonWebHelpers.buildRequest(targetAccountWithId.account, sourceProfile.inbox, new PostActivityPubInbox("Accept", target, params)))
                                                                              .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)))
                                                                              .map(result2 -> new HashMap());
                                                               }
                                                               else return Mono.just(new HashMap());
                                                           })))));
            }
            case "Block": {
                String source = params.actor;
                URL sourceUrl = new URL(source);
                String sourceHost = sourceUrl.getHost();
                if (sourceHost.equals(MastodonConfig.API_DOMAIN) && !sourceHost.equals("localhost")) throw new RuntimeException("Actor is on this server");
                String target = (String) params.object;
                URL targetUrl = new URL(target);
                String targetHost = targetUrl.getHost();
                if (!targetHost.equals(MastodonConfig.API_DOMAIN)) throw new RuntimeException("Object is not on this server");
                return this.getRemoteAccountProfile(source)
                           .flatMap(sourceProfile ->
                               this.getProfile(MastodonApiHelpers.getUsernameFromProfileUrl(targetUrl))
                                   .flatMap(targetProfile ->
                                       this.getRemoteAccountWithId(sourceProfile, sourceHost)
                                           .map(sourceAccountWithId -> MastodonWebHelpers.verifyHeaders(exchange.getRequest().getHeaders().entrySet(), exchange.getRequest().getPath().toString(), requestBody, sourceAccountWithId))
                                           .flatMap(sourceAccountWithId ->
                                               Mono.fromFuture(manager.getAccountId(targetProfile.preferredUsername))
                                                   .flatMap(targetAccountId -> Mono.fromFuture(manager.getAccountWithId(targetAccountId)))
                                                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                                   .flatMap(targetAccountWithId -> Mono.fromFuture(manager.postBlockAccount(sourceAccountWithId.accountId, targetAccountWithId.accountId)))
                                                   .map(result -> new HashMap()))));
            }
            case "Undo": {
                String source = params.actor;
                URL sourceUrl = new URL(source);
                String sourceHost = sourceUrl.getHost();
                if (sourceHost.equals(MastodonConfig.API_DOMAIN) && !sourceHost.equals("localhost")) throw new RuntimeException("Actor is on this server");
                HashMap object = (HashMap) params.object;
                if (!source.equals(object.get("actor"))) throw new RuntimeException("Invalid actor");
                String target = (String) object.get("object");
                String objectType = (String) object.get("type");
                switch (objectType) {
                    case "Follow":
                    case "Block":
                        URL targetUrl = new URL(target);
                        String targetHost = targetUrl.getHost();
                        if (!targetHost.equals(MastodonConfig.API_DOMAIN)) throw new RuntimeException("Object is not on this server");
                        return this.getRemoteAccountProfile(source)
                                   .flatMap(sourceProfile ->
                                       this.getProfile(MastodonApiHelpers.getUsernameFromProfileUrl(targetUrl))
                                           .flatMap(targetProfile ->
                                               this.getRemoteAccountWithId(sourceProfile, sourceHost)
                                                   .map(sourceAccountWithId -> MastodonWebHelpers.verifyHeaders(exchange.getRequest().getHeaders().entrySet(), exchange.getRequest().getPath().toString(), requestBody, sourceAccountWithId))
                                                   .flatMap(sourceAccountWithId ->
                                                       Mono.fromFuture(manager.getAccountId(targetProfile.preferredUsername))
                                                           .flatMap(targetAccountId -> Mono.fromFuture(manager.getAccountWithId(targetAccountId)))
                                                           .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                                           .flatMap(targetAccountWithId -> {
                                                               Mono<Boolean> mono;
                                                               if (objectType.equals("Follow")) {
                                                                   mono = Mono.fromFuture(manager.postRemoveFollowAccount(sourceAccountWithId.accountId, targetAccountWithId.accountId, sourceAccountWithId.account.content.getRemote().sharedInboxUrl));
                                                               } else if (objectType.equals("Block")) {
                                                                   mono = Mono.fromFuture(manager.postRemoveBlockAccount(sourceAccountWithId.accountId, targetAccountWithId.accountId));
                                                               } else throw new RuntimeException("Invalid type");
                                                               return mono;
                                                           })
                                                           .map(result -> new HashMap()))));
                    case "Like":
                    case "Announce":
                        return this.getRemoteAccountProfile(source)
                                   .flatMap(sourceProfile -> this.getRemoteAccountWithId(sourceProfile, sourceHost))
                                   .flatMap(sourceAccountWithId ->
                                       this.getRemoteOrLocalStatusPointer(sourceAccountWithId.accountId, target)
                                           .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                           .flatMap(statusPointer -> {
                                               if (objectType.equals("Like")) {
                                                   return Mono.fromFuture(manager.postRemoveFavoriteStatus(sourceAccountWithId.accountId, statusPointer));
                                               } else if (objectType.equals("Announce")) {
                                                   return Mono.fromFuture(manager.postRemoveBoostStatus(sourceAccountWithId.accountId, statusPointer));
                                               } else throw new RuntimeException("Invalid type");
                                           })
                                           .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                           .map(result -> new HashMap()));
                    default: throw new RuntimeException("Unhandled object type: " + objectType);
                }
            }
            case "Like":
            case "Announce": {
                String source = params.actor;
                URL sourceUrl = new URL(source);
                String sourceHost = sourceUrl.getHost();
                if (sourceHost.equals(MastodonConfig.API_DOMAIN) && !sourceHost.equals("localhost")) throw new RuntimeException("Actor is on this server");
                String target = (String) params.object;
                return this.getRemoteAccountProfile(source)
                           .flatMap(sourceProfile -> this.getRemoteAccountWithId(sourceProfile, sourceHost))
                           .flatMap(sourceAccountWithId ->
                               this.getRemoteOrLocalStatusPointer(sourceAccountWithId.accountId, target)
                                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                   .flatMap(statusPointer -> {
                                       if (params.type.equals("Like")) {
                                           return Mono.fromFuture(manager.postFavoriteStatus(sourceAccountWithId.accountId, statusPointer));
                                       } else if (params.type.equals("Announce")) {
                                           return Mono.fromFuture(manager.postBoostStatus(sourceAccountWithId.accountId, statusPointer, params.id));
                                       } else throw new RuntimeException("Invalid type");
                                   })
                                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                   .map(result -> new HashMap()));
            }
            case "Delete": {
                String source = params.actor;
                URL sourceUrl = new URL(source);
                String sourceHost = sourceUrl.getHost();
                if (sourceHost.equals(MastodonConfig.API_DOMAIN) && !sourceHost.equals("localhost")) throw new RuntimeException("Actor is on this server");
                HashMap object = (HashMap) params.object;
                String target = (String) object.get("id");
                URL targetUrl = new URL(target);
                String targetHost = targetUrl.getHost();
                if (!targetHost.equals(sourceHost)) throw new RuntimeException("Object server is not the same as the actor server");
                String objectType = (String) object.get("type");
                switch (objectType) {
                    case "Tombstone":
                        return this.getRemoteAccountProfile(source)
                                   .flatMap(sourceProfile ->
                                       this.getRemoteAccountWithId(sourceProfile, sourceHost)
                                           .map(sourceAccountWithId -> MastodonWebHelpers.verifyHeaders(exchange.getRequest().getHeaders().entrySet(), exchange.getRequest().getPath().toString(), requestBody, sourceAccountWithId))
                                           .flatMap(sourceAccountWithId ->
                                               Mono.fromFuture(manager.getRemoteStatus(sourceAccountWithId.accountId, target))
                                                   .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                                   .flatMap(statusQueryResult -> Mono.fromFuture(manager.deleteStatus(statusQueryResult.result.status.author.accountId, statusQueryResult.result.statusId)))
                                                   .map(result -> new HashMap())));
                    default: throw new RuntimeException("Unhandled object type: " + objectType);
                }
            }
            case "Accept": return Mono.just(new HashMap());
        }
        throw new RuntimeException("Unhandled type: " + params.type);
    }

    @PostMapping("/inbox")
    public Mono postSharedInbox(ServerWebExchange exchange, @RequestBody String requestBody) throws MalformedURLException, JsonProcessingException {
        return this.postInbox(exchange, null, requestBody);
    }

    @GetMapping("/metrics")
    public Mono<GetMetrics> getMetrics() {
        return Mono.just(new GetMetrics(MastodonApiMetrics.HOURLY_METRICS));
    }
}
