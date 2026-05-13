package de.muenchen.mcmp.sleeper;

import org.springframework.stereotype.Component;

/**
 * The ThreadSleeper class is an implementation of the Sleeper interface.
 * This class uses the {@link Thread#sleep(long)} method to pause the
 * current thread for a specified duration in milliseconds.
 * <p>
 * The sleep operation is wrapped in a try-catch block and appropriately
 * handles any {@link InterruptedException} that may be thrown, ensuring
 * that the interrupt flag is re-set on the thread after being interrupted.
 */
@Component
public class ThreadSleeper implements Sleeper {
    @Override
    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
