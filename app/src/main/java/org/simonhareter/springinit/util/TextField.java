package org.simonhareter.springinit.util;

public class TextField {
    private String text;

    public TextField(String text) {
        this.text = text;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
