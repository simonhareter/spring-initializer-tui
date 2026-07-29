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
import java.util.Arrays;
import java.util.List;
import org.simonhareter.springinit.libc.Terminal;
import org.simonhareter.springinit.libc.WindowSize;
import org.simonhareter.springinit.util.CursorPosition;
import org.simonhareter.springinit.util.Direction;
import org.simonhareter.springinit.util.MetaData;
import org.simonhareter.springinit.util.MetaDataCache;
import org.simonhareter.springinit.util.MetaDataOption;
import org.simonhareter.springinit.util.TextField;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class SpringInit {
    private final Terminal terminal;

    private ObjectMapper mapper;
    private MetaData data;
    private MetaDataCache cache;
    private final Path cacheFile = Path.of("cache.json");
    private List<List<Integer>> menuGrid;

    private int rows, columns;

    private boolean isRunning, isEditing;
    private int cursorX, cursorY;
    private int previousCursorX, previousCursorY;
    private CursorPosition textCursorPos;
    private int[] previousSelection;
    private int[] currentSelection;
    private TextField group, artifact, packageName;

    private final String SELECTED = "\u25CF"; // ●
    private final String UNSELECTED = "\u25CB"; // ○
    private final String UNDERLINED = "\033[4m";
    private final String RESET_UNDERLINED = "\033[24m";
    private final String GREEN = "\033[38;2;109;179;63m";
    private final String RESET_COLOR = "\033[0m";

    private final int TEXT_START = 25;

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
        enterAlternateBuffer();
        clearScreen();
        renderLoading();
        init();
        hideCursor();

        Arrays.fill(this.previousSelection, 1);
        renderUI();
        renderStatusBar();
        Arrays.fill(this.previousSelection, 0);

        while (isRunning) {
            int key = readKey();
            boolean shouldRender = handleKey(key);
            if (shouldRender) {
                renderUI();
                renderStatusBar();
            }
        }

        leaveAlternateBuffer();
    }

    private void enterAlternateBuffer() {
        IO.print("\033[?47h");
        IO.print("\033[?1049h");
    }

    private void leaveAlternateBuffer() {
        IO.print("\033[?47l");
        IO.print("\033[?1049l");
    }

    private void init() {
        this.isRunning = true;
        this.cursorX = 0;
        this.cursorY = 0;
        this.menuGrid = new ArrayList<>();
        this.previousSelection = new int[9];
        this.currentSelection = new int[9];
        this.mapper = new ObjectMapper();

        if (Files.exists(cacheFile)) {
            this.cache = mapper.readValue(cacheFile.toFile(), MetaDataCache.class);

            if (Instant.now().getEpochSecond() - this.cache.timestamp() < 86400) {
                this.data = this.cache.data();
                // remove gradle-build and maven-build
                this.data.type().values().remove(2);
                this.data.type().values().remove(3);
            } else {
                fetchSpringInitData();
            }
        } else {
            fetchSpringInitData();
        }

        IO.print("\033[2K");
        IO.print("\033[0G");

        this.group = new TextField(this.data.groupId().defaultValue());
        this.artifact = new TextField(this.data.artifactId().defaultValue());
        this.packageName = new TextField(this.data.packageName().defaultValue());

        fillMenuGrid();

        terminal.enableRawMode();

        WindowSize windowSize = this.terminal.getWindowSize();
        this.rows = windowSize.rows();
        this.columns = windowSize.columns();
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

            if (System.in.available() == 0) {
                return '\033';
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

    private boolean handleKey(int key) {
        switch (key) {
            case 'q', -1, 3 -> {
                quit();
                return true;
            }
            case 'r' -> {
                reset();
                return true;
            }
            case 'D', 'h', 'C', 'l', 'B', 'j', 'A', 'k' -> {
                move(key);
                return true;
            }
            case 'i', 'e', '\r', '\n' -> {
                if ((key == '\r' || key == '\n') && !isTextFieldSelected()) {
                    return false;
                }
                this.isEditing = true;
                renderStatusBar();
                writeTextField();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void quit() {
        this.isRunning = false;
        clearScreen();
        terminal.disableRawMode();
        System.exit(0);
    }

    private void clearScreen() {
        System.out.print("\033[2J"); // clear screen
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

        this.previousCursorX = this.cursorX;
        this.previousCursorY = this.cursorY;

        this.cursorY = newRow;
        if (newRow != previousCursorY) {
            this.cursorX = currentSelection[newRow];
        } else {
            this.cursorX = newCol;
        }

        updateSelection();
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

    private void updateSelection() {
        System.arraycopy(this.currentSelection, 0, this.previousSelection, 0, this.currentSelection.length);
        this.currentSelection[this.cursorY] = this.cursorX;
    }

    private void reset() {
        IO.println("reset");
        this.cursorX = 0;
        this.cursorY = 0;
    }

    private void renderLoading() {
        StringBuilder builder = new StringBuilder();
        renderLogo(builder);

        builder.append("\r\n");

        builder.append("Loading metadata...");
        IO.print(builder);
    }

    private void renderLogo(StringBuilder builder) {
        for (String line : this.logo) {
            builder.append(GREEN)
                    .append(line, 0, 35)
                    .append(RESET_COLOR)
                    .append(line.substring(35))
                    .append("\r\n");
        }
        builder.append("\r\n");
    }

    private void renderUI() {
        hideCursor();
        skipLogo();

        StringBuilder builder = new StringBuilder();

        renderProject(builder);
        renderLanguage(builder);
        renderBootVersion(builder);
        renderProjectMetaData(builder);
        renderPackaging(builder);
        renderConfiguration(builder);
        renderJavaVersion(builder);

        IO.print(builder);
        if (isTextFieldSelected()) {
            positionTextCursor();
            showCursor();
        } else {
            hideCursor();
        }
    }

    private void renderSelectionRow(StringBuilder builder, String title, List<MetaDataOption> options,
            int selectionIndex) {

        boolean selectionChanged = currentSelection[selectionIndex] != previousSelection[selectionIndex];
        boolean isPreviousRow = this.previousCursorY == selectionIndex;
        boolean isUnderlined = this.cursorY == selectionIndex;

        if (selectionChanged || cursorY == selectionIndex) {
            builder.append(title).append("\r\n\r\n");
            renderOptions(builder, options, selectionIndex, isUnderlined);
        } else if (isPreviousRow) {
            builder.append(title).append("\r\n\r\n");
            renderOptions(builder, options, selectionIndex, false);
        } else {
            builder.append("\r\033[2B");
        }

        builder.append("\r\n\r\n");
    }

    private void renderOptions(StringBuilder builder, List<MetaDataOption> options, int selectionIndex,
            boolean isUnderlined) {

        for (int i = 0; i < options.size(); i++) {
            if (i == currentSelection[selectionIndex]) {
                if (isUnderlined) {
                    builder.append(UNDERLINED);
                }
                builder.append(GREEN + SELECTED + " ")
                        .append(options.get(i).name())
                        .append(RESET_COLOR + "  ");
                if (isUnderlined) {
                    builder.append(RESET_UNDERLINED);
                }
            } else {
                builder.append(UNSELECTED + " ").append(options.get(i).name()).append("  ");
            }
        }
    }

    private void renderProject(StringBuilder builder) {
        int selectionIndex = 0;
        renderSelectionRow(builder, "Project", this.data.type().values(), selectionIndex);
    }

    private void renderLanguage(StringBuilder builder) {
        int selectionIndex = 1;
        renderSelectionRow(builder, "Language", this.data.language().values(), selectionIndex);
    }

    private void renderBootVersion(StringBuilder builder) {
        int selectionIndex = 2;
        renderSelectionRow(builder, "Spring Boot", this.data.bootVersion().values(), selectionIndex);
    }

    private void renderProjectMetaData(StringBuilder builder) {
        builder.append("Project Metadata\r\n\r\n");

        renderTextField(builder, "Group", this.group, 3);
        renderTextField(builder, "Artifact", this.artifact, 4);
        renderTextField(builder, "Package name", this.packageName, 5);
    }

    private void renderTextField(StringBuilder builder, String title, TextField field, int selectionIndex) {
        builder.append("\033[2K");
        builder.append(String.format("    - %-14s: ", title));

        if (selectionIndex == this.cursorY) {
            builder.append("[ ");
        }

        builder.append(field.getText());

        if (selectionIndex == this.cursorY) {
            builder.append(" ]");
        }

        builder.append("\r\n\r\n");
    }

    private void writeTextField() {
        showCursor();

        while (this.isEditing) {
            int key = readKey();

            switch (key) {
                case 'A', 'B', '\033', '\r', '\n' -> {
                    this.isEditing = false;
                    move(key);
                }
                case 'C', 'D', 127 -> moveCursor(key);
                default -> {
                    switch (this.cursorY) {
                        case 3 -> {

                        }
                        case 4 -> {

                        }
                        case 5 -> {

                        }
                    }
                    IO.print((char) key);
                }
            }
        }
        hideCursor();
    }

    private boolean isTextFieldSelected() {
        return this.cursorY >= 3 && this.cursorY <= 5;
    }

    private void positionTextCursor() {
        switch (this.cursorY) {
            case 3 -> IO.print("\033[23;" + TEXT_START + "H");
            case 4 -> IO.print("\033[25;" + TEXT_START + "H");
            case 5 -> IO.print("\033[27;" + TEXT_START + "H");
        }
    }

    private String formatPackageName() {
        return this.group.getText() + "." + this.artifact.getText();
    }

    private void moveCursor(int c) {
        if (isIllegalCursorMove(c)) {
            return;
        }

        switch ((char) c) {
            case 'A' -> IO.print("\033[" + c);
            case 'B' -> IO.print("\033[" + c);
            case 'C' -> IO.print("\033[" + c);
            case 'D' -> IO.print("\033[" + c);
        }
    }

    private boolean isIllegalCursorMove(int c) {
        this.textCursorPos = getCursorPosition();
        IO.print(c);

        switch (c) {
            case 'D', 127 -> {
                if (this.textCursorPos.col() <= TEXT_START) {
                    return true;
                }
            }
            case 'C' -> {
                if (this.textCursorPos.col() >= TEXT_START + this.group.getText().length()) {
                    IO.print("not the end");
                    return true;
                }
            }
        }

        return false;
    }

    private CursorPosition getCursorPosition() {
        int row = 0, col = 0;
        int c;

        IO.print("\033[6n");
        System.out.flush();

        try {
            if (System.in.read() != '\033') {
                throw new IOException("Expected ESC character");
            }

            if (System.in.read() != '[') {
                throw new IOException("Expected [");
            }

            while ((c = System.in.read()) != ';') {
                row = row * 10 + (c - '0');
            }

            while ((c = System.in.read()) != 'R') {
                col = col * 10 + (c - '0');
            }

        } catch (IOException e) {
            IO.print(e);
        }

        return new CursorPosition(row - 1, col - 1);
    }

    private void saveCursor() {
        IO.print("\033[s");
    }

    private void restoreCursor() {
        IO.print("\033[u");
    }

    private void renderPackaging(StringBuilder builder) {
        int selectionIndex = 6;
        renderSelectionRow(builder, "Packaging", this.data.packaging().values(), selectionIndex);
    }

    private void renderConfiguration(StringBuilder builder) {
        int selectionIndex = 7;
        renderSelectionRow(builder, "Configuration", this.data.configurationFileFormat().values(),
                selectionIndex);
    }

    private void renderJavaVersion(StringBuilder builder) {
        int selectionIndex = 8;
        renderSelectionRow(builder, "Java", this.data.javaVersion().values(), selectionIndex);
    }

    private void renderStatusBar() {
        saveCursor();

        StringBuilder builder = new StringBuilder();

        String mode, hints = "↑↓ Navigate   ←→ Change   Enter Edit   Esc Back   Ctrl+C Exit";

        if (this.isEditing) {
            mode = "INSERT";
        } else {
            mode = "NORMAL";
        }

        int spaces = this.columns - mode.length() - hints.length();

        builder.append("\033[" + String.valueOf(this.rows - 1) + ";0H");
        builder.append(mode);

        if (spaces < 1) {
            builder.append(" ");
        } else {
            builder.append(" ".repeat(spaces));
        }

        builder.append(hints);

        IO.print(builder);
        restoreCursor();
    }

    private void skipLogo() {
        IO.print("\033[9;0H");
    }

    private void hideCursor() {
        IO.print("\033[?25l");
    }

    private void showCursor() {
        IO.print("\033[?25h");
    }

    // private void printMetaData(ObjectMapper mapper) {
    // IO.println(
    // mapper.writerWithDefaultPrettyPrinter()
    // .writeValueAsString(this.data));
    // }
}
