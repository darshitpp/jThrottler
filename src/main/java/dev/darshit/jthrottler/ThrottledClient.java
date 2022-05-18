package dev.darshit.jthrottler;

import java.util.List;
import java.util.function.Function;

/**
 * Wrapper over {@code Function<List<Throttler.Request<K>>, List<Throttler.Response<R>>>}
 * @param <K>
 * @param <R>
 */
public interface ThrottledClient<K, R> extends Function<List<Throttler.Request<K>>, List<Throttler.Response<R>>> {

}
