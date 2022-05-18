package dev.darshit.jthrottler;

public interface ThrottledClientOperations<I, J> {

    boolean add(Throttler.Request<I> i);

    Throttler.Response<J> read();

    boolean remove(Throttler.Response<J> response);

}
