package org.simonhareter.springinit.util;

public class TextField {
    private char[] text;

    public TextField(String text) {
        this.text = text.toCharArray();
    }

	public String getText() {
		return String.valueOf(this.text);
	}
}
