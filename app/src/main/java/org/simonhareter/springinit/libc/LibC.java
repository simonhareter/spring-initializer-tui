package org.simonhareter.springinit;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import java.util.Arrays;

public interface LibC extends Library {
    int SYSTEM_OUT_FD = 0;
    int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2,
            IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6, VTIME = 5, TIOCGWINSZ = 0x5413;

    // we're loading the C standard library for POSIX systems
    LibC INSTANCE = Native.load("c", LibC.class);

    @Structure.FieldOrder(value = { "ws_row", "ws_col", "ws_xpixel", "ws_ypixel" })
    class Winsize extends Structure {
        public short ws_row, ws_col, ws_xpixel, ws_ypixel;
    }

    @Structure.FieldOrder(value = { "c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc" })
    class Termios extends Structure {
        public int c_iflag, c_oflag, c_cflag, c_lflag;

        public byte[] c_cc = new byte[19];

        public Termios() {
        }

        public static Termios of(Termios t) {
            Termios copy = new Termios();
            copy.c_iflag = t.c_iflag;
            copy.c_oflag = t.c_oflag;
            copy.c_cflag = t.c_cflag;
            copy.c_lflag = t.c_lflag;
            copy.c_cc = t.c_cc.clone();
            return copy;
        }

        @Override
        public String toString() {
            return "Termios{" +
                    "c_iflag=" + c_iflag +
                    ", c_oflag=" + c_oflag +
                    ", c_cflag=" + c_cflag +
                    ", c_lflag=" + c_lflag +
                    ", c_cc=" + Arrays.toString(c_cc) +
                    '}';
        }
    }

    int tcgetattr(int fd, Termios termios);

    int tcsetattr(int fd, int optional_actions,
            Termios termios);

    int ioctl(int fd, int opt, Winsize winsize);
}
