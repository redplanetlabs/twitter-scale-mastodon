package com.rpl.mastodonapi;

import io.github.bucket4j.*;
import io.github.bucket4j.local.SynchronizationStrategy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;

@Component
@Profile("!test") // don't rate limit in tests
public class MastodonApiRateLimiter implements WebFilter {
    private static final int BUCKET_COUNT = 1000;

    private static final int MAIN_REQUESTS_PER_SECOND = 100;
    MastodonApiConcurrentFixedMap<String, Bucket> mainBuckets = new MastodonApiConcurrentFixedMap<>(BUCKET_COUNT);

    private static final int LOGIN_REQUESTS_PER_SECOND = 2;
    MastodonApiConcurrentFixedMap<String, Bucket> loginBuckets = new MastodonApiConcurrentFixedMap<>(BUCKET_COUNT);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        InetSocketAddress remoteAddr = request.getRemoteAddress();

        if (remoteAddr != null) {
            String sourceIp = remoteAddr.getAddress().getHostAddress();

            // get the buckets based on the path
            // the login endpoint has a more restrictive request limit
            final MastodonApiConcurrentFixedMap<String, Bucket> buckets;
            final int reqPerSec;
            if (request.getMethod() == HttpMethod.POST && request.getPath().pathWithinApplication().value().equals("/oauth/token")) {
                buckets = loginBuckets;
                reqPerSec = LOGIN_REQUESTS_PER_SECOND;
            } else {
                buckets = mainBuckets;
                reqPerSec = MAIN_REQUESTS_PER_SECOND;
            }

            // get or create the bucket
            Bucket bucket = buckets.get(sourceIp);
            if (bucket == null) {
                bucket = Bucket.builder()
                        .addLimit(Bandwidth.simple(reqPerSec, Duration.ofSeconds(1)))
                        .withNanosecondPrecision()
                        .withSynchronizationStrategy(SynchronizationStrategy.LOCK_FREE)
                        .build();
                buckets.put(sourceIp, bucket);
            }

            // return error if limit exceeded
            if (!bucket.tryConsume(1)) return Mono.error(new ResponseStatusException(HttpStatus.BANDWIDTH_LIMIT_EXCEEDED));
        }

        return chain.filter(exchange);
    }
}
