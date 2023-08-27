package com.rpl.mastodonapi;

import com.clearspring.analytics.stream.cardinality.HyperLogLog;
import com.fasterxml.jackson.annotation.JsonGetter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class MastodonApiMetrics implements WebFilter {
    public static class Metrics {
        public long requests = 0;
        private final HyperLogLog uniqueIPsHLL = new HyperLogLog(11);

        @JsonGetter("uniqueIPs")
        public long getUniqueIPs() {
            return uniqueIPsHLL.cardinality();
        }
    }

    public static final MastodonApiConcurrentFixedMap<LocalDateTime, Metrics> HOURLY_METRICS = new MastodonApiConcurrentFixedMap<>(24);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        LocalDateTime ldt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        HOURLY_METRICS.compute(ldt, (LocalDateTime key, Metrics m) -> {
            if (m == null) m = new Metrics();
            m.requests += 1;
            InetSocketAddress remoteAddr = exchange.getRequest().getRemoteAddress();
            if (remoteAddr != null) {
                String addr = remoteAddr.getAddress().getHostAddress();
                m.uniqueIPsHLL.offer(addr);
            }
            return m;
        });
        return chain.filter(exchange);
    }
}
