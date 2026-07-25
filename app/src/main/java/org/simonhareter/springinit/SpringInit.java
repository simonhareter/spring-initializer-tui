package org.simonhareter.springinit;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.simonhareter.springinit.dtos.MetaData;
import org.simonhareter.springinit.libc.Terminal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class SpringInit {
    private final Terminal terminal;

    private boolean isRunning;
    private int cursorX, cursorY;
    private MetaData data;
    private List<List<Integer>> menuGrid;

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
        this.menuGrid = new ArrayList<>();
        fetchSpringInitData();
        fillMenuGrid();
        IO.print(this.menuGrid);
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

            con.disconnect();
        } catch (MalformedURLException e) {
            IO.println("Malformed URL: " + e.getMessage());
        } catch (URISyntaxException e) {
            IO.println("UriSyntaxException: " + e.getMessage());
        } catch (IOException e) {
            IO.println("IOException: " + e.getMessage());
        }

    }

    private void fillMenuGrid() {
        int size = 0;

        for (int i = 0; i < 9; i++) {
            switch (i) {
                case 0 -> {
                    // ignore gradle-build and maven-build
                    int ignoreOptionsSize = 2;
                    size = this.data.type().values().size() - ignoreOptionsSize;
                }
                case 1 -> {
                    size = this.data.language().values().size();
                }
                case 2 -> {
                    size = this.data.bootVersion().values().size();
                }
                case 3, 4, 5 -> size = 1;
                case 6 -> {
                    size = this.data.packaging().values().size();
                }
                case 7 -> {
                    size = this.data.configurationFileFormat().values().size();
                }
                case 8 -> {
                    size = this.data.javaVersion().values().size();
                }
            }

            List<Integer> list = createRangeList(size);
            this.menuGrid.add(list);
        }
    }

    private List<Integer> createRangeList(int size) {
        List<Integer> list = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            list.add(i);
        }

        return list;
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

        int newRow = this.cursorY;
        int newCol = this.cursorX;

        switch (dir) {
            case UP -> newRow--;
            case DOWN -> newRow++;
            case LEFT -> newCol--;
            case RIGHT -> newCol++;
        }

        if (isIllegalMove(newRow, newCol)) {
            return;
        }

        this.cursorY = newRow;
        this.cursorX = newCol;

        IO.print(this.cursorX + " : " + this.cursorY + "\r\n");
    }

    private Direction getDirection(int key) {
        return switch (key) {
            case 'D', 'h' -> Direction.LEFT;
            case 'B', 'j' -> Direction.DOWN;
            case 'A', 'k' -> Direction.UP;
            default -> Direction.RIGHT;
        };
    }

    private boolean isIllegalMove(int newRow, int newCol) {
       if(newRow < 0 || newRow >= this.menuGrid.size()) {
           return true;
       }

       if(newCol < 0 || newCol >= this.menuGrid.get(newRow).size()) {
           return true;
       }

       return false;
    }

    private void select() {
        IO.println("select");
    }

    private void reset() {
        IO.println("reset");
        this.cursorX = 0;
        this.cursorY = 0;
    }

    private void printMetaData(ObjectMapper mapper) {
        IO.println(
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(this.data));
    }
}
