package com.rpl.mastodonapi;

import com.rpl.rama.ops.RamaFunction0;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestHelpers {
    public static void assertNotFound(RequestHeadersSpec<?> req) {
        req.exchangeToMono(response -> {
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
            return Mono.empty();
        })
        .block();
    }

    public static void assertUnauthorized(RequestHeadersSpec<?> req) {
        req.exchangeToMono(response -> {
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode());
            return Mono.empty();
        })
        .block();
    }

    public static void attainCondition(RamaFunction0<Boolean> fn) {
      long start = System.nanoTime();
      while(true) {
        if(fn.invoke()) {
          break;
        } else if(System.nanoTime() - start >= 45000000000L) { // 45 seconds
          throw new RuntimeException("Failed to attain condition");
        } else {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
}
