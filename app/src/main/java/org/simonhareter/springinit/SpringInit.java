package org.simonhareter.springinit;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.simonhareter.springinit.libc.Terminal;
import org.simonhareter.springinit.libc.WindowSize;
import org.simonhareter.springinit.util.CursorPosition;
import org.simonhareter.springinit.util.Dialog;
import org.simonhareter.springinit.util.Direction;
import org.simonhareter.springinit.util.MetaData;
import org.simonhareter.springinit.util.MetaDataCache;
import org.simonhareter.springinit.util.MetaDataConfig;
import org.simonhareter.springinit.util.MetaDataOption;
import org.simonhareter.springinit.util.Project;
import org.simonhareter.springinit.util.SectionLayout;
import org.simonhareter.springinit.util.TextField;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class SpringInit {
    private final Terminal terminal;
    private final ObjectMapper mapper;
    private final List<List<Integer>> menuGrid;
    private final int[] previousSelection;
    private final int[] currentSelection;
    private final String version = "0.0.1";

    private MetaData data;
    private MetaDataCache cache;
    private MetaDataConfig config;

    private final Path home = Path.of(System.getProperty("user.home"));
    private final Path configDir = this.home.resolve(".config").resolve("spring-initializr-tui");
    private final Path cacheDir = this.home.resolve(".cache").resolve("spring-initializr-tui");
    private final Path configFile = this.configDir.resolve("config.json");
    private final Path cacheFile = this.cacheDir.resolve("cache.json");

    private WindowSize windowSize;
    private int rows, columns;

    private boolean isRunning, isEditing, isPostGenMenuRunning, isAddDependencyRunning, updatePackageName, isDimmed,
            firstRender;
    private int postGenMenuIndex = 0;

    // Cursor position inside the menu grid
    private int cursorX = 0, cursorY = 0, previousCursorY;

    // Virtual cursor position
    private int contentHeight = 0, scrollOffset = 0, scrollCursorY = 10, viewPortHeight, statusBarHeight = 1,
            debugHeight = 1;
    private final int SCROLL_MARGIN = 5;
    private int logoHeight = 8, projectHeight = 4, languageHeight = 4, bootVersionHeight = 6, groupHeight = 2,
            artifactHeight = 2, packageNameHeight = 2, packagingHeight = 4,
            configurationHeight = 4, javaVersionHeight = 4,
            addDepHeight = 2, generateHeight = 1, postGenHeight = 3;
    private SectionLayout logoL, project, language, bootVersion, groupL, artifactL, packageNameL,
            packaging, configuration, javaVersion, addDep, generate, postGen;
    private SectionLayout[] sections;

    private CursorPosition textCursorPos;
    private TextField group, artifact, packageName;
    private Dialog dependencyDialog;

    private final String SELECTED = "\u25CF"; // ●
    private final String UNSELECTED = "\u25CB"; // ○
    private final String UNDERLINED = "\033[4m";
    private final String RESET_UNDERLINED = "\033[24m";
    private final String GREEN = "\033[38;2;109;179;63m";
    private final String RED = "\033[38;2;220;50;47m";
    private final String BG = "\033[48;2;21;21;31m";
    private final String BG_DIMMED = "\033[48;2;10;10;20m";
    private final String DIMMED = "\033[2m";
    private final String RESET_DIMMED = "\033[22m";
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
        this.mapper = new ObjectMapper();
        this.menuGrid = new ArrayList<>();
        this.previousSelection = new int[11];
        this.currentSelection = new int[11];
    }

    public void start() {
        enterAlternateBuffer();
        renderLoading();
        init();
        renderLogo();
        hideCursor();
        renderUI();
        renderStatusBar();
        calculateContentHeight();

        this.sections = new SectionLayout[] {
                this.project,
                this.language,
                this.bootVersion,
                this.groupL,
                this.artifactL,
                this.packageNameL,
                this.packaging,
                this.configuration,
                this.javaVersion,
                this.addDep,
                this.generate,
                this.postGen
        };

        while (this.isRunning) {
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
        this.firstRender = true;

        terminal.enableRawMode();

        this.windowSize = this.terminal.getWindowSize();
        this.rows = this.windowSize.rows();
        this.columns = this.windowSize.columns();

        this.viewPortHeight = this.rows - this.statusBarHeight - this.debugHeight;

        int width = (int) (this.columns * 0.8);
        int height = (int) (this.rows * 0.8);

        // ANSI is 1-based, so + 1
        int x = (this.columns - width) / 2 + 1;
        int y = (this.rows - height) / 2 + 1;

        this.dependencyDialog = new Dialog(x, y, width, height);

        if (isCacheValid()) {
            loadFromCache();
        } else {
            fetchSpringInitData();
        }

        debug("");

        this.group = new TextField(this.data.groupId().defaultValue());
        this.artifact = new TextField(this.data.artifactId().defaultValue());
        this.packageName = new TextField(this.data.packageName().defaultValue());

        if (doesConfigExist()) {
            loadConfig();
        }

        fillMenuGrid();
    }

    private void calculateContentHeight() {
        int row = 0;

        this.logoL = new SectionLayout(row, this.logoHeight);
        row += this.logoL.height();

        this.project = new SectionLayout(row, this.projectHeight);
        row += this.project.height();

        this.language = new SectionLayout(row, this.languageHeight);
        row += this.language.height();

        this.bootVersion = new SectionLayout(row, this.bootVersionHeight);
        row += this.bootVersion.height();

        this.groupL = new SectionLayout(row, this.groupHeight);
        row += this.groupL.height();

        this.artifactL = new SectionLayout(row, this.artifactHeight);
        row += this.artifactL.height();

        this.packageNameL = new SectionLayout(row, this.packageNameHeight);
        row += this.packageNameL.height();

        this.packaging = new SectionLayout(row, this.packagingHeight);
        row += this.packaging.height();

        this.configuration = new SectionLayout(row, this.configurationHeight);
        row += this.configuration.height();

        this.javaVersion = new SectionLayout(row, this.javaVersionHeight);
        row += this.javaVersion.height();

        this.addDep = new SectionLayout(row, this.addDepHeight);
        row += this.addDep.height();

        this.generate = new SectionLayout(row, this.generateHeight);
        row += this.generate.height();

        this.postGen = new SectionLayout(row, this.postGenHeight);
        row += this.postGen.height();

        this.contentHeight = row;
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

        removeUnsupportedProjectTypes();
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

            Files.createDirectories(this.cacheDir);

            this.mapper.writerWithDefaultPrettyPrinter().writeValue(this.cacheFile, this.cache);

            removeUnsupportedProjectTypes();

            con.disconnect();
        } catch (MalformedURLException e) {
            IO.println("Malformed URL: " + e.getMessage());
        } catch (URISyntaxException e) {
            IO.println("UriSyntaxException: " + e.getMessage());
        } catch (IOException e) {
            IO.println("IOException: " + e.getMessage());
        }
    }

    private void removeUnsupportedProjectTypes() {
        // remove gradle-build and maven-build
        this.data.type().values().remove(2);
        this.data.type().values().remove(3);
    }

    private void loadConfig() {
        this.config = this.mapper.readValue(this.configFile, MetaDataConfig.class);

        int typeIndex = getSelectionIndex(this.data.type().values(), this.config.type());
        this.cursorX = typeIndex;
        this.currentSelection[0] = typeIndex;
        this.currentSelection[1] = getSelectionIndex(this.data.language().values(), this.config.language());
        this.currentSelection[2] = getSelectionIndex(this.data.bootVersion().values(), this.config.bootVersion());

        this.group.setText(this.config.project().group());
        this.artifact.setText(this.config.project().artifact());
        this.packageName.setText(this.config.project().packageName());

        this.currentSelection[6] = getSelectionIndex(this.data.packaging().values(), this.config.packaging());
        this.currentSelection[7] = getSelectionIndex(this.data.configurationFileFormat().values(),
                this.config.configurationFileFormat());
        this.currentSelection[8] = getSelectionIndex(this.data.javaVersion().values(), this.config.javaVersion());
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

        try {
            StringBuilder builder = new StringBuilder();

            builder.append("https://start.spring.io/starter.zip?")
                    .append("type=").append(this.data.type().values().get(this.currentSelection[0]).id())
                    .append("&language=").append(this.data.language().values().get(this.currentSelection[1]).id())
                    .append("&bootVersion=").append(this.data.bootVersion().values().get(this.currentSelection[2]).id())
                    .append("&baseDir=").append(this.artifact.getText())
                    .append("&groupId=").append(this.group.getText())
                    .append("&artifactId=").append(this.artifact.getText())
                    .append("&packageName=").append(this.packageName.getText())
                    .append("&packaging=").append(this.data.packaging().values().get(this.currentSelection[6]).id())
                    .append("&javaVersion=").append(this.data.javaVersion().values().get(this.currentSelection[8]).id())
                    .append("&configurationFileFormat=")
                    .append(this.data.configurationFileFormat().values().get(this.currentSelection[7]).id());

            URL url = URI.create(builder.toString()).toURL();

            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);

            int status = con.getResponseCode();

            if (status >= 300) {
                try (InputStream errorStream = con.getErrorStream()) {
                    StringBuilder builderError = new StringBuilder();

                    String error = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);

                    builderError.append(RED)
                            .append("Error: ")
                            .append(RESET_COLOR)
                            .append(error);

                    debug(builderError);
                    return;
                }
            }

            Path cwd = Path.of(System.getProperty("user.dir"));
            Path projectDir = cwd.resolve(this.artifact.getText());

            if (Files.exists(projectDir)) {
                StringBuilder builderError = new StringBuilder();

                String message = "Directory already exists: " + projectDir
                        + " Hint: Artifact needs to be unique in this directory.";

                builderError.append(RED)
                        .append("Error: ")
                        .append(RESET_COLOR)
                        .append(message);

                debug(builderError);
                return;
            }

            Files.createDirectories(projectDir);

            try (InputStream stream = con.getInputStream();
                    ZipInputStream zip = new ZipInputStream(stream)) {

                ZipEntry entry;

                while ((entry = zip.getNextEntry()) != null) {
                    String entryName = entry.getName();

                    if (entryName.startsWith(this.artifact.getText() + "/")) {
                        entryName = entryName.substring(this.artifact.getText().length() + 1);
                    }

                    Path output = projectDir.resolve(entryName);

                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                    } else {
                        Files.createDirectories(output.getParent());
                        Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                    }

                    zip.closeEntry();
                }
            }
        } catch (MalformedURLException e) {
            StringBuilder builderError = new StringBuilder();

            builderError.append(RED)
                    .append("Error: ")
                    .append(RESET_COLOR)
                    .append(e.getMessage());

            debug(builderError);
            return;
        } catch (IOException e) {
            StringBuilder builderError = new StringBuilder();

            builderError.append(RED)
                    .append("Error: ")
                    .append(RESET_COLOR)
                    .append(e.getMessage());

            debug(builderError);
            return;
        }

        StringBuilder builderSuccess = new StringBuilder();

        builderSuccess.append(GREEN)
                .append("Success: ")
                .append(RESET_COLOR)
                .append("Created project '")
                .append(this.artifact.getText())
                .append("'.");

        debug(builderSuccess);

        postGenerationMenu();
    }

    private void postGenerationMenu() {
        this.postGenMenuIndex = 0;
        this.isPostGenMenuRunning = true;

        StringBuilder builder = new StringBuilder();
        builder.append("\033[1A");
        renderGenerateButton(builder);
        IO.print(builder);

        renderPostGenerationOptions();

        while (isPostGenMenuRunning) {
            int key = readKey();

            switch (key) {
                case '\r', '\n' -> {
                    if (this.postGenMenuIndex == 0) {
                        this.isPostGenMenuRunning = false;
                        quit();
                    } else {
                        this.isPostGenMenuRunning = false;
                        removePostGenerationOptions();
                    }

                    return;
                }
                case 'A' -> {
                    if (this.postGenMenuIndex == 1) {
                        this.postGenMenuIndex = 0;
                    }
                }
                case 'B' -> {
                    if (this.postGenMenuIndex == 0) {
                        this.postGenMenuIndex = 1;
                    }
                }
                case 'q', -1, 3 -> {
                    this.isPostGenMenuRunning = false;
                    quit();
                }
            }
            renderPostGenerationOptions();
        }
    }

    private void renderPostGenerationOptions() {
        saveCursor();

        StringBuilder builder = new StringBuilder();

        builder.append("\r\n\r\n");

        if (this.postGenMenuIndex == 0) {
            builder.append(GREEN)
                    .append("> Exit")
                    .append(RESET_COLOR)
                    .append("\r\n")
                    .append("  Stay open");
        } else {
            builder.append("  Exit")
                    .append("\r\n")
                    .append(GREEN)
                    .append("> Stay open")
                    .append(RESET_COLOR);
        }

        IO.print(builder);

        restoreCursor();
    }

    private void removePostGenerationOptions() {
        saveCursor();

        StringBuilder builder = new StringBuilder();

        builder.append("\r\n\r\n")
                .append("\033[2K")
                .append("\r\n")
                .append("\033[2K");

        IO.print(builder);

        restoreCursor();
    }

    private void fillMenuGrid() {
        int size = 0;

        for (int i = 0; i < this.currentSelection.length; i++) {
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
                case 3, 4, 5, 9, 10 -> size = 1;
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
            case 'D', 'h', 'C', 'l', 'B', 'j', 'A', 'k' -> {
                move(key);
                return true;
            }
            case 'i', '\r', '\n' -> {
                if (this.cursorY == 9) {
                    addDependencies();
                }

                if (this.cursorY == 10) {
                    move('A');
                    generateProject();
                    return true;
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

        this.previousCursorY = this.cursorY;

        this.cursorY = newRow;
        if (newRow != previousCursorY) {
            this.cursorX = currentSelection[newRow];
        } else {
            this.cursorX = newCol;
        }

        updateScrollCursorY();
        updateSelection();
    }

    private SectionLayout getSelectedSection() {
        return this.sections[this.cursorY];
    }

    private void updateScrollCursorY() {
        this.scrollCursorY = getSelectedSection().row();
        debug(this.scrollCursorY);
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
        this.config = new MetaDataConfig(
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

        try {
            Files.createDirectories(this.configDir);

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(this.configFile.toFile(), config);
        } catch (IOException e) {
            debug(e.getMessage());
        }
    }

    private void renderLoading() {
        StringBuilder builder = new StringBuilder();
        builder.append("Loading metadata...");
        debug(builder);
    }

    private void renderLogo() {
        StringBuilder builder = new StringBuilder();

        builder.append("\033[H");

        for (String line : this.logo) {

            if (this.isDimmed) {
                builder.append(BG_DIMMED)
                        .append(DIMMED);
            }

            builder.append(GREEN)
                    .append(line, 0, 35)
                    .append(RESET_COLOR);

            if (this.isDimmed) {
                builder.append(BG_DIMMED)
                        .append(DIMMED);
                ;
            }

            builder.append(line.substring(35))
                    .append("\r\n");

        }

        builder.append("\r\n\r\n")
                .append(RESET_BUTTON_BG)
                .append(RESET_DIMMED);

        IO.print(builder);
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
        renderAddDependencies(builder);
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

        if (this.isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

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

        if (this.isDimmed) {
            builder.append(RESET_DIMMED);
        }
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
                        .append(RESET_COLOR);

                if (isDimmed) {
                    builder.append(BG_DIMMED)
                            .append(DIMMED)
                            .append("  ");
                } else {
                    builder.append("  ");
                }

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
        if (isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        builder.append("Project Metadata\r\n\r\n");

        renderTextField(builder, "Group", this.group, 3);
        renderTextField(builder, "Artifact", this.artifact, 4);
        renderTextField(builder, "Package name", formatPackageName(), 5);

        if (isDimmed) {
            builder.append(RESET_DIMMED);
        }
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

    private void renderAddDependencies(StringBuilder builder) {
        if (this.isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        builder.append("\r\n");

        if (this.cursorY == 9) {
            builder.append(BUTTON_BG_SELECTED)
                    .append(" Add dependencies ")
                    .append(RESET_COLOR);
        } else {
            builder.append(BUTTON_BG_UNSELECTED)
                    .append(" Add dependencies ");
        }

        builder.append(RESET_BUTTON_BG)
                .append("\r\n");

        if (this.isDimmed) {
            builder.append(RESET_DIMMED);
        }
    }

    private void renderGenerateButton(StringBuilder builder) {
        if (this.isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        builder.append("\r\n");

        if (this.cursorY == 10) {
            builder.append(BUTTON_BG_SELECTED)
                    .append(" Generate ")
                    .append(RESET_COLOR);
        } else {
            builder.append(BUTTON_BG_UNSELECTED)
                    .append(" Generate ");
        }

        builder.append(RESET_BUTTON_BG);

        if (this.isDimmed) {
            builder.append(RESET_DIMMED);
        }
    }

    private void addDependencies() {
        saveCursor();

        this.isAddDependencyRunning = true;
        renderDialogBackGround();
        renderDialog();

        while (isAddDependencyRunning) {
            int key = readKey();

            switch (key) {
                case 'q', '\033' -> {
                    isAddDependencyRunning = false;
                }
            }

            renderDialog();
        }

        removeDialog();
        restoreCursor();
    }

    private void renderDialogBackGround() {
        StringBuilder builderDimmed = new StringBuilder();

        builderDimmed.append("\033[H");

        // Dim entire terminal
        for (int row = 1; row <= this.rows; row++) {
            builderDimmed.append("\033[")
                    .append(row)
                    .append(";1H");

            builderDimmed.append(BG_DIMMED)
                    .append(" ".repeat(this.columns));
        }

        IO.print(builderDimmed);

        this.isDimmed = true;
        renderLogo();
        this.firstRender = true;
        renderUI();
        renderStatusBar();
        this.isDimmed = false;
    }

    private void renderDialog() {
        StringBuilder builderDialog = new StringBuilder();

        for (int row = 0; row < dependencyDialog.getHeight(); row++) {
            builderDialog.append("\033[")
                    .append(this.dependencyDialog.getY() + row)
                    .append(";")
                    .append(this.dependencyDialog.getX())
                    .append("H");

            builderDialog.append(BG)
                    .append(" ".repeat(this.dependencyDialog.getWidth()))
                    .append(RESET_BUTTON_BG);
        }

        IO.print(builderDialog);
    }

    private void removeDialog() {
        StringBuilder builder = new StringBuilder();

        builder.append("\033[H");

        // undim entire terminal
        for (int row = 1; row <= this.rows; row++) {
            builder.append("\033[")
                    .append(row)
                    .append(";1H");

            builder.append(BG)
                    .append(" ".repeat(this.columns))
                    .append(RESET_BUTTON_BG);
        }

        for (int row = 0; row < dependencyDialog.getHeight(); row++) {
            builder.append("\033[")
                    .append(this.dependencyDialog.getY() + row)
                    .append(";")
                    .append(this.dependencyDialog.getX())
                    .append("H");

            builder.append(BG)
                    .append(" ".repeat(this.dependencyDialog.getWidth()))
                    .append(RESET_BUTTON_BG);
        }

        IO.print(builder);
        this.firstRender = true;
        renderLogo();
        renderUI();
        renderStatusBar();
    }

    private void renderStatusBar() {
        saveCursor();

        StringBuilder builder = new StringBuilder();

        if (isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        String mode, hints = "↑↓ Navigate   ←→ Change   Enter Edit   Esc Back   Ctrl+C Exit",
                v = "   v" + this.version;

        if (this.isEditing) {
            mode = "INSERT";
        } else {
            mode = "NORMAL";
        }

        int spaces = this.columns - mode.length() - hints.length() - v.length();

        builder.append("\033[" + String.valueOf(this.rows - 1) + ";0H");
        builder.append(mode);

        if (spaces < 1) {
            builder.append(" ");
        } else {
            builder.append(" ".repeat(spaces));
        }

        builder.append(hints)
                .append(v);

        if (isDimmed) {
            builder.append(RESET_DIMMED);
        }

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

        StringBuilder builder = new StringBuilder();
        builder.append("\033[" + String.valueOf(this.rows) + ";0H")
                .append("\033[2K");

        if (this.isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        builder.append(" ".repeat(this.columns));

        builder.append("\033[" + String.valueOf(this.rows) + ";0H");

        builder.append(value);

        builder.append(RESET_DIMMED);

        IO.print(builder);
        restoreCursor();
    }

    // private void printMetaData(ObjectMapper mapper) {
    // IO.println(
    // mapper.writerWithDefaultPrettyPrinter()
    // .writeValueAsString(this.data));
    // }
}
