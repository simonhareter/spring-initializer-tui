package org.simonhareter;

import org.simonhareter.springinit.SpringInit;
import org.simonhareter.springinit.libc.Terminal;
import org.simonhareter.springinit.libc.TerminalFactory;

public class Main {
    void main(String[] args) { 
        Terminal terminal = TerminalFactory.create();
        SpringInit springInit = new SpringInit(terminal);
        springInit.start();
    }

}
