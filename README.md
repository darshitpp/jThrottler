# jThrottler

A client-side configurable API request throttler

* Does your application break Third Party API Rate Limits?
* Is your application unable to serve requests when the Rate Limit exceeds in the Third Party API?
* Does the Third Party API provide batch requests?

If the answers to the above questions is true, this project will help you solve the problem

## Usage

Two classes can be used
1. `Throttler`
2. `ThrottledClient` (or optionally provide your own `Function` implementation or Lambda)

### Steps:

Initialize a single instance of `Throttler`
```java
Throttler<I, J> throttler = new Throttler.Builder<I, J>()
                .withPoisonPillRequest(POISON_PILL)
                .withThrottledClient(throttledClient)
                .withBatchRequestSizePerSecond(BATCH_REQUEST_SIZE_PER_SECOND)
                .build();
```
and start the Throttler instance in a thread.

* `throttledClient` is an instance of `ThrottledClient` or `Function`
* `POISON_PILL` is an instance of `Throttler.Request<I>` which will stop the throttling from the thread when received
* `BATCH_REQUEST_SIZE_PER_SECOND` is the size for which you want to batch your requests for per second

Use the `Throttler.add()` method to add requests to throttle.
`ThrottleClient` should have the implementation of calling the Third Party API in Bulk, and converting the Bulk response into individual `Throttler.Response` objects.
Response is available from the `Throttler.read()` method to read; ensure that you `Throttler.remove` it from the queue if the `Throttler.Response.requestId` matches `Throttler.Request.requestId`.

Check the JUnit Tests for more information regarding usage.