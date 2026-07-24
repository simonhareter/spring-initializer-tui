package org.simonhareter;

import org.simonhareter.springinit.SpringInit;

public class Main {
    void main(String[] args) {
        IO.println("Hello spring initializer tui");
        SpringInit springInit = new SpringInit();
        springInit.start();
    }
}
