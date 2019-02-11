
package humer.kamera;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
Kamera mit folgendem Befehl im Terminal auslesen:
lsusb -v -d 05c8:0233

Folgende Variablen müssen gesetzt werden:

In C:

camStreamingInterfaceNum                 // normalerweise 1;
camControlInterfaceNum                   //  normalerweise 0;
endpunktadresse                   // 0x81

camFormatIndex                        
camFrameIndex                  
camFrameInterval
packetsPerRequest
ANZAHL_URBS
bConfigurationValue
camStreamingAltSetting
maxPacketSize
    int packetsPerRequest =  8;
#define  ANZAHL_URBS 16


*/
public class Kam extends javax.swing.JFrame {
    private static final int BUS = 1;
    private static final int DEVICE = 5;
    private static final int ALT_SETTING = 6; // 7 = 3*1024 bytes packet size // 6 = 3*896 // 5 = 2*1024 // 4 = 2*768 // 3 = 1x 1024 // 2 = 1x 512 // 1 = 128 //
    private boolean               backgroundJobActive;
    private int					  camStreamingAltSetting;
                    
    private boolean               bulkMode;
    private int                   camFormatIndex;   // MJPEG // YUV
    private int                   camFrameIndex = 5; // Foxlink // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
    private int                   camFrameInterval;
    private USBIso                usbIso;
    private int                   packetsPerRequest = 8;
    private int                     bConfigurationValue;
    public int                   maxPacketSize = 3072;
    private int                   imageWidth;
    private int                   imageHeight;
    private int                   activeUrbs = 16;
    private boolean               camIsOpen;
    public int dateiHandlung;
    int maxVideoFrameGroesse;
    
    public native void usbIsoLinux();
    public native void kameraSchliessen();
    
    static {  
        System.out.println("Bibliothek wird geladen");
        System.load(new File("../Kam_c/dist/Kam.so").getAbsolutePath());
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
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
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
    }// </editor-fold>//GEN-END:initComponents

    private void KameraActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_KameraActionPerformed
        // TODO add your handling code here:
        usbIsoLinux();
        
        
        
        //dateiHandlung = geraetetreiber(kameras, camFrameIndex);
        usbIso = new USBIso(dateiHandlung, packetsPerRequest, maxPacketSize);
        usbIso.preallocateRequests(activeUrbs);
        //usbIso.preallocateRequests(16);
        
        
        
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
//GEN-LAST:event_KameraActionPerformed
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton Kamera;
    // End of variables declaration//GEN-END:variables


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
   public void dateiHandlungSetzen(int dateiH){
    
        dateiHandlung = dateiH;
    }
   
    public void einstellungDerKamera (int camStreamingAltSettingJni, int maxPacketSizeJNI, int packetsPerRequestJni,
            int activeUrbsJni, int camFormatIndexJni, int camFrameIndexJni, int imageWidthJni, int imageHeightJni) {
        camStreamingAltSetting = camStreamingAltSettingJni;              // 7 = 3x1024 bytes packet size // 6 = 3x 896 // 5 = 2x 1024 // 4 = 2x 768 // 3 = 1x 1024 // 2 = 1x 512 // 1 = 128 // 
        maxPacketSize = maxPacketSizeJNI;
        camFormatIndex = camFormatIndexJni;                       // bFormatIndex: 1 = uncompressed
        camFrameIndex = camFrameIndexJni;                        // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
        camFrameInterval = 333333;                 // 333333 YUV = 30 fps // 666666 YUV = 15 fps
        packetsPerRequest = packetsPerRequestJni;
        activeUrbs = activeUrbsJni;
        imageWidth = imageWidthJni;
        imageHeight = imageHeightJni;
        System.out.println("imageWidth = " + imageWidth + " imageHeight = " + imageHeight);
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

private void testIsochronousRead1() {
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
        while (System.currentTimeMillis() - time0 < 10000) {
        for (int i=0; i<activeUrbs; i++) { 
            try {
                // Thread.sleep(0, 1);               // ??????????
                boolean stopReq = false;
                USBIso.Request req = usbIso.reapRequest1(true, i);
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
                        
                        
                        //if (headerLen < 2 || headerLen > packetLen) {
                        //    throw new IOException("Invalid payload header length. headerLen=" + headerLen + " packetLen=" + packetLen);
                        //}
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
                            //if (frameCnt == 10) {
                            //    sendStillImageTrigger(); }           // test ***********
                            frameLen = 0;
                        }
                    }
                    //if (packetLen == 0 && frameLen > 0) {
                    //   logEntry.append(" assumed EOF, framelen=" + frameLen);
                    //   frameLen = 0; }
                    //int streamErrorCode = getVideoStreamErrorCode();
                    //if (streamErrorCode != 0) {
                    //   logEntry.append(" streamErrorCode=" + streamErrorCode); }
                    //int controlErrorCode = getVideoControlErrorCode();
                    // if (controlErrorCode != 0) {
                    //  logEntry.append(" controlErrorCode=" + controlErrorCode); }
                    logArray.add(logEntry.toString());
                }
                if (stopReq) {
                    break;
                }
                //int i = 0 ;
                requestCnt++;
                req.initialize((byte) 0x81);
                req.submit();
            } catch (IOException ex) {
                Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
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
