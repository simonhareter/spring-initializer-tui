package org.simonhareter.springinit.libc;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

public class WindowsTerminal implements Terminal {
    private IntByReference inMode;
    private IntByReference outMode;

    @Override
    public void enableRawMode() {
        Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);

        inMode = new IntByReference();
        Kernel32.INSTANCE.GetConsoleMode(inHandle, inMode);

        int inMode;
        inMode = this.inMode.getValue() & ~(Kernel32.ENABLE_ECHO_INPUT
                | Kernel32.ENABLE_LINE_INPUT
                | Kernel32.ENABLE_MOUSE_INPUT
                | Kernel32.ENABLE_WINDOW_INPUT
                | Kernel32.ENABLE_PROCESSED_INPUT);

        inMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_INPUT;

        Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode);

        Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        outMode = new IntByReference();
        Kernel32.INSTANCE.GetConsoleMode(outHandle, outMode);

        int outMode = this.outMode.getValue();
        outMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_PROCESSING;
        outMode |= Kernel32.ENABLE_PROCESSED_OUTPUT;
        Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode);

    }

    @Override
    public void disableRawMode() {
        Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
        Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode.getValue());

        Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode.getValue());
    }

    @Override
    public WindowSize getWindowSize() {
        final Kernel32.CONSOLE_SCREEN_BUFFER_INFO info = new Kernel32.CONSOLE_SCREEN_BUFFER_INFO();
        final Kernel32 instance = Kernel32.INSTANCE;
        final Pointer handle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        instance.GetConsoleScreenBufferInfo(handle, info);
        return new WindowSize(info.windowHeight(), info.windowWidth());
    }

    interface Kernel32 extends StdCallLibrary {

        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        /**
         * The CryptUIDlgSelectCertificateFromStore function displays a dialog box
         * that allows the selection of a certificate from a specified store.
         *
         * @param hCertStore        Handle of the certificate store to be searched.
         * @param hwnd              Handle of the window for the display. If NULL,
         *                          defaults to the desktop window.
         * @param pwszTitle         String used as the title of the dialog box. If
         *                          NULL, the default title, "Select Certificate,"
         *                          is used.
         * @param pwszDisplayString Text statement in the selection dialog box. If
         *                          NULL, the default phrase, "Select a certificate
         *                          you want to use," is used.
         * @param dwDontUseColumn   Flags that can be combined to exclude columns of
         *                          the display.
         * @param dwFlags           Currently not used and should be set to 0.
         * @param pvReserved        Reserved for future use.
         * @return Returns a pointer to the selected certificate context. If no
         *         certificate was selected, NULL is returned. When you have
         *         finished using the certificate, free the certificate context by
         *         calling the CertFreeCertificateContext function.
         */
        public static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004, ENABLE_PROCESSED_OUTPUT = 0x0001;

        int ENABLE_LINE_INPUT = 0x0002;
        int ENABLE_PROCESSED_INPUT = 0x0001;
        int ENABLE_ECHO_INPUT = 0x0004;
        int ENABLE_MOUSE_INPUT = 0x0010;
        int ENABLE_WINDOW_INPUT = 0x0008;
        int ENABLE_QUICK_EDIT_MODE = 0x0040;
        int ENABLE_INSERT_MODE = 0x0020;

        int ENABLE_EXTENDED_FLAGS = 0x0080;

        int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

        int STD_OUTPUT_HANDLE = -11;
        int STD_INPUT_HANDLE = -10;
        int DISABLE_NEWLINE_AUTO_RETURN = 0x0008;

        // BOOL WINAPI GetConsoleScreenBufferInfo(
        // _In_ HANDLE hConsoleOutput,
        // _Out_ PCONSOLE_SCREEN_BUFFER_INFO lpConsoleScreenBufferInfo);
        void GetConsoleScreenBufferInfo(
                Pointer in_hConsoleOutput,
                CONSOLE_SCREEN_BUFFER_INFO out_lpConsoleScreenBufferInfo)
                throws LastErrorException;

        void GetConsoleMode(
                Pointer in_hConsoleOutput,
                IntByReference out_lpMode)
                throws LastErrorException;

        void SetConsoleMode(
                Pointer in_hConsoleOutput,
                int in_dwMode) throws LastErrorException;

        Pointer GetStdHandle(int nStdHandle);

        // typedef struct _CONSOLE_SCREEN_BUFFER_INFO {
        // COORD dwSize;
        // COORD dwCursorPosition;
        // WORD wAttributes;
        // SMALL_RECT srWindow;
        // COORD dwMaximumWindowSize;
        // } CONSOLE_SCREEN_BUFFER_INFO;
        class CONSOLE_SCREEN_BUFFER_INFO extends Structure {

            public COORD dwSize;
            public COORD dwCursorPosition;
            public short wAttributes;
            public SMALL_RECT srWindow;
            public COORD dwMaximumWindowSize;

            private static String[] fieldOrder = { "dwSize", "dwCursorPosition", "wAttributes", "srWindow",
                    "dwMaximumWindowSize" };

            @Override
            protected java.util.List<String> getFieldOrder() {
                return java.util.Arrays.asList(fieldOrder);
            }

            public int windowWidth() {
                return this.srWindow.width() + 1;
            }

            public int windowHeight() {
                return this.srWindow.height() + 1;
            }
        }

        // typedef struct _COORD {
        // SHORT X;
        // SHORT Y;
        // } COORD, *PCOORD;
        class COORD extends Structure implements Structure.ByValue {
            public COORD() {
            }

            public COORD(short X, short Y) {
                this.X = X;
                this.Y = Y;
            }

            public short X;
            public short Y;

            private static String[] fieldOrder = { "X", "Y" };

            @Override
            protected java.util.List<String> getFieldOrder() {
                return java.util.Arrays.asList(fieldOrder);
            }
        }

        // typedef struct _SMALL_RECT {
        // SHORT Left;
        // SHORT Top;
        // SHORT Right;
        // SHORT Bottom;
        // } SMALL_RECT;
        class SMALL_RECT extends Structure {
            public SMALL_RECT() {
            }

            public SMALL_RECT(SMALL_RECT org) {
                this(org.Top, org.Left, org.Bottom, org.Right);
            }

            public SMALL_RECT(short Top, short Left, short Bottom, short Right) {
                this.Top = Top;
                this.Left = Left;
                this.Bottom = Bottom;
                this.Right = Right;
            }

            public short Left;
            public short Top;
            public short Right;
            public short Bottom;

            private static String[] fieldOrder = { "Left", "Top", "Right", "Bottom" };

            @Override
            protected java.util.List<String> getFieldOrder() {
                return java.util.Arrays.asList(fieldOrder);
            }

            public short width() {
                return (short) (this.Right - this.Left);
            }

            public short height() {
                return (short) (this.Bottom - this.Top);
            }
        }
    }
}
