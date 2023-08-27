package com.rpl.mastodonapi;

import org.springframework.context.annotation.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.multipart.*;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.session.*;
import org.springframework.session.config.annotation.web.server.EnableSpringWebSession;
import org.springframework.web.reactive.config.*;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.*;
import org.springframework.web.server.session.*;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableWebFlux
@EnableSpringWebSession
@EnableWebFluxSecurity
public class MastodonApiConfig implements WebFluxConfigurer {
    public static final String STATIC_FILE_DIR = "./uploads";
    public static final String STATIC_FILE_URL_PATH_NAME = "uploads";
    public static final HashSet<String> IMAGE_EXTS = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
    public static final HashSet<String> VIDEO_EXTS = new HashSet<>(Arrays.asList("webm", "mp4", "m4v", "mov"));
    public static final String OAUTH_CLIENT_ID = "cef6f1929499f942a173abd002a69a3a";
    public static class S3Options {
        public String bucketName;
        public String url;
    }
    public static S3Options S3_OPTIONS = null;
    static {
        S3_OPTIONS = new S3Options();
        S3_OPTIONS.bucketName = "yourbucket";
        S3_OPTIONS.url = "https://yourbucket.s3.amazonaws.com";
    }
    public static final int MAX_STATUS_LENGTH = 500;
    public static final int MAX_DISPLAY_NAME_LENGTH = 20;
    public static final int MAX_BIO_LENGTH = 280;
    public static final int MAX_FIELD_LENGTH = 40;
    public static final int MAX_LIST_LENGTH = 40;
    public static final int MAX_NOTE_LENGTH = 500;
    public static final int MAX_USERNAME_LENGTH = 30;
    public static final int MAX_POLL_CHOICE_LENGTH = 30;

    @Bean
    public ReactiveSessionRepository reactiveSessionRepository() {
        return new ReactiveSessionRepository<MapSession>() {
            private final Map<String, Session> sessions = new MastodonApiConcurrentFixedMap<>(10000);

            @Override
            public Mono<Void> save(MapSession session) {
                return Mono.fromRunnable(() -> {
                    if (!session.getId().equals(session.getOriginalId())) this.sessions.remove(session.getOriginalId());
                    this.sessions.put(session.getId(), new MapSession(session));
                });
            }

            @Override
            public Mono<MapSession> findById(String id) {
                return Mono.defer(() ->
                        // find the session id in the backend
                        // we always query it first in case it has been revoked
                        Mono.fromFuture(MastodonApiController.manager.getAccountIdFromAuthCode(id))
                            // if we can't find it, make sure it's removed from memory
                            .switchIfEmpty(Mono.defer(() -> this.deleteById(id).then(Mono.empty())))
                            .flatMap(accountId ->
                                // try to get the session from the in-memory map
                                Mono.justOrEmpty(this.sessions.get(id))
                                    .map(MapSession::new)
                                    // if we can't find the session, query the backend for the account info
                                    // and create the session. this could happen if the user logged in via
                                    // a different API server.
                                    .switchIfEmpty(
                                        Mono.defer(() ->
                                            Mono.fromFuture(MastodonApiController.manager.getAccountWithId(accountId))
                                                .flatMap(accountWithId ->
                                                        this.createSession()
                                                            .flatMap(session -> {
                                                                session.setId(id);
                                                                session.setAttribute("accountId", accountWithId.accountId);
                                                                session.setAttribute("accountName", accountWithId.account.name);
                                                                return this.save(session).then(Mono.just(session));
                                                            }))))));
            }

            @Override
            public Mono<Void> deleteById(String id) {
                return Mono.fromRunnable(() -> this.sessions.remove(id));
            }

            @Override
            public Mono<MapSession> createSession() {
                return Mono.defer(() -> {
                    MapSession result = new MapSession();
                    return Mono.just(result);
                });
            }
        };
    }

    @Bean
    public WebSessionIdResolver headerWebSessionIdResolver() {
        final String AUTH_HEADER = "Authorization";
        final String AUTH_HEADER_PREFIX = "Bearer";
        final String SEC_WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";

        return new WebSessionIdResolver() {
            @Override
            public List<String> resolveSessionIds(ServerWebExchange exchange) {
                HttpHeaders headers = exchange.getRequest().getHeaders();
                // authentication header
                for (String header : headers.getOrDefault(AUTH_HEADER, Collections.emptyList())) {
                    String[] parts = header.split("\\s+");
                    if (parts.length == 2 && AUTH_HEADER_PREFIX.equals(parts[0])) return Arrays.asList(parts[1]);
                    else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Authorization header");
                }
                // web socket connections send the session id via a different header
                for (String header : headers.getOrDefault(SEC_WEBSOCKET_PROTOCOL_HEADER, Collections.emptyList())) {
                    return Arrays.asList(header);
                }
                return new ArrayList<>();
            }

            @Override
            public void setSessionId(ServerWebExchange exchange, String sessionId) {
                exchange.getResponse().getHeaders().set(AUTH_HEADER, AUTH_HEADER_PREFIX + " " + sessionId);
            }

            @Override
            public void expireSession(ServerWebExchange exchange) {
                this.setSessionId(exchange, "");
            }
        };
    }

    @Bean
    public SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) {
        return http.httpBasic().disable()
                   .formLogin().disable()
                   .csrf().disable()
                   .authorizeExchange()
                   .pathMatchers("/**")
                   .permitAll()
                   .and()
                   .build();
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        DefaultPartHttpMessageReader partReader = new DefaultPartHttpMessageReader();
        partReader.setMaxParts(40); // the update_credentials endpoint sends all its params as parts
        partReader.setMaxDiskUsagePerPart(4 * 1024 * 1024);
        partReader.setEnableLoggingRequestDetails(true);
        MultipartHttpMessageReader multipartReader = new MultipartHttpMessageReader(partReader);
        multipartReader.setEnableLoggingRequestDetails(true);
        configurer.defaultCodecs().multipartReader(multipartReader);
        configurer.defaultCodecs().maxInMemorySize(512 * 1024);
    }

    @Bean
    public RouterFunction staticResourceLocator() {
        return RouterFunctions.resources(String.format("/%s/**", STATIC_FILE_URL_PATH_NAME),
                                         new FileSystemResource(STATIC_FILE_DIR + "/"));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/public/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS));
    }
}
