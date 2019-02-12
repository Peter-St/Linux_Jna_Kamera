
package humer.kamera;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import humer.kamera.USBIso.usbdevfs_ctrltransfer;
import humer.kamera.USBIso.usbdevfs_getdriver;
import humer.kamera.USBIso.usbdevfs_ioctl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Kam extends javax.swing.JFrame {
    private static final int BUS = 1;
    private static final int DEVICE = 5;
    private static final int ALT_SETTING = 6; // 7 = 3*1024 bytes packet size // 6 = 3*896 // 5 = 2*1024 // 4 = 2*768 // 3 = 1x 1024 // 2 = 1x 512 // 1 = 128 //
    private static final int camStreamingInterfaceNum = 1;
    private static final int camControlInterfaceNum = 0;
    private static final int endpunktadresse = 0x81;
    private static final String devicePath = String.format("/dev/bus/usb/%03d/%03d", BUS, DEVICE);
    private boolean               backgroundJobActive;
    private int					  camStreamingAltSetting;
                    
    private boolean               bulkMode;
    private int                   camFormatIndex = 1;   // MJPEG // YUV // bFormatIndex: 1 = uncompressed
    private int                   camFrameIndex = 5; // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
    private int                   camFrameInterval = 333333; // 333333 YUV = 30 fps // 666666 YUV = 15 fps
    private USBIso                usbIso;
    private int                   packetsPerRequest = 8;
    private int                     bConfigurationValue;
    public int                   maxPacketSize = 3072;
    private int                   imageWidth = 640;
    private int                   imageHeight = 480;
    private int                   activeUrbs = 16;
    private boolean               camIsOpen;
    public int fd;
    int maxVideoFrameGroesse;
    
    public void usbIsoLinux() throws IOException {
        fd = Libc.INSTANCE.open(devicePath, Libc.O_RDWR);
        usbIso = new USBIso(fd, packetsPerRequest, maxPacketSize);
        usbdevfs_getdriver getdrv = new usbdevfs_getdriver();
        usbdevfs_ioctl command = new usbdevfs_ioctl();
        for(int i= 0; i < 2; i++) {
            getdrv.ifno = i;
            try {
                Libc.INSTANCE.ioctl(fd, USBIso.USBDEVFS_GETDRIVER, getdrv);
                System.out.printf("Interface %d of %s was connected to: %s%n", i, devicePath, Native.toString(getdrv.driver));
                command.ifno = i;
                command.ioctl_code = USBIso.USBDEVFS_DISCONNECT;
                command.data = null;
                try {
                    Libc.INSTANCE.ioctl(fd, USBIso.USBDEVFS_IOCTL, command);
                    System.out.printf("Successfully unlinked kerndriver from Interface %d of %s%n", i, devicePath);
                } catch (LastErrorException ex) {
                    System.out.printf("Failed to unlink kernel driver from Interface %d of %s: %s%n", i, devicePath, ex.getMessage());
                }
            } catch (LastErrorException ex) {
                System.out.printf("Failed to retrieve driver for Interface %d of %s: %s%n", i, devicePath, ex.getMessage());
            }
            try {
                Libc.INSTANCE.ioctl(fd, USBIso.USBDEVFS_CLAIMINTERFACE, new IntByReference(i));
                System.out.printf("Successfully claimed Interface %d of %s%n", i, devicePath);
            } catch (LastErrorException ex) {
                System.out.printf("Failed to claim Interface %d of %s: %s%n", i, devicePath, ex.getMessage());
            }
            usbIso.setInterface(camStreamingInterfaceNum, 0);
            ioctlControltransfer();
            usbIso.setInterface(camStreamingInterfaceNum, ALT_SETTING);
        }
    }

    public void kameraSchliessen() throws IOException {
        usbIso.setInterface(camStreamingInterfaceNum, 0);
        for(int if_num = 0; if_num <= camStreamingInterfaceNum; if_num++) {
            int ret = Libc.INSTANCE.ioctl(fd, USBIso.USBDEVFS_RELEASEINTERFACE, new IntByReference(if_num));
        }
        Libc.INSTANCE.close(fd);
    }

    public void ioctlControltransfer() {
        Memory buffer = new Memory(26);
        buffer.clear();
        buffer.setByte(0, (byte) 0x01); // what fields shall be kept fixed (0x01: dwFrameInterval)
        buffer.setByte(1, (byte) 0x00); //
        buffer.setByte(2, (byte) camFormatIndex); // video format index
        buffer.setByte(3, (byte) camFrameIndex);  // video frame index // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
        buffer.setByte(4, (byte) (camFrameInterval & 0xFF));
        buffer.setByte(5, (byte) ((camFrameInterval >> 8) & 0xFF));
        buffer.setByte(6, (byte) ((camFrameInterval >> 16) & 0xFF));
        buffer.setByte(7, (byte) ((camFrameInterval >> 24) & 0xFF));
        usbdevfs_ctrltransfer ctrl = new usbdevfs_ctrltransfer();
        ctrl.wValue = USBIso.VS_PROBE_CONTROL << 8;
        ctrl.wIndex = camStreamingInterfaceNum;
        ctrl.wLength = (short) buffer.size();
        ctrl.timeout = 2000; // USB should t/o after 5 seconds.
        ctrl.data = buffer;
        videoParameter(buffer.getByteArray(0, 26));
        ctrl.bRequestType = USBIso.RT_CLASS_INTERFACE_SET;
        ctrl.bRequest = USBIso.SET_CUR;

        try {
            Libc.INSTANCE.ioctl(fd, USBIso.USBDEVFS_CONTROL, ctrl);
            System.out.printf("Camera initialization success%n");
        } catch (LastErrorException ex) {
            System.out.printf("Camera initialization failed: %s%n", ex.getMessage());
        }

        ctrl.bRequestType = (byte) USBIso.RT_CLASS_INTERFACE_GET;
        ctrl.bRequest = (byte) USBIso.GET_CUR;

        try {
            Libc.INSTANCE.ioctl(fd, USBIso.USBDEVFS_CONTROL, ctrl);
            videoParameter(buffer.getByteArray(0, 26));
        } catch (LastErrorException ex) {
            System.out.printf("Camera initialization failed. Streaming parms probe set failed: %s%n", ex.getMessage());
        }

        ctrl.bRequest = (byte) USBIso.SET_CUR;
        ctrl.bRequestType = (byte) USBIso.RT_CLASS_INTERFACE_SET;
        ctrl.wValue = USBIso.VS_COMMIT_CONTROL << 8;
        try {
            Libc.INSTANCE.ioctl(fd, USBIso.USBDEVFS_CONTROL, ctrl);
            videoParameter(buffer.getByteArray(0, 26));
        } catch (LastErrorException ex) {
            System.out.printf("Camera initialization failed. Streaming parms commit set failed: %s%n", ex.getMessage());
        }

        ctrl.bRequest = (byte) USBIso.GET_CUR;
        ctrl.bRequestType = (byte) USBIso.RT_CLASS_INTERFACE_GET;
        ctrl.wValue = USBIso.VS_COMMIT_CONTROL << 8;
        try {
            Libc.INSTANCE.ioctl(fd, USBIso.USBDEVFS_CONTROL,  ctrl);
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
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents() {

        Kamera = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        Kamera.setText("Kamera");
        Kamera.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                KameraActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(68, 68, 68)
                .addComponent(Kamera)
                .addContainerGap(274, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(36, 36, 36)
                .addComponent(Kamera)
                .addContainerGap(233, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>                        

    private void KameraActionPerformed(java.awt.event.ActionEvent evt) {                                       
        try {
            // TODO add your handling code here:
            usbIsoLinux();
        } catch (IOException ex) {
            Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        usbIso.preallocateRequests(activeUrbs);
        
        
        try {
            startBackgroundJob(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    testIsochronousRead1();
                    return null;
                }
            });
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
        System.out.println("OK");
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
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(Kam.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(Kam.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(Kam.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Kam.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Kam().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify                     
    private javax.swing.JButton Kamera;
    // End of variables declaration                   


private void startBackgroundJob (final Callable callable) throws Exception {
        if (backgroundJobActive) {
            throw new Exception("Background job is already active."); }
        backgroundJobActive = true;
        Thread thread = new Thread() {
            @Override public void run() {
                try {
                    callable.call(); }
                catch (Throwable e) {
                	e.printStackTrace();}
                finally {
                    backgroundJobActive = false; }}};
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start(); 
    }

   
    private static String hexDump (byte[] buf, int len) {
        StringBuilder s = new StringBuilder(len * 3);
        for (int p = 0; p < len; p++) {
            if (p > 0) {
                s.append(' '); }
            int v = buf[p] & 0xff;
            if (v < 16) {
                s.append('0'); }
            s.append(Integer.toHexString(v)); }
        return s.toString(); }
    
      
   private void submitActiveUrbs() throws IOException {
      // /*
        long time0 = System.currentTimeMillis();
        for (int i = 0; i < activeUrbs; i++) {
            USBIso.Request req = usbIso.getRequest();
            //EndpunktAdresse = (int) 129;
            //System.out.println("Endpunktadresse vor = " +String.format("0x%02X", (int)EndpunktAdresse));
            //System.out.println("Endpunktadresse in int = " + EndpunktAdresse2);
            req.initialize((byte) 0x81);
            req.submit();
            
            //System.out.println("Endpunktadresse nach submit urb = " +String.format("0x%02X", (int)EndpunktAdresse));
        }
        System.out.println("Verschrittene Zeit bei der Urbübermittlung: " + (System.currentTimeMillis() - time0) + " ms.");
    // */
    //usbIso.submitUsbdevfs_urb(8,1);
   }

    public void videoParameter (byte[] p) {
        StringBuilder s = new StringBuilder(128);
        s.append("hint=0x" + Integer.toHexString(unpackUsbUInt2(p, 0)));
        s.append(" format=" + (p[2] & 0xf));
        s.append(" frame=" + (p[3] & 0xf));
        s.append(" frameInterval=" + unpackUsbInt(p, 4));
        s.append(" keyFrameRate=" + unpackUsbUInt2(p, 8));
        s.append(" pFrameRate=" + unpackUsbUInt2(p, 10));
        s.append(" compQuality=" + unpackUsbUInt2(p, 12));
        s.append(" compWindowSize=" + unpackUsbUInt2(p, 14));
        s.append(" delay=" + unpackUsbUInt2(p, 16));
        s.append(" maxVideoFrameSize=" + unpackUsbInt(p, 18));
        maxVideoFrameGroesse = unpackUsbInt(p, 18);
        s.append(" maxPayloadTransferSize=" + unpackUsbInt(p, 22));
        System.out.println( s.toString());
    }   
    
        private String dumpStillImageParms (byte[] p) {
        StringBuilder s = new StringBuilder(128);
        s.append("bFormatIndex=" + (p[0] & 0xff));
        s.append(" bFrameIndex=" + (p[1] & 0xff));
        s.append(" bCompressionIndex=" + (p[2] & 0xff));
        s.append(" maxVideoFrameSize=" + unpackUsbInt(p, 3));
        s.append(" maxPayloadTransferSize=" + unpackUsbInt(p, 7));
        return s.toString(); 
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

private void testIsochronousRead1() throws IOException {
        //Thread.sleep(500);
        ArrayList<String> logArray = new ArrayList<String>(512);
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
        byte[] data = new byte[maxPacketSize];
                try {
                    //enableStreaming(true);
                    submitActiveUrbs();
                } catch (IOException ex) {
                    Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
                }
        while (System.currentTimeMillis() - time0 < 3000) {
                boolean stopReq = false;
                USBIso.Request req = usbIso.reapRequest(true);
                for (int packetNo = 0; packetNo < req.getPacketCount(); packetNo++) {
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
                        if (packetLen > maxPacketSize) {
                            //throw new Exception("packetLen > maxPacketSize");
                        }
                        req.getPacketData(packetNo, data, packetLen);
                        logEntry.append(" data=" + hexDump(data, Math.min(32, packetLen)));
                        int headerLen = data[0] & 0xff;
                        
                        try { if (headerLen < 2 || headerLen > packetLen) {
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
                req.initialize((byte) 0x81);
                req.submit();

        }
        try {
          //  enableStreaming(false);
        } catch (Exception e) {
            System.out.println("Exception during enableStreaming(false): " + e);
        }
        System.out.println("requests=" + requestCnt + " packetCnt=" + packetCnt + " packetErrorCnt=" + packetErrorCnt + " packet0Cnt=" + packet0Cnt + ", packet12Cnt=" + packet12Cnt + ", packetDataCnt=" + packetDataCnt + " packetHdr8cCnt=" + packetHdr8Ccnt + " frameCnt=" + frameCnt);
        for (String s : logArray) {
            System.out.println(s);
        }
        kameraSchliessen();
    }

}
