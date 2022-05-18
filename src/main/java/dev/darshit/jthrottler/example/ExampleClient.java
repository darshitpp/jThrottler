package dev.darshit.jthrottler.example;

import dev.darshit.jthrottler.ThrottledClient;
import dev.darshit.jthrottler.Throttler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class ExampleClient implements ThrottledClient<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(ExampleClient.class);

    @Override
    public List<Throttler.Response<String>> apply(List<Throttler.Request<String>> requests) {
        logger.debug("-------- Executing on Client -----------");
        return requests.stream()
                .map(request -> new Throttler.Response<>(request.getRequestId(), request.getRequest().replace("Request", "Response")))
                .peek(response -> logger.debug(String.valueOf(response)))
                .collect(Collectors.toList());
    }
}
