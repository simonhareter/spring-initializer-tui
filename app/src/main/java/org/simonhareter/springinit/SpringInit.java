package org.simonhareter.springinit;

import java.io.File;
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
import org.simonhareter.springinit.util.MetaDataConfig;
import org.simonhareter.springinit.util.MetaDataOption;
import org.simonhareter.springinit.util.Project;
import org.simonhareter.springinit.util.TextField;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class SpringInit {
    private final Terminal terminal;

    private ObjectMapper mapper;
    private MetaData data;
    private MetaDataCache cache;
    private MetaDataConfig config;
    private final Path cacheFile = Path.of("cache.json");
    private final Path configFile = Path.of("config.json");
    private List<List<Integer>> menuGrid;

    private int rows, columns;

    private boolean isRunning, isEditing;
    private boolean firstRender;
    private int cursorX, cursorY;
    private int previousCursorX, previousCursorY;
    private CursorPosition textCursorPos;
    private int[] previousSelection;
    private int[] currentSelection;
    private TextField group, artifact, packageName;
    private boolean updatePackageName;

    private final String SELECTED = "\u25CF"; // ●
    private final String UNSELECTED = "\u25CB"; // ○
    private final String UNDERLINED = "\033[4m";
    private final String RESET_UNDERLINED = "\033[24m";
    private final String GREEN = "\033[38;2;109;179;63m";
    private final String BUTTON_BG_SELECTED = "\033[48;2;50;80;30m";
    private final String BUTTON_BG_UNSELECTED = "\033[48;2;33;33;48m";
    private final String RESET_BUTTON_BG = "\033[48;2;21;21;31m";
    private final String RESET_COLOR = "\033[0m";

    private final int TEXT_START = 26;

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
        enterAlternateBuffer();
        renderLoading();
        init();
        hideCursor();

        this.firstRender = true;
        renderUI();
        renderStatusBar();

        while (isRunning) {
            int key = readKey();
            boolean shouldRender = handleKey(key);
            if (shouldRender) {
                renderUI();
                renderStatusBar();
            }
        }

        leaveAlternateBuffer();
        System.exit(0);
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
        this.previousSelection = new int[10];
        this.currentSelection = new int[10];
        this.mapper = new ObjectMapper();

        terminal.enableRawMode();

        WindowSize windowSize = this.terminal.getWindowSize();
        this.rows = windowSize.rows();
        this.columns = windowSize.columns();

        if (isCacheValid()) {
            loadFromCache();
        } else {
            fetchSpringInitData();
        }

        // remove renderLoading text;
        IO.print("\033[2K");
        IO.print("\033[0G");

        this.group = new TextField(this.data.groupId().defaultValue());
        this.artifact = new TextField(this.data.artifactId().defaultValue());
        this.packageName = new TextField(this.data.packageName().defaultValue());

        if (doesConfigExist()) {
            loadConfig();
        }

        fillMenuGrid();
    }

    private boolean isCacheValid() {
        if (Files.exists(this.cacheFile)) {
            this.cache = mapper.readValue(this.cacheFile.toFile(), MetaDataCache.class);

            if (Instant.now().getEpochSecond() - this.cache.timestamp() < 86400) {
                return true;
            }
        }

        return false;
    }

    private void loadFromCache() {
        this.data = this.cache.data();

        // remove gradle-build and maven-build
        this.data.type().values().remove(2);
        this.data.type().values().remove(3);
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
            this.mapper.writerWithDefaultPrettyPrinter().writeValue(this.cacheFile, this.cache);

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

    private void loadConfig() {
        this.config = this.mapper.readValue(this.configFile.toFile(), MetaDataConfig.class);

        this.currentSelection[0] = getSelectionIndex(this.data.type().values(), this.config.type());
        this.currentSelection[1] = getSelectionIndex(this.data.language().values(), this.config.language());
        this.currentSelection[2] = getSelectionIndex(this.data.bootVersion().values(), this.config.bootVersion());

        this.group.setText(this.config.project().group());
        this.artifact.setText(this.config.project().artifact());
        this.packageName.setText(this.config.project().packageName());

        this.currentSelection[6] = getSelectionIndex(this.data.packaging().values(), this.config.packaging());
        this.currentSelection[7] = getSelectionIndex(this.data.configurationFileFormat().values(),
                this.config.configurationFileFormat());
        this.currentSelection[8] = getSelectionIndex(this.data.javaVersion().values(), this.config.javaVersion());

        // debug(Arrays.toString(this.currentSelection));
    }

    private int getSelectionIndex(List<MetaDataOption> options, String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).name().equals(value)) {
                return i;
            }
        }

        return 0;
    }

    private boolean doesConfigExist() {
        return Files.exists(this.configFile);
    }

    private void generateProject() {
        saveCurrentSelection();
    }

    private void fillMenuGrid() {
        int size = 0;

        for (int i = 0; i < 10; i++) {
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
                case 9 -> size = 1;
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
                if (this.cursorY == 9) {
                    generateProject();
                }

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
        saveCurrentSelection();
        clearScreen();
        terminal.disableRawMode();
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

    private void saveCurrentSelection() {
        MetaDataConfig config = new MetaDataConfig(
                this.data.type().values().get(this.currentSelection[0]).name(),
                this.data.language().values().get(this.currentSelection[1]).name(),
                this.data.bootVersion().values().get(this.currentSelection[2]).name(),
                new Project(
                        this.group.getText(),
                        this.artifact.getText(),
                        this.packageName.getText()),
                this.data.packaging().values().get(this.currentSelection[6]).name(),
                this.data.configurationFileFormat().values().get(this.currentSelection[7]).name(),
                this.data.javaVersion().values().get(this.currentSelection[8]).name());

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File("config.json"), config);
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
        renderGenerateButton(builder);

        IO.print(builder);
        this.firstRender = false;

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

        if (this.firstRender || selectionChanged || cursorY == selectionIndex) {
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
        renderTextField(builder, "Package name", formatPackageName(), 5);
    }

    private void renderTextField(StringBuilder builder, String title, TextField field, int selectionIndex) {
        builder.append("\033[2K");
        builder.append(String.format("    - %-14s: ", title));

        if (selectionIndex == this.cursorY) {
            builder.append("[  ");
        }

        builder.append(field.getText());

        if (selectionIndex == this.cursorY) {
            builder.append("  ]");
        }

        builder.append("\r\n\r\n");
    }

    private void writeTextField() {
        showCursor();

        StringBuilder builder = new StringBuilder(getSelectedText(this.cursorY));
        int cursorIdx = 0;

        while (this.isEditing) {
            int key = readKey();

            switch (key) {
                case 'A', 'B', '\033', '\r', '\n' -> {
                    this.isEditing = false;
                    move(key);
                }
                case 'C', 'D' -> {
                    int result = moveCursor(key, cursorIdx);
                    if (result != -1) {
                        cursorIdx = result;
                    }
                }
                case 127 -> cursorIdx = deleteChar(builder, cursorIdx);
                default -> {
                    cursorIdx = writeText(builder, cursorIdx, key);
                }
            }
        }
        hideCursor();
    }

    private int writeText(StringBuilder builder, int cursorIdx, int key) {
        builder.insert(cursorIdx, (char) key);
        cursorIdx++;
        IO.print("\033[1C");
        applyEdit(builder);
        return cursorIdx;
    }

    private int deleteChar(StringBuilder builder, int cursorIdx) {
        if (cursorIdx == builder.length() && cursorIdx > 0) {
            builder.deleteCharAt(cursorIdx - 1);
            cursorIdx--;
            IO.print("\033[1D");
        } else if (cursorIdx > 0 && cursorIdx < builder.length()) {
            builder.deleteCharAt(cursorIdx - 1);
            cursorIdx--;
            IO.print("\033[1D");
        }

        applyEdit(builder);

        return cursorIdx;
    }

    private void applyEdit(StringBuilder builder) {
        switch (this.cursorY) {
            case 3 -> {
                this.group.setText(builder.toString());
                this.updatePackageName = true;
            }
            case 4 -> {
                this.artifact.setText(builder.toString());
                this.updatePackageName = true;
            }
            default -> {
                this.packageName.setText(builder.toString());
                this.updatePackageName = false;
            }
        }

        renderEdit(builder);
    }

    private void renderEdit(StringBuilder builder) {
        saveCursor();
        positionTextCursor();
        IO.print("\033[0K");
        IO.print(builder);
        IO.print("  ]");
        restoreCursor();
    }

    private String getSelectedText(int index) {
        return switch (index) {
            case 3 -> this.group.getText();
            case 4 -> this.artifact.getText();
            default -> this.packageName.getText();
        };
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

    private TextField formatPackageName() {
        if (updatePackageName) {
            this.packageName.setText(this.group.getText() + "." + this.artifact.getText());
        }
        return this.packageName;
    }

    private int moveCursor(int c, int cursorIdx) {
        if (isIllegalCursorMove(c)) {
            return -1;
        }

        switch ((char) c) {
            case 'A' -> IO.print("\033[1A");
            case 'B' -> IO.print("\033[1B");
            case 'C' -> {
                IO.print("\033[1C");
                cursorIdx++;
            }
            case 'D' -> {
                IO.print("\033[1D");
                cursorIdx--;
            }
        }

        return cursorIdx;
    }

    private boolean isIllegalCursorMove(int c) {
        this.textCursorPos = getCursorPosition();

        int textLength = 0;

        switch (this.cursorY) {
            case 3 -> textLength = this.group.getText().length() - 1;
            case 4 -> textLength = this.artifact.getText().length() - 1;
            case 5 -> textLength = this.packageName.getText().length() - 1;
        }

        switch (c) {
            case 'D' -> {
                if (this.textCursorPos.col() <= TEXT_START - 1) {
                    return true;
                }
            }
            case 'C' -> {
                if (this.textCursorPos.col() >= TEXT_START + textLength) {
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

    private void renderGenerateButton(StringBuilder builder) {
        builder.append("\r\n");

        if (this.cursorY == 9) {
            builder.append(BUTTON_BG_SELECTED)
                    .append(" Generate ")
                    .append(RESET_COLOR);
        } else {
            builder.append(BUTTON_BG_UNSELECTED)
                    .append(" Generate ");
        }

        builder.append(RESET_BUTTON_BG);
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

    private <T> void debug(T value) {
        saveCursor();
        IO.print("\033[" + String.valueOf(this.rows) + ";0H");
        IO.print(value);
        restoreCursor();
    }

    // private void printMetaData(ObjectMapper mapper) {
    // IO.println(
    // mapper.writerWithDefaultPrettyPrinter()
    // .writeValueAsString(this.data));
    // }
}
