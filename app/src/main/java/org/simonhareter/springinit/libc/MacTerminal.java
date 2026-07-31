package org.simonhareter.springinit.libc;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Structure;
import com.sun.jna.Memory;

public class MacTerminal implements Terminal {

  private int ttyFd = -1;
  private LibC.Termios originalAttributes;

  @Override
  public void enableRawMode() {

    ttyFd = LibC.INSTANCE.open("/dev/tty", LibC.O_RDWR);

    if (ttyFd == -1) {
      throw new RuntimeException("Unable to open /dev/tty");
    }

    LibC.Termios termios = new LibC.Termios();

    if (LibC.INSTANCE.tcgetattr(ttyFd, termios) != 0) {
      throw new RuntimeException("tcgetattr failed");
    }

    originalAttributes = LibC.Termios.copyOf(termios);

    termios.c_lflag.setValue(
        termios.c_lflag.longValue()
            & ~(LibC.ECHO | LibC.ICANON | LibC.IEXTEN | LibC.ISIG));

    termios.c_iflag.setValue(
        termios.c_iflag.longValue()
            & ~(LibC.IXON | LibC.ICRNL));

    termios.c_oflag.setValue(
        termios.c_oflag.longValue()
            & ~(LibC.OPOST));

    termios.c_cc[LibC.VMIN] = 1;
    termios.c_cc[LibC.VTIME] = 0;

    if (LibC.INSTANCE.tcsetattr(ttyFd, LibC.TCSAFLUSH, termios) != 0) {
      throw new RuntimeException("tcsetattr failed");
    }
  }

  @Override
  public void disableRawMode() {

    if (ttyFd == -1)
      return;

    LibC.INSTANCE.tcsetattr(ttyFd, LibC.TCSAFLUSH, originalAttributes);
    LibC.INSTANCE.close(ttyFd);
    ttyFd = -1;
  }

  @Override
  public WindowSize getWindowSize() {
    Memory mem = new Memory(8);

    int ioctlRc = LibC.INSTANCE.ioctl(
        ttyFd,
        LibC.TIOCGWINSZ,
        mem);

    if (ioctlRc != 0) {
      throw new RuntimeException(
          "ioctl(TIOCGWINSZ) failed errno=" + Native.getLastError());
    }

    short rows = mem.getShort(0);
    short cols = mem.getShort(2);

    return new WindowSize(rows, cols);
  }

  interface LibC extends Library {

    LibC INSTANCE = Native.load("c", LibC.class);

    long ISIG = 0x00000080L;
    long ICANON = 0x00000100L;
    long ECHO = 0x00000008L;
    long IEXTEN = 0x00000400L;
    long IXON = 0x00000200L;
    long ICRNL = 0x00000100L;
    long OPOST = 0x00000001L;

    int VMIN = 16;
    int VTIME = 17;

    int TCSAFLUSH = 2;
    long TIOCGWINSZ = 0x40087468L;
    int O_RDWR = 0x0002;

    @Structure.FieldOrder({
        "c_iflag",
        "c_oflag",
        "c_cflag",
        "c_lflag",
        "c_cc",
        "c_ispeed",
        "c_ospeed"
    })
    class Termios extends Structure {

      public NativeLong c_iflag = new NativeLong();
      public NativeLong c_oflag = new NativeLong();
      public NativeLong c_cflag = new NativeLong();
      public NativeLong c_lflag = new NativeLong();

      public byte[] c_cc = new byte[20];

      public NativeLong c_ispeed = new NativeLong();
      public NativeLong c_ospeed = new NativeLong();

      static Termios copyOf(Termios t) {

        Termios c = new Termios();

        c.c_iflag.setValue(t.c_iflag.longValue());
        c.c_oflag.setValue(t.c_oflag.longValue());
        c.c_cflag.setValue(t.c_cflag.longValue());
        c.c_lflag.setValue(t.c_lflag.longValue());

        c.c_ispeed.setValue(t.c_ispeed.longValue());
        c.c_ospeed.setValue(t.c_ospeed.longValue());

        c.c_cc = t.c_cc.clone();

        return c;
      }
    }

    @Structure.FieldOrder({
        "ws_row",
        "ws_col",
        "ws_xpixel",
        "ws_ypixel"
    })
    public static class Winsize extends Structure {
      public short ws_row;
      public short ws_col;
      public short ws_xpixel;
      public short ws_ypixel;
    }

    int open(String path, int flags);

    int close(int fd);

    int tcgetattr(int fd, Termios termios);

    int tcsetattr(int fd, int action, Termios termios);

    int ioctl(int fd, long request, Object... args);

    int isatty(int fd);
  }
}
