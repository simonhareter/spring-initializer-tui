package org.simonhareter.springinit.libc;

import static com.sun.jna.Platform.isLinux;
import static com.sun.jna.Platform.isMac;

public final class TerminalFactory {
    private TerminalFactory() {
    }

    public static Terminal create() {
        if (isLinux()) {
            return new UnixTerminal();
        } else if (isMac()) {
            return new MacTerminal();
        } else {
            return new WindowsTerminal();
        }
    }
}
