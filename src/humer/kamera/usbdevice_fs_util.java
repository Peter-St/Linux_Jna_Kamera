
package humer.kamera;

import static humer.kamera.usbdevice_fs.USBDEVFS_SETINTERFACE;
import java.io.IOException;

public class usbdevice_fs_util {
    /**
     * Sends a SET_INTERFACE command to the USB device.
     * <p>
     * Starting with Android 5.0, UsbDeviceConnection.setInterface() could be
     * used instead.
     *
     * @param interfaceId The interface ID. For Android, this is the value
     *                    returned by <code>UsbInterface.getId()</code>.
     * @param altSetting  The alternate setting number. The value 0 is used to
     *                    stop streaming. For Android, this is the value
     *                    returned by
     *                    <code>UsbInterface.getAlternateSetting()</code> (only
     *                    available since Android 5.0). You may use
     *                    <code>lsusb -v -d xxxx:xxxx</code> to find the
     *                    alternate settings available for your USB device.
     */
    public static void setInterface(int fileDescriptor, int interfaceId, int altSetting) throws IOException {
        usbdevice_fs.usbdevfs_setinterface p = new usbdevice_fs.usbdevfs_setinterface();
        p.interfaceId = interfaceId;
        p.altsetting = altSetting;
        Libc.INSTANCE.ioctl(fileDescriptor, USBDEVFS_SETINTERFACE, p);
    }
}
