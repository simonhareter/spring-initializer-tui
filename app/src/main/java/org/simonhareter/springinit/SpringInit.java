package org.simonhareter.springinit;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.simonhareter.springinit.libc.Terminal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class SpringInit {
    private final Terminal terminal;

    private boolean isRunning;
    private int cursorX, cursorY;
    private MetaData data;

    public SpringInit(Terminal terminal) {
        this.terminal = terminal;
    }

    public void start() {
        init();

        while (isRunning) {
            int key = readKey();
            handleKey(key);
        }
    }

    private void init() {
        this.isRunning = true;
        this.cursorX = 0;
        this.cursorY = 0;
        fetchSpringInitData();
        terminal.enableRawMode();
    }

    private void fetchSpringInitData() {
        try {
            URL url = new URI("https://start.spring.io").toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            con.setRequestProperty("Accept", "application/vnd.initializr.v2.3+json");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);

            int status = con.getResponseCode();

            InputStream stream = status > 299 ? con.getErrorStream() : con.getInputStream();

            ObjectMapper mapper = new ObjectMapper();

            JsonNode json = mapper.readTree(stream);

            this.data = mapper.treeToValue(json, MetaData.class);

            IO.println(data.language());
            con.disconnect();
        } catch (MalformedURLException e) {
            IO.println("Malformed URL: " + e.getMessage());
        } catch (URISyntaxException e) {
            IO.println("UriSyntaxException: " + e.getMessage());
        } catch (IOException e) {
            IO.println("IOException: " + e.getMessage());
        }

    }

    private int readKey() {
        try {
            int key = System.in.read();
            // \033 = escape character (decimal value 27)
            if (key != '\033') {
                return key;
            }

            int key2 = System.in.read();
            if (key2 != '[' && key2 != 'O') {
                return key2;
            }

            return System.in.read();
        } catch (IOException e) {
            IO.println("Error reading key");
            return -1;
        }
    }

    private void handleKey(int key) {
        switch (key) {
            case 'q', -1, 3 -> quit();
            case '\r', '\n', ' ' -> select();
            case 'r' -> reset();
            case 'D', 'h', 'B', 'j', 'A', 'k', 'C', 'l' -> move(key);
        }
    }

    private void quit() {
        this.isRunning = false;
        System.out.print("\033[2J"); // clear screen
        System.out.print("\033[H"); // reset cursor
        terminal.disableRawMode();
        System.exit(0);
    }

    private void move(int key) {
        Direction dir = getDirection(key);
        switch (dir) {
            case UP -> this.cursorY--;
            case DOWN -> this.cursorY++;
            case LEFT -> this.cursorX--;
            case RIGHT -> this.cursorX++;
        }
        IO.print("move");
        IO.println(this.cursorX + ":" + this.cursorY);
    }

    private Direction getDirection(int key) {
        return switch (key) {
            case 'D', 'h' -> Direction.LEFT;
            case 'B', 'j' -> Direction.DOWN;
            case 'A', 'k' -> Direction.UP;
            default -> Direction.RIGHT;
        };
    }

    private void select() {
        IO.println("select");
    }

    private void reset() {
        IO.println("reset");
        this.cursorX = 0;
        this.cursorY = 0;
    }
}
