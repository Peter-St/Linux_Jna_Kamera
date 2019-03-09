
package humer.kamera;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import static humer.kamera.SaveToFile.paths;
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
import java.util.Date;
import javax.swing.JPanel;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import javax.swing.JOptionPane;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class Kam extends javax.swing.JFrame {
    // REQUIRED CONFIGURATION
    public static  int BUS = 1;
    public static  int DEVICE = 5;
    public static  int ALT_SETTING = 6; // 7 = 3*1024 bytes packet size // 6 = 3*896 // 5 = 2*1024 // 4 = 2*768 // 3 = 1x 1024 // 2 = 1x 512 // 1 = 128 //
    // ADDITIONAL CONFIGURATION
    private static final String DUMP_FILE = "target/test.dump";
    private static final int CAM_STREAMING_INTERFACE_NUM = 1;
    private static final int CAM_CONTROL_INTERFACE_NUM = 0;
    public static  byte ENDPOINT_ADDRESS = (byte) 0x81;
    public static  int CAM_FORMAT_INDEX = 1;   // MJPEG // YUV // bFormatIndex: 1 = uncompressed
    public static  int CAM_FRAME_INDEX = 1; // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
    public static  int CAM_FRAME_INTERVAL = 333333; // 333333 YUV = 30 fps // 666666 YUV = 15 fps
    public static  int PACKETS_PER_REQUEST = 8;
    public static  int MAX_PACKET_SIZE = 3072;
    public static  int ACTIVE_URBS = 16;
    
    public static int imageWidth = 1280;
    public static int imageHeight = 720;
    public static String videoformat = "mjpeg";

    public static String DEVICE_PATH = String.format("/dev/bus/usb/%03d/%03d", BUS, DEVICE);

    private volatile IsochronousRead1 runningTransfer;
    private volatile IsochronousStream runningTransferStream;

    private int fd;
    
    // Global Objects for the Automatic CameraSearch and CameraSettings
    public StringBuilder stringBuilder;
    public String cameraDescripton;
    public CameraSearch cs;
    public JLabel video;
    private ImageIcon icon;
    
    private SaveToFile stf;
    private volatile boolean stopKamera = false;
    
    private enum OptionForInit {savetofile, camerasearch }
    private OptionForInit optionForInit;

    public void usbIsoLinux() throws IOException {
        DEVICE_PATH = String.format("/dev/bus/usb/%03d/%03d", BUS, DEVICE);
        System.out.println("devpath = " + DEVICE_PATH);
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
            
        }
        usbdevice_fs_util.setInterface(fd, CAM_STREAMING_INTERFACE_NUM, 0);
        ioctlControltransfer();
        infoPanel.setText(stringBuilder.toString());
        usbdevice_fs_util.setInterface(fd, CAM_STREAMING_INTERFACE_NUM, ALT_SETTING);
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
        if (stringBuilder == null) stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        stringBuilder.append("---- Initial streaming parms: ----\n");
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
        stringBuilder.append("---- Probed streaming parms: ----\n");
        stringBuilder.append(dumpStreamingParms(buffer.getByteArray(0, 26)));
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
            stringBuilder.append("---- Final streaming parms: ----\n");
            stringBuilder.append(dumpStreamingParms(buffer.getByteArray(0, 26)));
            stringBuilder.append("\n");
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
    
    public Kam(int a) {
        
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButton1 = new javax.swing.JButton();
        jPopupMenu1 = new javax.swing.JPopupMenu();
        Kamera = new javax.swing.JButton();
        infoPanelScrollPane = new javax.swing.JScrollPane();
        infoPanel = new javax.swing.JTextPane();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        AutoSearchTheCameras = new javax.swing.JMenuItem();
        EditTheValues = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        RestoreValues = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        jMenu2 = new javax.swing.JMenu();
        Isoread1 = new javax.swing.JMenuItem();

        jButton1.setText("jButton1");

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

        jMenu1.setForeground(new java.awt.Color(159, 126, 25));
        jMenu1.setText("AutoFind / Edit / Open / Save");
        jMenu1.setFont(new java.awt.Font("Noto Sans", 0, 14)); // NOI18N

        AutoSearchTheCameras.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        AutoSearchTheCameras.setForeground(new java.awt.Color(133, 85, 85));
        AutoSearchTheCameras.setText("Automatic search for a Camera");
        AutoSearchTheCameras.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AutoSearchTheCamerasActionPerformed(evt);
            }
        });
        jMenu1.add(AutoSearchTheCameras);

        EditTheValues.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        EditTheValues.setForeground(new java.awt.Color(133, 85, 85));
        EditTheValues.setText("Edit / Save    - -> the camera values");
        EditTheValues.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EditTheValuesActionPerformed(evt);
            }
        });
        jMenu1.add(EditTheValues);
        jMenu1.add(jSeparator1);

        RestoreValues.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        RestoreValues.setForeground(new java.awt.Color(133, 85, 85));
        RestoreValues.setText("Restore Camera Settings");
        RestoreValues.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RestoreValuesActionPerformed(evt);
            }
        });
        jMenu1.add(RestoreValues);
        jMenu1.add(jSeparator2);

        jMenuBar1.add(jMenu1);

        jMenu2.setForeground(new java.awt.Color(228, 28, 244));
        jMenu2.setText("Isoread");
        jMenu2.setFont(new java.awt.Font("Noto Sans", 0, 14)); // NOI18N

        Isoread1.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
        Isoread1.setText("Isoread1");
        Isoread1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Isoread1ActionPerformed(evt);
            }
        });
        jMenu2.add(Isoread1);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(infoPanelScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 466, Short.MAX_VALUE)
                    .addComponent(Kamera, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addComponent(infoPanelScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 218, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(Kamera)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void EditTheValuesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EditTheValuesActionPerformed
        if (stf == null) stf = new SaveToFile();
        stf.startEditSave();
        updateValues(OptionForInit.savetofile);
    }//GEN-LAST:event_EditTheValuesActionPerformed

    private void Isoread1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Isoread1ActionPerformed
        
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
        
    }//GEN-LAST:event_Isoread1ActionPerformed

    private void RestoreValuesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RestoreValuesActionPerformed
        if (stf == null) stf = new SaveToFile();
        stf.startRestore();
        updateValues(OptionForInit.savetofile);
    }//GEN-LAST:event_RestoreValuesActionPerformed

    private void AutoSearchTheCamerasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AutoSearchTheCamerasActionPerformed
        if (cs == null)  cs = new CameraSearch();
        stringBuilder = new StringBuilder();
        stringBuilder.append(cs.autoSearchTheCamera());
        infoPanel.setText(stringBuilder.toString());
        if (cs.uvcDescriptorPhrased == true) {
            Object[] options = {"Select from UVC Values", "Dismiss !"};
            int option = JOptionPane.showOptionDialog(null, "A UVC Camera has been found" ,"Would you like to set up the camera with the detected UVC values ?", JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE,null,options , options[0]);
            if (option == JOptionPane.OK_OPTION) {
                stf = new SaveToFile();
                stf.setUpWithUvcValues(cs);
            }
        }
        updateValues(OptionForInit.camerasearch);
    }//GEN-LAST:event_AutoSearchTheCamerasActionPerformed



    private void KameraActionPerformed(java.awt.event.ActionEvent evt) {
        if(runningTransferStream != null) {
            return;
        }
        stopKamera = false;
        try {
            // TODO add your handling code here:
            usbIsoLinux();
            
        } catch (IOException ex) {
            Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
        }

        runningTransferStream = new IsochronousStream();
        runningTransferStream.start();
        
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
    private javax.swing.JMenuItem AutoSearchTheCameras;
    private javax.swing.JMenuItem EditTheValues;
    private javax.swing.JMenuItem Isoread1;
    private javax.swing.JButton Kamera;
    private javax.swing.JMenuItem RestoreValues;
    public javax.swing.JTextPane infoPanel;
    private javax.swing.JScrollPane infoPanelScrollPane;
    private javax.swing.JButton jButton1;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
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
    
    private String dumpStreamingParms (byte[] p) {
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
        s.append(" maxPayloadTransferSize=" + unpackUsbInt(p, 22));
        return s.toString(); 
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
    
    class IsochronousStream extends Thread {
        
        ConvertStream convertStream = new ConvertStream(imageWidth, imageHeight);
        
        public IsochronousStream() {
            setPriority(Thread.MAX_PRIORITY);
        }
        
        private void initUI() {
            JDialog dialog = new JDialog();
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            dialog.addWindowListener(new WindowAdapter()   {
                public void windowClosed(WindowEvent e)  {
                        System.out.println("jdialog window closed event received");
                        stopKamera = true;
                }
                public void windowClosing(WindowEvent e)  {
                    System.out.println("jdialog window closing event received");
                    stopKamera = true;
                    
                }
            });
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));
            /*
            JTextField textfield = new JTextField(8);
            textfield.setBounds(10, 10, 40, 20);
            panel.add(textfield);
            */
            video = new javax.swing.JLabel();
            video.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            //video.setText("Übertragung startet");
        
            dialog.add(video);
            dialog.setSize(imageWidth, imageHeight);
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        }
        
        
        @Override
        public void run() {
            
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    initUI();
                }
            });
            USBIso usbIso = new USBIso(fd, PACKETS_PER_REQUEST, MAX_PACKET_SIZE, ENDPOINT_ADDRESS);
            usbIso.preallocateRequests(ACTIVE_URBS);
            ByteArrayOutputStream frameData = new ByteArrayOutputStream(0x20000);
            long startTime = System.currentTimeMillis();
            int skipFrames = 0;
            // if (cameraType == CameraType.wellta) {
            //    skipFrames = 1; }                                // first frame may look intact but it is not always intact
            boolean frameComplete = false;
            byte[] data = new byte[MAX_PACKET_SIZE];
            try {
                usbIso.submitUrbs();
            } catch (IOException ex) {
                    Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
            }   
            stopKamera = false;
            while (true) {
                try {
                    USBIso.Request req = usbIso.reapRequest(true);
                    for (int packetNo = 0; packetNo < req.getNumberOfPackets(); packetNo++) {
                        int packetStatus = req.getPacketStatus(packetNo);
                        try {if (packetStatus != 0) {
                            skipFrames = 1;}
                        //    throw new IOException("Camera read error, packet status=" + packetStatus);
                        } catch (Exception e){
                            System.out.println("Camera read error, packet status=" + packetStatus);
                        }
                        int packetLen = req.getPacketActualLength(packetNo);
                        if (packetLen == 0) {
                            // if (packetLen == 0 && frameData.size() > 0) {         // assume end of frame
                            //   endOfFrame = true;
                            //   break; }
                            continue;
                        }
                        if (packetLen > MAX_PACKET_SIZE) {
                            System.out.println("packetLen > maxPacketSize");
                        }
                        req.getPacketData(packetNo, data, packetLen);
                        int headerLen = data[0] & 0xff;
                        
                        try { if (headerLen < 2 || headerLen > packetLen) {
                            skipFrames = 1;
                        }
                        } catch (Exception e) {
                            System.out.println("Invalid payload header length.");
                        }
                        int headerFlags = data[1] & 0xff;
                        int dataLen = packetLen - headerLen;
                        boolean error = (headerFlags & 0x40) != 0;
                        if (error && skipFrames == 0) {
                            skipFrames = 1;
                        }
                        if (dataLen > 0 && skipFrames == 0) {
                            frameData.write(data, headerLen, dataLen);
                        }
                        /////////////////////////////////// Frame completion handling ///////////////////////////////
                        if ((headerFlags & 2) != 0) {
                            if (skipFrames > 0) {
                                System.out.println("Skipping frame, len= " + frameData.size());
                                frameData.reset();
                                skipFrames--;
                            }
                            else {
                                frameData.write(data, headerLen, dataLen);
                                byte[] jpg = convertStream.processReceivedVideoFrame(frameData.toByteArray(), videoformat);
                                icon = new ImageIcon(jpg);
                                video.setIcon(icon);
                                frameData.reset();
                            }
                        }
                    }
                    req.initialize();
                    req.submit();
                    if (stopKamera == true) {
                        System.out.println("stopKamera == true ... breaking the Loop ...");
                        break;
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
                } catch (Exception ex) {
                    Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            //enableStreaming(false);
            //processReceivedMJpegVideoFrame(frameData.toByteArray());
            //saveReceivedVideoFrame(frameData.toByteArray());
            System.out.println("OK");
            try {
                kameraSchliessen();
            } catch (IOException ex) {
                Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
            }
            runningTransferStream = null;
            }

        }
    
    
        public void infoPanelSetText (String text){
            stringBuilder.append(text);
            infoPanel.setText(stringBuilder.toString());
        }
    
    private void saveThePicture(byte[] mRgbImage) throws FileNotFoundException{
        
        StringBuilder path = new StringBuilder();
            
        Date date = new Date() ;
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss") ;
        path.append("/home/peter/Foxlink/");
        path.append(dateFormat.format(date));
        path.append(".jpg");
        
        try (FileOutputStream stream = new FileOutputStream(path.toString())) {        
            stream.write(mRgbImage);            
        } catch (IOException ex) {
            Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void updateValues(OptionForInit option){
        optionForInit = option;
        switch (optionForInit) {
            case savetofile: 
                if (stf != null) {
                    BUS = stf.sbus;
                    DEVICE = stf.sdevice;
                    ENDPOINT_ADDRESS = stf.sendpunktadresse;
                    DEVICE_PATH = stf.sdevicePath;
                    ALT_SETTING = stf.sALT_SETTING;
                    videoformat = stf.svideoformat;
                    CAM_FORMAT_INDEX = stf.scamFormatIndex;
                    imageWidth = stf.simageWidth;
                    imageHeight = stf.simageHeight;
                    CAM_FRAME_INDEX = stf.scamFrameIndex;
                    CAM_FRAME_INTERVAL = stf.scamFrameInterval;
                    PACKETS_PER_REQUEST = stf.spacketsPerRequest;
                    MAX_PACKET_SIZE = stf.smaxPacketSize;
                    ACTIVE_URBS = stf.sactiveUrbs;
                    System.out.printf("SaveToFile entries setted \n");
                } else System.out.printf("SaveToFile = NULL ... using default entries .. \n");
                
            case camerasearch:
                if (cs != null) {
                    BUS = cs.BUS;
                    DEVICE = cs.DEVICE;
                    ENDPOINT_ADDRESS = cs.endpunktadresse;
                    DEVICE_PATH = String.format("/dev/bus/usb/%03d/%03d", BUS, DEVICE);
                    ALT_SETTING = stf.sALT_SETTING;
                    videoformat = stf.svideoformat;
                    CAM_FORMAT_INDEX = stf.scamFormatIndex;
                    imageWidth = stf.simageWidth;
                    imageHeight = stf.simageHeight;
                    CAM_FRAME_INDEX = stf.scamFrameIndex;
                    CAM_FRAME_INTERVAL = stf.scamFrameInterval;
                    PACKETS_PER_REQUEST = stf.spacketsPerRequest;
                    MAX_PACKET_SIZE = stf.smaxPacketSize;
                    ACTIVE_URBS = stf.sactiveUrbs;
                    System.out.printf("CameraSearch entries setted \n");
                } else System.out.printf("CameraSearch = NULL ... using default entries .. \n");
            default: break;
        }
        System.out.printf("CAM_FRAME_INDEX = " + CAM_FRAME_INDEX + "   /   devpath = " + DEVICE_PATH + "  /  camFrameInterval  = " + CAM_FRAME_INTERVAL + "  /  bus = " + BUS + "  /  imageWidth = " + imageWidth+ "  /  imageHeight = " + imageHeight);
    }
}
