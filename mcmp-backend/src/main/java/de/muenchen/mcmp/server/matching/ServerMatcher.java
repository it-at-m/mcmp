package de.muenchen.mcmp.server.matching;

import de.muenchen.mcmp.server.Server;
import org.springframework.lang.Nullable;

public interface ServerMatcher<T> {

    @Nullable
    Server match(T data);
}
