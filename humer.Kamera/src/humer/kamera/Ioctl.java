package humer.kamera;

public class Ioctl {

    public static final int _IOC_NRBITS = 8;
    public static final int _IOC_TYPEBITS = 8;

    public static final int _IOC_SIZEBITS = get_IOC_SIZEBITS();
    public static final int _IOC_DIRBITS = get_IOC_DIRBITS();

    public static final int _IOC_NRMASK = ((1 << _IOC_NRBITS) - 1);
    public static final int _IOC_TYPEMASK = ((1 << _IOC_TYPEBITS) - 1);
    public static final int _IOC_SIZEMASK = ((1 << _IOC_SIZEBITS) - 1);
    public static final int _IOC_DIRMASK = ((1 << _IOC_DIRBITS) - 1);

    public static final int _IOC_NRSHIFT = 0;
    public static final int _IOC_TYPESHIFT = _IOC_NRSHIFT + _IOC_NRBITS;
    public static final int _IOC_SIZESHIFT = _IOC_TYPESHIFT + _IOC_TYPEBITS;
    public static final int _IOC_DIRSHIFT = _IOC_SIZESHIFT + _IOC_SIZEBITS;

    public static final int _IOC_NONE = get_IOC_NONE();
    public static final int _IOC_WRITE = get_IOC_WRITE();
    public static final int _IOC_READ = get_IOC_READ();

    private static int get_IOC_SIZEBITS() {
        return 14; // Default, depends on platform
    }

    private static int get_IOC_DIRBITS() {
        return 2;// Default, depends on platform
    }

    private static int get_IOC_NONE() {
        return 0;
    }

    private static int get_IOC_WRITE() {
        return 1;
    }

    private static int get_IOC_READ() {
        return 2;
    }

    public static final int _IOC(int dir, int type, int nr, int size) {
        return (dir << _IOC_DIRSHIFT)
                | (type << _IOC_TYPESHIFT)
                | (nr << _IOC_NRSHIFT)
                | (size << _IOC_SIZESHIFT);
    }

    public static final int _IO(int type, int nr) {
        return _IOC(_IOC_NONE, type, nr, 0);
    }

    public static final int _IOR(int type, int nr, int size) {
        return _IOC(_IOC_READ, type, nr, size);
    }

    public static final int _IOW(int type, int nr, int size) {
        return _IOC(_IOC_WRITE, type, nr, size);
    }

    public static final int _IOWR(int type, int nr, int size) {
        return _IOC(_IOC_READ | _IOC_WRITE, type, nr, size);
    }
}
