
package humer.kamera;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import static humer.kamera.usbdevice_fs.USBDEVFS_CLAIMINTERFACE;
import static humer.kamera.usbdevice_fs.USBDEVFS_CONTROL;
import static humer.kamera.usbdevice_fs.USBDEVFS_DISCONNECT;
import static humer.kamera.usbdevice_fs.USBDEVFS_GETDRIVER;
import static humer.kamera.usbdevice_fs.USBDEVFS_IOCTL;
import static humer.kamera.usbdevice_fs.USBDEVFS_RELEASEINTERFACE;
import humer.kamera.usbdevice_fs.usbdevfs_ctrltransfer;
import humer.kamera.usbdevice_fs.usbdevfs_getdriver;
import humer.kamera.usbdevice_fs.usbdevfs_ioctl;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class Kam extends javax.swing.JFrame {
    // REQUIRED CONFIGURATION
    private static final int BUS = 1;
    private static final int DEVICE = 5;
    private static final int ALT_SETTING = 6; // 7 = 3*1024 bytes packet size // 6 = 3*896 // 5 = 2*1024 // 4 = 2*768 // 3 = 1x 1024 // 2 = 1x 512 // 1 = 128 //
    // ADDITIONAL CONFIGURATION
    private static final String DUMP_FILE = "target/test.dump";
    private static final int CAM_STREAMING_INTERFACE_NUM = 1;
    private static final int CAM_CONTROL_INTERFACE_NUM = 0;
    private static final byte ENDPOINT_ADDRESS = (byte) 0x81;
    private static final int CAM_FORMAT_INDEX = 1;   // MJPEG // YUV // bFormatIndex: 1 = uncompressed
    private static final int CAM_FRAME_INDEX = 1; // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
    private static final int CAM_FRAME_INTERVAL = 333333; // 333333 YUV = 30 fps // 666666 YUV = 15 fps
    private static final int PACKETS_PER_REQUEST = 8;
    private static final int MAX_PACKET_SIZE = 3072;
    private static final int ACTIVE_URBS = 16;

    private static final String DEVICE_PATH = String.format("/dev/bus/usb/%03d/%03d", BUS, DEVICE);

    private volatile IsochronousRead1 runningTransfer;

    private int fd;

    public void usbIsoLinux() throws IOException {
        fd = Libc.INSTANCE.open(DEVICE_PATH, Libc.O_RDWR);
        usbdevfs_getdriver getdrv = new usbdevfs_getdriver();
        usbdevfs_ioctl command = new usbdevfs_ioctl();
        for(int i= 0; i < 2; i++) {
            getdrv.ifno = i;
            try {
                Libc.INSTANCE.ioctl(fd, USBDEVFS_GETDRIVER, getdrv);
                System.out.printf("Interface %d of %s was connected to: %s%n", i, DEVICE_PATH, Native.toString(getdrv.driver));
                command.ifno = i;
                command.ioctl_code = USBDEVFS_DISCONNECT;
                command.data = null;
                try {
                    Libc.INSTANCE.ioctl(fd, USBDEVFS_IOCTL, command);
                    System.out.printf("Successfully unlinked kerndriver from Interface %d of %s%n", i, DEVICE_PATH);
                } catch (LastErrorException ex) {
                    System.out.printf("Failed to unlink kernel driver from Interface %d of %s: %s%n", i, DEVICE_PATH, ex.getMessage());
                }
            } catch (LastErrorException ex) {
                System.out.printf("Failed to retrieve driver for Interface %d of %s: %s%n", i, DEVICE_PATH, ex.getMessage());
            }
            try {
                Libc.INSTANCE.ioctl(fd, USBDEVFS_CLAIMINTERFACE, new IntByReference(i));
                System.out.printf("Successfully claimed Interface %d of %s%n", i, DEVICE_PATH);
            } catch (LastErrorException ex) {
                System.out.printf("Failed to claim Interface %d of %s: %s%n", i, DEVICE_PATH, ex.getMessage());
            }
            usbdevice_fs_util.setInterface(fd, CAM_STREAMING_INTERFACE_NUM, 0);
            ioctlControltransfer();
            usbdevice_fs_util.setInterface(fd, CAM_STREAMING_INTERFACE_NUM, ALT_SETTING);
        }
        usbIso.setInterface(camStreamingInterfaceNum, 0);
        ioctlControltransfer();
        usbIso.setInterface(camStreamingInterfaceNum, ALT_SETTING);
    }
    
    

    public void kameraSchliessen() throws IOException {
        usbdevice_fs_util.setInterface(fd, CAM_STREAMING_INTERFACE_NUM, 0);
        for(int if_num = 0; if_num <= CAM_STREAMING_INTERFACE_NUM; if_num++) {
            Libc.INSTANCE.ioctl(fd, USBDEVFS_RELEASEINTERFACE, new IntByReference(if_num));
        }
        Libc.INSTANCE.close(fd);
    }

    public void ioctlControltransfer() {
        Memory buffer = new Memory(26);
        buffer.clear();
        buffer.setByte(0, (byte) 0x01); // what fields shall be kept fixed (0x01: dwFrameInterval)
        buffer.setByte(1, (byte) 0x00); //
        buffer.setByte(2, (byte) CAM_FORMAT_INDEX); // video format index
        buffer.setByte(3, (byte) CAM_FRAME_INDEX);  // video frame index // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
        buffer.setByte(4, (byte) (CAM_FRAME_INTERVAL & 0xFF));
        buffer.setByte(5, (byte) ((CAM_FRAME_INTERVAL >> 8) & 0xFF));
        buffer.setByte(6, (byte) ((CAM_FRAME_INTERVAL >> 16) & 0xFF));
        buffer.setByte(7, (byte) ((CAM_FRAME_INTERVAL >> 24) & 0xFF));
        usbdevfs_ctrltransfer ctrl = new usbdevfs_ctrltransfer();
        ctrl.wValue = USBIso.VS_PROBE_CONTROL << 8;
        ctrl.wIndex = CAM_STREAMING_INTERFACE_NUM;
        ctrl.wLength = (short) buffer.size();
        ctrl.timeout = 2000; // USB should t/o after 5 seconds.
        ctrl.data = buffer;
        videoParameter(buffer.getByteArray(0, 26));
        ctrl.bRequestType = USBIso.RT_CLASS_INTERFACE_SET;
        ctrl.bRequest = USBIso.SET_CUR;

        try {
            Libc.INSTANCE.ioctl(fd, USBDEVFS_CONTROL, ctrl);
            System.out.printf("Camera initialization success%n");
        } catch (LastErrorException ex) {
            System.out.printf("Camera initialization failed: %s%n", ex.getMessage());
        }

        ctrl.bRequestType = (byte) USBIso.RT_CLASS_INTERFACE_GET;
        ctrl.bRequest = (byte) USBIso.GET_CUR;

        try {
            Libc.INSTANCE.ioctl(fd, USBDEVFS_CONTROL, ctrl);
            videoParameter(buffer.getByteArray(0, 26));
        } catch (LastErrorException ex) {
            System.out.printf("Camera initialization failed. Streaming parms probe set failed: %s%n", ex.getMessage());
        }

        ctrl.bRequest = (byte) USBIso.SET_CUR;
        ctrl.bRequestType = (byte) USBIso.RT_CLASS_INTERFACE_SET;
        ctrl.wValue = USBIso.VS_COMMIT_CONTROL << 8;
        try {
            Libc.INSTANCE.ioctl(fd, USBDEVFS_CONTROL, ctrl);
            videoParameter(buffer.getByteArray(0, 26));
        } catch (LastErrorException ex) {
            System.out.printf("Camera initialization failed. Streaming parms commit set failed: %s%n", ex.getMessage());
        }

        ctrl.bRequest = (byte) USBIso.GET_CUR;
        ctrl.bRequestType = (byte) USBIso.RT_CLASS_INTERFACE_GET;
        ctrl.wValue = USBIso.VS_COMMIT_CONTROL << 8;
        try {
            Libc.INSTANCE.ioctl(fd, USBDEVFS_CONTROL,  ctrl);
            videoParameter(buffer.getByteArray(0, 26));
        } catch (LastErrorException ex) {
            System.out.printf("Camera initialization failed. Streaming parms commit get failed: %s%n.", ex.getMessage());
        }
    }

    /**
     * Creates new form Kam
     */
    public Kam() {
        initComponents();
        try {
            Path path = Paths.get("README.md");
            if (Files.exists(path)) {
                infoPanel.setText(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            } else {
                try(InputStream is = Kam.class.getResourceAsStream("/README.md")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[512 * 1024];
                    int read;
                    while((read = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, read);
                    }
                    infoPanel.setText(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        infoPanel.setCaretPosition(0);
        setSize(800, 600);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        Kamera = new javax.swing.JButton();
        infoPanelScrollPane = new javax.swing.JScrollPane();
        infoPanel = new javax.swing.JTextPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        Kamera.setText("Kamera");
        Kamera.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                KameraActionPerformed(evt);
            }
        });

        infoPanelScrollPane.setOpaque(false);

        infoPanel.setEditable(false);
        infoPanel.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        infoPanel.setOpaque(false);
        infoPanelScrollPane.setViewportView(infoPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(infoPanelScrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(Kamera, javax.swing.GroupLayout.DEFAULT_SIZE, 466, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(infoPanelScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 239, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(Kamera)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents


    private void KameraActionPerformed(java.awt.event.ActionEvent evt) {
        if(runningTransfer != null) {
            return;
        }

        try {
            // TODO add your handling code here:
            usbIsoLinux();
        } catch (IOException ex) {
            Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
        }

        runningTransfer = new IsochronousRead1();
        runningTransfer.start();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) throws IOException {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Kam.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new Kam().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton Kamera;
    private javax.swing.JTextPane infoPanel;
    private javax.swing.JScrollPane infoPanelScrollPane;
    // End of variables declaration//GEN-END:variables
   
    private static String hexDump (byte[] buf, int len) {
        StringBuilder s = new StringBuilder(len * 3);
        for (int p = 0; p < len; p++) {
            if (p > 0) {
                s.append(' '); }
            int v = buf[p] & 0xff;
            if (v < 16) {
                s.append('0'); }
            s.append(Integer.toHexString(v)); }
        return s.toString();
    }


    public void videoParameter(byte[] p) {
        String s = "hint=0x" + Integer.toHexString(unpackUsbUInt2(p, 0))
                + " format=" + (p[2] & 0xf)
                + " frame=" + (p[3] & 0xf)
                + " frameInterval=" + unpackUsbInt(p, 4)
                + " keyFrameRate=" + unpackUsbUInt2(p, 8)
                + " pFrameRate=" + unpackUsbUInt2(p, 10)
                + " compQuality=" + unpackUsbUInt2(p, 12)
                + " compWindowSize=" + unpackUsbUInt2(p, 14)
                + " delay=" + unpackUsbUInt2(p, 16)
                + " maxVideoFrameSize=" + unpackUsbInt(p, 18)
                + " maxPayloadTransferSize=" + unpackUsbInt(p, 22);
        System.out.println(s);
    }
    
    private String dumpStillImageParms(byte[] p) {
        String s = "bFormatIndex=" + (p[0] & 0xff)
                + " bFrameIndex=" + (p[1] & 0xff)
                + " bCompressionIndex=" + (p[2] & 0xff)
                + " maxVideoFrameSize=" + unpackUsbInt(p, 3)
                + " maxPayloadTransferSize=" + unpackUsbInt(p, 7);
        return s;
    }
        
    private static int unpackUsbInt (byte[] buf, int pos) {
        return unpackInt(buf, pos, false); 
    }

    private static int unpackUsbUInt2 (byte[] buf, int pos) {
        return ((buf[pos + 1] & 0xFF) << 8) | (buf[pos] & 0xFF); 
    }
    
    private static void packUsbInt (int i, byte[] buf, int pos) {
        packInt(i, buf, pos, false); 
    }
        
    private static void packInt (int i, byte[] buf, int pos, boolean bigEndian) {
        if (bigEndian) {
            buf[pos]     = (byte)((i >>> 24) & 0xFF);
            buf[pos + 1] = (byte)((i >>> 16) & 0xFF);
            buf[pos + 2] = (byte)((i >>>  8) & 0xFF);
            buf[pos + 3] = (byte)(i & 0xFF); }
        else {
            buf[pos]     = (byte)(i & 0xFF);
            buf[pos + 1] = (byte)((i >>>  8) & 0xFF);
            buf[pos + 2] = (byte)((i >>> 16) & 0xFF);
            buf[pos + 3] = (byte)((i >>> 24) & 0xFF); 
        }
    }
            
    private static int unpackInt (byte[] buf, int pos, boolean bigEndian) {
        if (bigEndian) {
            return (buf[pos] << 24) | ((buf[pos + 1] & 0xFF) << 16) | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF); }
        else {
            return (buf[pos + 3] << 24) | ((buf[pos + 2] & 0xFF) << 16) | ((buf[pos + 1] & 0xFF) << 8) | (buf[pos] & 0xFF); 
        }
    }

    class IsochronousRead1 extends Thread {

        public IsochronousRead1() {
            setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            USBIso usbIso = new USBIso(fd, PACKETS_PER_REQUEST, MAX_PACKET_SIZE, ENDPOINT_ADDRESS);
            usbIso.preallocateRequests(ACTIVE_URBS);
            File dump = new File(DUMP_FILE).getAbsoluteFile();
            dump.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(dump)) {
                //Thread.sleep(500);
                ArrayList<String> logArray = new ArrayList<>(512);
                int packetCnt = 0;
                int packet0Cnt = 0;
                int packet12Cnt = 0;
                int packetDataCnt = 0;
                int packetHdr8Ccnt = 0;
                int packetErrorCnt = 0;
                int frameCnt = 0;
                long time0 = System.currentTimeMillis();
                int frameLen = 0;
                int requestCnt = 0;
                byte[] data = new byte[MAX_PACKET_SIZE];
                try {
                    //enableStreaming(true);
                    usbIso.submitUrbs();
                } catch (IOException ex) {
                    Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
                }
                while (System.currentTimeMillis() - time0 < 3000) {
                    boolean stopReq = false;
                    USBIso.Request req = usbIso.reapRequest(true);
                    for (int packetNo = 0; packetNo < req.getNumberOfPackets(); packetNo++) {
                        packetCnt++;
                        int packetLen = req.getPacketActualLength(packetNo);
                        if (packetLen == 0) {
                            packet0Cnt++;
                        }
                        if (packetLen == 12) {
                            packet12Cnt++;
                        }
                        if (packetLen == 0) {
                            continue;
                        }
                        StringBuilder logEntry = new StringBuilder(requestCnt + "/" + packetNo + " len=" + packetLen);
                        int packetStatus = req.getPacketStatus(packetNo);
                        if (packetStatus != 0) {
                            System.out.println("Packet status=" + packetStatus);
                            stopReq = true;
                            break;
                        }
                        if (packetLen > 0) {
                            if (packetLen > MAX_PACKET_SIZE) {
                                //throw new Exception("packetLen > maxPacketSize");
                            }
                            req.getPacketData(packetNo, data, packetLen);
                            logEntry.append(" data=" + hexDump(data, Math.min(32, packetLen)));
                            int headerLen = data[0] & 0xff;

                            try {
                                if (headerLen < 2 || headerLen > packetLen) {
                                    //    skipFrames = 1;
                                }
                            } catch (Exception e) {
                                System.out.println("Invalid payload header length.");
                            }
                            int headerFlags = data[1] & 0xff;
                            if (headerFlags == 0x8c) {
                                packetHdr8Ccnt++;
                            }
                            // logEntry.append(" hdrLen=" + headerLen + " hdr[1]=0x" + Integer.toHexString(headerFlags));
                            int dataLen = packetLen - headerLen;
                            if (dataLen > 0) {
                                packetDataCnt++;
                            }
                            fos.write(data, headerLen, dataLen);
                            frameLen += dataLen;
                            if ((headerFlags & 0x40) != 0) {
                                logEntry.append(" *** Error ***");
                                packetErrorCnt++;
                            }
                            if ((headerFlags & 2) != 0) {
                                logEntry.append(" EOF frameLen=" + frameLen);
                                frameCnt++;
                                frameLen = 0;
                            }
                        }
                        logArray.add(logEntry.toString());
                    }
                    if (stopReq) {
                        break;
                    }
                    requestCnt++;
                    req.initialize();
                    req.submit();

                }
                System.out.println("requests=" + requestCnt + " packetCnt=" + packetCnt + " packetErrorCnt=" + packetErrorCnt + " packet0Cnt=" + packet0Cnt + ", packet12Cnt=" + packet12Cnt + ", packetDataCnt=" + packetDataCnt + " packetHdr8cCnt=" + packetHdr8Ccnt + " frameCnt=" + frameCnt);
                for (String s : logArray) {
                    System.out.println(s);
                }

                kameraSchliessen();

                runningTransfer = null;
            } catch (IOException ex) {
                Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}
