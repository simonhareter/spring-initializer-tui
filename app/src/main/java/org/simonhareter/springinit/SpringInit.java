package org.simonhareter.springinit;

import org.simonhareter.springinit.libc.Terminal;

public class SpringInit {
    private final Terminal terminal;

    public SpringInit(Terminal terminal) {
        this.terminal = terminal;
    }

    public void start() {
        IO.println("Hello Spring initializer");
        terminal.enableRawMode();
        terminal.disableRawMode();
    }

}
