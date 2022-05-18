package dev.darshit.jthrottler;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Throttles the request
 * @param <I>
 * @param <J>
 */
public class Throttler<I, J> implements ThrottledClientOperations<I, J>, Runnable {

    private final LinkedBlockingQueue<Request<I>> requestQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Response<J>> responseQueue = new LinkedBlockingQueue<>();
    private final int batchRequestSizePerSecond;
    private final Function<List<Request<I>>, List<Response<J>>> throttledClient;
    private final Throttler.Request<I> POISON_PILL_REQUEST;
    private final RateLimiter rateLimiter;

    private volatile boolean running;

    public static final Logger logger = LoggerFactory.getLogger(Throttler.class.getName());

    private Throttler(Request<I> poisonPillRequest, Function<List<Request<I>>, List<Response<J>>> throttledClient, int batchRequestSizePerSecond) {
        this.batchRequestSizePerSecond = batchRequestSizePerSecond;
        this.throttledClient = throttledClient;
        POISON_PILL_REQUEST = poisonPillRequest;
        this.rateLimiter = RateLimiter.create(batchRequestSizePerSecond);
    }
    private Throttler(Request<I> poisonPillRequest, ThrottledClient<I, J> throttledClient, int batchRequestSizePerSecond) {
        this.batchRequestSizePerSecond = batchRequestSizePerSecond;
        this.throttledClient = throttledClient;
        POISON_PILL_REQUEST = poisonPillRequest;
        this.rateLimiter = RateLimiter.create(batchRequestSizePerSecond);
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Add request to the Request Queue
     * @param request - {@code Request<I>}
     * @return This implementation returns true if offer succeeds, else throws an IllegalStateException.
     */
    @Override
    public boolean add(Request<I> request) {
        logger.debug("Added: {}", request);
        return requestQueue.add(request);
    }

    /**
     * Reads response from the Response Queue. If the response matches the requestId, ensure that you remove it from the queue using
     * {@code dev.darshit.jthrottler.Throttler#remove(dev.darshit.jthrottler.Throttler.Response)}
     * @return {@code Response<J>}
     */
    @Override
    public Response<J> read() {
        return responseQueue.peek();
    }

    @Override
    public boolean remove(Response<J> response) {
        logger.debug("Removed: {}", response);
        return responseQueue.remove(response);
    }

    /**
     * Actual Throttling logic
     * Ensures that the Throttling thread does not exit unless
     * it has been poison pilled
     * AND
     * the {@code responseQueue} is empty (response is read by the calling client)
     */
    @Override
    public void run() {
        running = true;
        boolean poisonPilled = !running;
        while (!poisonPilled || !responseQueue.isEmpty()) {
            List<Request<I>> requests = new ArrayList<>(batchRequestSizePerSecond);
            requestQueue.drainTo(requests, batchRequestSizePerSecond);
            if (!requests.isEmpty()) {
                List<Request<I>> processRequests = requests.stream().filter(request -> !POISON_PILL_REQUEST.equals(request)).collect(Collectors.toList());
                if (!processRequests.isEmpty()) {
                    List<Response<J>> processed = process(processRequests);
                    responseQueue.addAll(processed);
                }
                poisonPilled = processRequests.size() != requests.size();
                if (poisonPilled) {
                    logger.debug("Poison Pill received: {}", POISON_PILL_REQUEST);
                }
            }
        }
        running = false;
        logger.debug("Throttler stopped");
    }

    private List<Response<J>> process(List<Request<I>> processRequests) {
        double time = rateLimiter.acquire(processRequests.size());
        if (time > 0) {
            logger.debug("================================");
            logger.debug("Blocked for {} s", time);
            logger.debug("================================");
        }
        return throttledClient.apply(processRequests);
    }

    /**
     * Request accepted by the Throttler
     * Contains a RequestId (String) and the Actual Request Object
     * @param <I>
     */
    public static class Request<I> {

        private final String requestId;

        private final I request;

        public Request(String requestId, I request) {
            this.requestId = requestId;
            this.request = request;
        }

        public String getRequestId() {
            return requestId;
        }

        public I getRequest() {
            return request;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Request<?> that = (Request<?>) o;
            return Objects.equals(requestId, that.requestId) && Objects.equals(request, that.request);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestId, request);
        }

        @Override
        public String toString() {
            return "Request{" +
                    "requestId='" + requestId + '\'' +
                    ", request=" + request +
                    '}';
        }
    }

    /**
     * Response returned by the Throttler once received from {@code ThrottledClient} or {@code Function}
     * @param <J>
     */
    public static class Response<J> {

        private final String requestId;

        private final J response;

        public Response(String requestId, J response) {
            this.requestId = requestId;
            this.response = response;
        }

        public String getRequestId() {
            return requestId;
        }

        public J getResponse() {
            return response;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Response<?> response1 = (Response<?>) o;
            return Objects.equals(requestId, response1.requestId) && Objects.equals(response, response1.response);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestId, response);
        }

        @Override
        public String toString() {
            return "Response{" +
                    "requestId='" + requestId + '\'' +
                    ", response=" + response +
                    '}';
        }
    }

    public static class Builder <I, J> {
        private Request<I> poisonPillRequest;
        private Function<List<Request<I>>, List<Response<J>>> throttledClient;
        private int batchRequestSizePerSecond;

        public Builder<I, J> withPoisonPillRequest(Request<I> poisonPillRequest) {
            this.poisonPillRequest = poisonPillRequest;
            return this;
        }

        public Builder<I, J> withThrottledClient(Function<List<Request<I>>, List<Response<J>>> throttledClient) {
            this.throttledClient = throttledClient;
            return this;
        }

        public Builder<I, J> withBatchRequestSizePerSecond(int batchRequestSizePerSecond) {
            this.batchRequestSizePerSecond = batchRequestSizePerSecond;
            return this;
        }

        public Builder<I, J> withFunction(Function<List<Request<I>>, List<Response<J>>> function) {
            this.throttledClient = function;
            return this;
        }

        public Throttler<I, J> build() {
            return new Throttler<>(poisonPillRequest, throttledClient, batchRequestSizePerSecond);
        }
    }
}
