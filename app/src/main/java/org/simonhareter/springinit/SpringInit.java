package org.simonhareter.springinit;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.simonhareter.springinit.dtos.MetaData;
import org.simonhareter.springinit.dtos.MetaDataCache;
import org.simonhareter.springinit.libc.Terminal;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class SpringInit {
    private final Terminal terminal;

    private ObjectMapper mapper;
    private MetaData data;
    private MetaDataCache cache;
    private Path cacheFile = Path.of("cache.json");
    private List<List<Integer>> menuGrid;

    private boolean isRunning;
    private int cursorX, cursorY;
    private int[] currentSelection;
    private String group, artifact, packageName;
    private String[] logo = {
            "  ____             _                  ___       _ _   _       _ _          ",
            " / ___| _ __  _ __(_)_ __   __ _     |_ _|_ __ (_) |_(_) __ _| (_)_____ __ ",
            " \\___ \\| '_ \\| '__| | '_ \\ / _` |_____| || '_ \\| | __| |/ _` | | |_  / '__|",
            "  ___) | |_) | |  | | | | | (_| |_____| || | | | | |_| | (_| | | |/ /| |   ",
            " |____/| .__/|_|  |_|_| |_|\\__, |    |___|_| |_|_|\\__|_|\\__,_|_|_/___|_|   ",
            "       |_|                 |___/                                            "
    };

    public SpringInit(Terminal terminal) {
        this.terminal = terminal;
    }

    public void start() {
        clearScreen();
        renderLoading();
        init();
        clearScreen();

        while (isRunning) {
            renderUI();
            int key = readKey();
            handleKey(key);
            clearScreen();
            // IO.print(this.cursorY + " : " + this.cursorX + "\r\n");
        }
    }

    private void init() {
        this.isRunning = true;
        this.cursorX = 0;
        this.cursorY = 0;
        this.menuGrid = new ArrayList<>();
        this.currentSelection = new int[6];
        this.mapper = new ObjectMapper();

        if (Files.exists(cacheFile)) {
            this.cache = mapper.readValue(cacheFile.toFile(), MetaDataCache.class);

            if (Instant.now().getEpochSecond() - this.cache.timestamp() < 86400) {
                this.data = this.cache.data();
            } else {
                fetchSpringInitData();
            }
        } else {
            fetchSpringInitData();
        }

        fillMenuGrid();

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

            JsonNode json = this.mapper.readTree(stream);

            this.data = this.mapper.treeToValue(json, MetaData.class);

            this.cache = new MetaDataCache(Instant.now().getEpochSecond(), this.data);
            this.mapper.writeValue(this.cacheFile, this.cache);

            // remove gradle-build and maven-build
            this.data.type().values().remove(2);
            this.data.type().values().remove(3);

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
                    size = this.data.type().values().size();
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
        clearScreen();
        terminal.disableRawMode();
        System.exit(0);
    }

    private void clearScreen() {
        System.out.print("\033[1J"); // clear screen
        System.out.print("\033[H"); // reset cursor
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
        if (newCol >= this.menuGrid.get(newRow).size()) {
            this.cursorX = this.menuGrid.get(newRow).size() - 1;
        } else {
            this.cursorX = newCol;
        }

        // IO.print(this.cursorX + " : " + this.cursorY + "\r\n");
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
        if (newRow < 0 || newRow >= this.menuGrid.size()) {
            return true;
        } else if (this.cursorY == newRow && (newCol < 0 || newCol >= this.menuGrid.get(newRow).size())) {
            return true;
        } else {
            return false;
        }
    }

    private void select() {
        IO.println("select");
    }

    private void reset() {
        IO.println("reset");
        this.cursorX = 0;
        this.cursorY = 0;
    }

    private void renderLoading() {
        StringBuilder builder = new StringBuilder();
        builder = renderLogo(builder);

        builder.append("\r\n");
        builder.append("\r\n");

        builder.append("Loading metadata...");
        IO.print(builder);
    }

    private StringBuilder renderLogo(StringBuilder builder) {
        String GREEN = "\033[38;2;109;179;63m";
        String RESET = "\033[0m";

        for (String line : this.logo) {
            builder.append(GREEN)
                    .append(line.substring(0, 35))
                    .append(RESET)
                    .append(line.substring(35))
                    .append("\r\n");
        }
        builder.append("\r\n");
        return builder;
    }

    private void renderUI() {
        String selected = "\u25CF"; // ●
        String unselected = "\u25CB"; // ○

        StringBuilder builder = new StringBuilder();

        String GREEN = "\033[38;2;109;179;63m";
        String RESET = "\033[0m";

        for (String line : this.logo) {
            builder.append(GREEN)
                    .append(line.substring(0, 35))
                    .append(RESET)
                    .append(line.substring(35))
                    .append("\r\n");
        }
        builder.append("\r\n");

        builder.append("Project\r\n");
        builder.append("\r\n");

        for (int i = 0; i < this.data.type().values().size(); i++) {
            if (i == currentSelection[0]) {
                builder.append(GREEN + selected + " " + this.data.type().values().get(i).name() + RESET + "  ");
            } else {
                builder.append(unselected + " " + this.data.type().values().get(i).name() + "  ");
            }
        }

        builder.append("\r\n");
        builder.append("\r\n");

        builder.append("Language\r\n");
        builder.append("\r\n");

        for (int i = 0; i < this.data.language().values().size(); i++) {
            if (i == currentSelection[1]) {
                builder.append(GREEN + selected + " " + this.data.language().values().get(i).name() + RESET + "  ");
            } else {
                builder.append(unselected + " " + this.data.language().values().get(i).name() + "  ");
            }
        }

        builder.append("\r\n");
        builder.append("\r\n");

        builder.append("Spring Boot\r\n");
        builder.append("\r\n");

        for (int i = 0; i < this.data.bootVersion().values().size(); i++) {
            if (i == currentSelection[2]) {
                builder.append(GREEN + selected + " " + this.data.bootVersion().values().get(i).name() + RESET + "  ");
            } else {
                builder.append(unselected + " " + this.data.bootVersion().values().get(i).name() + "  ");
            }
        }

        builder.append("\r\n");
        builder.append("\r\n");

        builder.append("Project Metadata\r\n");
        builder.append("\r\n");
        builder.append("Group\r\n");
        builder.append("Artifact\r\n");
        builder.append("Package name\r\n");

        builder.append("\r\n");

        builder.append("Packaging\r\n");
        builder.append("\r\n");

        for (int i = 0; i < this.data.packaging().values().size(); i++) {
            if (i == currentSelection[3]) {
                builder.append(GREEN + selected + " " + this.data.packaging().values().get(i).name() + RESET + "  ");
            } else {
                builder.append(unselected + " " + this.data.packaging().values().get(i).name() + "  ");
            }
        }

        builder.append("\r\n");
        builder.append("\r\n");

        builder.append("Configuration\r\n");
        builder.append("\r\n");

        for (int i = 0; i < this.data.configurationFileFormat().values().size(); i++) {
            if (i == currentSelection[4]) {
                builder.append(GREEN + selected + " " + this.data.configurationFileFormat().values().get(i).name()
                        + RESET + "  ");
            } else {
                builder.append(unselected + " " + this.data.configurationFileFormat().values().get(i).name() + "  ");
            }
        }

        builder.append("\r\n");
        builder.append("\r\n");

        builder.append("Java\r\n");
        builder.append("\r\n");

        for (int i = 0; i < this.data.javaVersion().values().size(); i++) {
            if (i == currentSelection[5]) {
                builder.append(GREEN + selected + " " + this.data.javaVersion().values().get(i).name() + RESET + "  ");
            } else {
                builder.append(unselected + " " + this.data.javaVersion().values().get(i).name() + "  ");
            }
        }

        IO.print(builder);
    }

    // private void printMetaData(ObjectMapper mapper) {
    // IO.println(
    // mapper.writerWithDefaultPrettyPrinter()
    // .writeValueAsString(this.data));
    // }
}
