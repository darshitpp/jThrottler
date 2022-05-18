package dev.darshit.jthrottler;

import dev.darshit.jthrottler.example.ExampleClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

class ThrottlerTest {

    private static final Throttler.Request<String> POISON_PILL = new Throttler.Request<>("POISON_PILL", "POISON_PILL");
    private static final int N_THREADS = 10;
    private static final int REQUESTS_SIZE = 20;
    private static final int BATCH_REQUEST_SIZE_PER_SECOND = 5;
    static ThrottledClient<String, String> throttledClient;
    static Throttler<String, String> throttler;

    @BeforeAll
    static void beforeAll() {

        throttledClient = new ExampleClient();
        throttler = new Throttler.Builder<String, String>()
                .withPoisonPillRequest(POISON_PILL)
                .withThrottledClient(throttledClient)
                .withBatchRequestSizePerSecond(BATCH_REQUEST_SIZE_PER_SECOND)
                .build();

        new Thread(throttler).start();
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        throttler.add(POISON_PILL);
        Thread.sleep(Duration.ofSeconds(1).toMillis());
        Assertions.assertThat(throttler.isRunning())
                .isEqualTo(false);
    }

    @RepeatedTest(5)
    void test_throttle() throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(N_THREADS);

        List<Future<Throttler.Response<String>>> requestFutures = new ArrayList<>();
        Map<String, String> requestMap = new HashMap<>();

        for (int i = 0; i < REQUESTS_SIZE; i++) {
            String requestId = i + "_" + UUID.randomUUID();
            String requestPayload = "Request_" + i + "_" + UUID.randomUUID();

            requestMap.put(requestId, requestPayload);

            Throttler.Request<String> request = new Throttler.Request<>(requestId, requestPayload);
            ThreadToThrottle threadToThrottle = new ThreadToThrottle(throttler, request);
            requestFutures.add(executorService.submit(threadToThrottle));
        }

        for (Future<Throttler.Response<String>> response : requestFutures) {
            Throttler.Response<String> throttlerResponse = response.get();

            String expected = requestMap.get(throttlerResponse.getRequestId()).replace("Request", "Response");
            Assertions.assertThat(throttlerResponse.getResponse())
                    .isEqualTo(expected);
        }
    }

    static class ThreadToThrottle implements Callable<Throttler.Response<String>> {

        private final Throttler<String, String> throttler;
        private final Throttler.Request<String> throttleRequest;

        ThreadToThrottle(Throttler<String, String> throttler, Throttler.Request<String> throttleRequest) {
            this.throttler = throttler;
            this.throttleRequest = throttleRequest;
        }

        @Override
        public Throttler.Response<String> call() {
            throttler.add(throttleRequest);
            Throttler.Response<String> read;
            do {
                read = throttler.read();
            } while (read == null || !read.getRequestId().equals(throttleRequest.getRequestId()));
            throttler.remove(read);
            return read;
        }
    }


}