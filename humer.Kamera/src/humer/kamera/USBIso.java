/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package humer.kamera;
// www.source-code.biz, www.inventec.ch/chdh
//
// This module is multi-licensed and may be used under the terms of any of the following licenses:
//
//  LGPL, GNU Lesser General Public License, V2.1 or later, http://www.gnu.org/licenses/lgpl.html
//  EPL, Eclipse Public License, V1.0 or later, http://www.eclipse.org/legal
//
// Please contact the author if you need another license.
// This module is provided "as is", without warranties of any kind.
//
// Home page: http://www.source-code.biz/snippets/java/UsbIso


import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author peter
 */
@SuppressWarnings({"PointlessBitwiseExpression", "unused", "SpellCheckingInspection"})


public class USBIso {
    
 

// Note: The layout and size of the USBFS structures matches that of Linux Kernel 3.2 and 3.14
// for ARM 32 bit. For other environments (X86, 64 bit, future Linux kernels), it might be
// necessary to adjust some values.

    private static final int usbSetIntSize = 8;                // size of struct usbdevfs_setinterface
    private static final int USBDEVFS_URB_TYPE_ISO = 0;
    private static final int USBDEVFS_URB_ISO_ASAP = 2;

    // IOCTL function codes:
    private static final int USBDEVFS_SETINTERFACE = (2 << 30) | (usbSetIntSize << 16) | (0x55 << 8) | 4;
    private static final int USBDEVFS_SUBMITURB = (2 << 30) | (Urb.urbBaseSize << 16) | (0x55 << 8) | 10;
    private static final int USBDEVFS_DISCARDURB = (0 << 30) | (0 << 16) | (0x55 << 8) | 11;
    private static final int USBDEVFS_REAPURB = (1 << 30) | (Pointer.SIZE << 16) | (0x55 << 8) | 12;
    public static final int USBDEVFS_REAPURBNDELAY = (1 << 30) | (Pointer.SIZE << 16) | (0x55 << 8) | 13;
    private static final int USBDEVFS_CLEAR_HALT = (2 << 30) | (4 << 16) | (0x55 << 8) | 21;

    //--- Native data structures ---------------------------------------------------
    private static final int EAGAIN = 11;
    private static final int ENODEV = 19;

    //--- Request object -----------------------------------------------------------
    private static final int EINVAL = 22;

    //--- Main logic ---------------------------------------------------------------
    private static Libc libc;
    private static boolean staticInitDone;
    private int fileDescriptor;
    private ArrayList<Request> requests = new ArrayList<>();
    private int maxPacketsPerRequest;
    private int maxPacketSize;
    Logger logger;
    
    
    public static int Benutzerkontext;

    
    // private native void nativePrint();
    // public native void nativePrint();
    public native int nativeIOCTLsenden(int dateiHandlung, int USBDEVFS_REAPURBNDELAY);
    public native int nativPaketStatus();
    public static native int fd();
        
    private Memory buffer = new Memory (3072);
    
    public Memory benutzerkontext = new Memory (1);
    

    
    /**
     * Creates an isochronous transfer controller instance.
     * <p>
     * The size of the data buffer allocated for each <code>Request</code> object is
     * <code>maxPacketsPerRequest * maxPacketSize</code>.
     *
     * @param fileDescriptor       For Android, this is the value returned by UsbDeviceConnection.getFileDescriptor().
     * @param maxPacketsPerRequest The maximum number of packets per request.
     * @param maxPacketSize        The maximum packet size.
     */
    public USBIso(int fileDescriptor, int maxPacketsPerRequest, int maxPacketSize) {
        staticInit();
        this.fileDescriptor = fileDescriptor;
        this.maxPacketsPerRequest = maxPacketsPerRequest;
        this.maxPacketSize = maxPacketSize;
    }

    private static synchronized void staticInit() {
        if (staticInitDone) {
            return;
        }
        if (new Usbdevfs_setinterface().size() != usbSetIntSize) {
            throw new RuntimeException("Value of usbSetIntSize constant does not match structure size.");
        }
        libc = (Libc) Native.loadLibrary("c", Libc.class);

        staticInitDone = true;
    }

    /**
     * Pre-allocates <code>n</code> {@link Request} objects with their associated buffer space.
     * <p>
     * The <code>UsbIso</code> class maintains an internal pool of <code>Request</code> objects.
     * Each <code>Request</code> object has native buffer space associated with it.
     * The purpose of this method is to save some execution time between the instant when
     * {@link #setInterface} is called to enable streaming, and the instant when {@link #reapRequest}
     * is called to receive the first data packet.
     *
     * @param n The minimum number of internal <code>Request</code> objects to be pre-allocated.
     */
    public void preallocateRequests(int n) {
        while (requests.size() < n) {
            new Request();
        }
        System.out.println("Alle Anfragen erfolgreich vorbereitet");
    }

    /**
     * Releases all resources associated with this class.
     * This method calls {@link #flushRequests} to cancel all pending requests.
     */
    public void dispose() throws IOException {
        try {
            flushRequests();
        } catch (LastErrorException e) {
            // This happens when the device has been disconnected.
            if (e.getErrorCode() != ENODEV) {
                throw e;
            }
        }
    }

    /**
     * Cancels all pending requests and removes all requests from the queue of the USB device driver.
     */
    public void flushRequests() throws IOException {
        cancelAllRequests();
        discardAllPendingRequests();
        int queuedRequests = countQueuedRequests();
        if (queuedRequests > 0) {
            throw new IOException("The number of queued requests after flushRequests() is " + queuedRequests + ".");
        }
    }

    private void cancelAllRequests() throws IOException {
        for (Request req : requests) {
            if (req.queued) {
                req.cancel();
            }
        }
    }

    /**
     * Sends a SET_INTERFACE command to the USB device.
     * <p>
     * Starting with Android 5.0, UsbDeviceConnection.setInterface() could be used instead.
     *
     * @param interfaceId The interface ID.
     *                    For Android, this is the value returned by <code>UsbInterface.getId()</code>.
     * @param altSetting  The alternate setting number. The value 0 is used to stop streaming.
     *                    For Android, this is the value returned by <code>UsbInterface.getAlternateSetting()</code> (only available since Android 5.0).
     *                    You may use <code>lsusb -v -d xxxx:xxxx</code> to find the alternate settings available for your USB device.
     */
    public void setInterface(int interfaceId, int altSetting) throws IOException {
        Usbdevfs_setinterface p = new Usbdevfs_setinterface();
        p.interfaceId = interfaceId;
        p.altsetting = altSetting;
        p.write();
        int rc = libc.ioctl(fileDescriptor, USBDEVFS_SETINTERFACE, p.getPointer());
        if (rc != 0) {
            throw new IOException("ioctl(USBDEVFS_SETINTERFACE) failed, rc=" + rc + ".");
        }
    }

    /**
     * Modeled after struct usbdevfs_setinterface in <linuxKernel>/include/uapi/linux/usbdevice_fs.h.
     */
    
    public static class Usbdevfs_setinterface extends Structure {
        public int interfaceId;
        public int altsetting;

        @Override
        protected List getFieldOrder() {
            return Arrays.asList(
                    "interfaceId",
                    "altsetting");
        }
    }
    
    
    /**
     * Returns an inactive <code>Request</code> object that can be submitted to the device driver.
     * <p>
     * The <code>UsbIso</code> class maintains an internal pool of all it's <code>Request</code> objects.
     * If the pool contains a <code>Request</code> object which is not in the request queue of the
     * USB device driver, that <code>Request</code> object is returned.
     * Otherwise a new <code>Request</code> object is created and returned.
     * <p>
     * The returned <code>Request</code> object must be initialized by calling {@link Request#initialize} and
     * can then be submitted by calling {@link Request#submit}.
     */
    public Request getRequest() {
        for (Request req : requests) {
            if (!req.queued) {
                return req;
            }
        }
        return new Request();
    }

//--- Static parts -------------------------------------------------------------

    /**
     * Returns a completed request.
     * <p>
     * A <code>Request</code> object returned by this method has been removed from the queue and can be re-used by calling {@link Request#initialize} and {@link Request#submit}.
     *
     * @param wait <code>true</code> to wait until a completed request is available. <code>false</code> to return immediately when no
     *             completed request is available.
     * @return A <code>Request</code> object representing a completed request, or <code>null</code> if
     * <code>wait</code> is <code>false</code> and no completed request is available at the time.
     */
    public Request reapRequest(boolean wait) {
     
        PointerByReference urbPointer = new PointerByReference();
        int func = wait ? USBDEVFS_REAPURB : USBDEVFS_REAPURBNDELAY;
        int rc;
      
        try {
            System.out.println("vor Reaprequest func = " + func +". Der Pointer hat folgenden Wert: " + Pointer.nativeValue(urbPointer.getValue()));
            System.out.println("vor Reaprequest fileDescriptor = " + fileDescriptor );
          //  func = USBDEVFS_REAPURBNDELAY;
          
              rc = libc.ioctl(fileDescriptor, func, urbPointer);
          
        } catch (LastErrorException e) {
            // logger.log(Level.SEVERE, null, e);
            System.out.println("ReapRequest fehlgeschlagen " +e);
            if (e.getErrorCode() == EAGAIN && !wait) {
                return null;
            }
            throw e;
        }
  
        System.out.println("nach Reaprequest func = " +func);
        if (rc != 0) {
            //throw new IOException("ioctl(USBDEVFS_REAPURB*) failed, rc=" + rc + ".");
        }
        
        int urbNdx = Urb.getUserContext(urbPointer.getValue());
        if (urbNdx < 0 || urbNdx >= requests.size()) {
          //  throw new IOException("URB.userContext returned by ioctl(USBDEVFS_REAPURB*) is out of range.");
        }
        Request req = requests.get(urbNdx);
        if (req.urbAddr != Pointer.nativeValue(urbPointer.getValue())) {
           // throw new IOException("Address of URB returned by ioctl(USBDEVFS_REAPURB*) does not match.");
        }
        if (!req.queued) {
           // throw new IOException("URB returned by ioctl(USBDEVFS_REAPURB*) was not queued.");
        }
        req.queued = false;
        req.initialized = false;
        return req;
    }
    
    
    
    
    
    public Request reapRequest1(boolean wait, int anzahl) throws IOException {
        
        Request req = requests.get(anzahl);
        Pointer p = req.p;
        System.out.println("Vor reapRequest1: p = " + p);
        long urbAddr = req.urbAddr;
        
        //Pointer p = urb.getNativeUrbPointer();
        //System.out.println("Vor reapRequest1: Pointer = " + p);
        //int bufSize = maxPacketsPerRequest * maxPacketSize;
        //System.out.println(" bufSize = " +bufSize);   //   bufSize = 12288
        //buffer = new Memory(bufSize);
        
        PointerByReference urbPointer = new PointerByReference(p);
        
        int func = wait ? USBDEVFS_REAPURB : USBDEVFS_REAPURBNDELAY;
        int rc;
      
        try {
            System.out.println("vor Reaprequest func = " + func +". Der Pointer hat folgenden Wert: " + Pointer.nativeValue(urbPointer.getValue()));
            //System.out.println("vor Reaprequest fileDescriptor = " + fileDescriptor );
            //  func = USBDEVFS_REAPURB;
            //System.out.println("vor Reap IOCTL URBAdresse = " +urbAddr);
          
            rc = libc.ioctl(fileDescriptor, func, urbPointer);
          
        } catch (LastErrorException e) {
            // logger.log(Level.SEVERE, null, e);
            System.out.println("ReapRequest fehlgeschlagen " +e);
            if (e.getErrorCode() == EAGAIN && !wait) {
                return null;
            }
            throw e;
           
        }
        
        if (rc == -1) {
            System.out.println("Urbstatus:  " + req.getUrbStaus());
            System.out.println("Paketstatus vom Paket 0:  " + req.getPacketStatus(0));
            
            req.queued = false;
            req.initialized = false;
            return req;
            
        } else {
  
        System.out.println("nach Reaprequest func = " +func);
        if (rc != 0) {
            //throw new IOException("ioctl(USBDEVFS_REAPURB*) failed, rc=" + rc + ".");
        }
        
        //int urbNdx = Urb.getUserContext(urbPointer.getValue());
        /*
        int urbNdx = 1;
        if (urbNdx < 0 || urbNdx >= requests.size()) {
            throw new IOException("URB.userContext returned by ioctl(USBDEVFS_REAPURB*) is out of range.");
        }
        //Request req = requests.get(urbNdx);
        /*
        if (req.urbAddr != Pointer.nativeValue(urbPointer.getValue())) {
            throw new IOException("Address of URB returned by ioctl(USBDEVFS_REAPURB*) does not match.");
        }
        
        if (req.urbAddr != 1) {
            throw new IOException("Address of URB returned by ioctl(USBDEVFS_REAPURB*) does not match.");
        }
        */
        if (!req.queued) {
            throw new IOException("URB returned by ioctl(USBDEVFS_REAPURB*) was not queued.");
        }
        req.queued = false;
        req.initialized = false;
        return req;
        }
        
    }
    

    @SuppressWarnings("StatementWithEmptyBody")
    private void discardAllPendingRequests() throws IOException {
        // bypass if we have never allocated any request
        if (requests.size() == 0) {
            return;
        }

        // to prevent errors when the USB device has been accessed by another method (e.g. using android.hardware.usb.UsbRequest)
        while (reapRequest(false) != null) ;
    }

    private int countQueuedRequests() {
        int ctr = 0;
        for (Request req : requests) {
            if (req.queued) {
                ctr++;
            }
        }
        return ctr;
    }

    private interface Libc extends Library {
        
        int ioctl(int fileHandle, int request, PointerByReference p) throws LastErrorException;

        int ioctl(int fileHandle, int request, Pointer p) throws LastErrorException;

        int ioctl(int fileHandle, int request, long i) throws LastErrorException;
        
    }

    /**
     * This class is modeled after struct usbdevfs_urb in <linuxKernel>/include/linux/usbdevice_fs.h
     * At first I implemented the URB structure directly using com.sun.jna.Structure, but that was extremely slow.
     * Therefore byte offsets are now used to access the fields of the structure.
     */
    private static class Urb {
        
          public static long a;
        /**
         * Base size of native URB (without iso_frame_desc) in bytes
         */
        public static final int urbBaseSize = 44;

        /**
         * Size of struct usbdevfs_iso_packet_desc
         */
        private static final int packetDescSize = 12;

        private ByteBuffer urbBuf;
        private long urbBufAddr;
        private int maxPackets;
        public Pointer p;

        public Urb(int maxPackets) {
            this.maxPackets = maxPackets;
            int urbSize = urbBaseSize + maxPackets * packetDescSize;
            // System.out.println("urbSize = " +urbSize);    //   44 + 4*12 = 92
            //urbBuf = ByteBuffer.allocateDirect(urbSize);
            urbBuf = ByteBuffer.allocateDirect(urbSize);
            urbBuf.order(ByteOrder.nativeOrder());
            //a = Pointer.nativeValue(Native.getDirectBufferPointer(urbBuf));
            //urbBufAddr = (int) Pointer.nativeValue(Native.getDirectBufferPointer(urbBuf));
            urbBufAddr = Pointer.nativeValue(Native.getDirectBufferPointer(urbBuf));
            //urbBufAddr = null;
            p = Native.getDirectBufferPointer(urbBuf);
        }

        public static int getUserContext(Pointer urbBufPointer) {
            return urbBufPointer.getInt(40);
        }

        public long getNativeUrbAddr() {
            //System.out.println("urbBufAddr = " +urbBufAddr);
            return urbBufAddr; 
        }
        
        public Pointer getNativeUrbPointer() {
            return p;
        }
        

        public void setType(int type) {
            urbBuf.put(0, (byte) type);
        }

        public void setEndpoint(int endpoint) {
            urbBuf.put(1, (byte) endpoint);
          //  System.out.println("endpoint = " +endpoint);
        }

        public int getStatus() {
            return urbBuf.getInt(4);
        }

        public void setStatus(int status) {
            urbBuf.putInt(4, status);
           // System.out.println("Bufferstatus = " +status);
        }

        public void setFlags(int flags) {
            urbBuf.putInt(8, flags);
        }

        public void setBuffer(Pointer buffer) {
            urbBuf.putInt(12, (int) Pointer.nativeValue(buffer));
           // System.out.println("buffer = " +buffer);    // buffer = allocated@0x7f02280160a0 (12288 bytes)
        }

        public void setBufferLength(int bufferLength) {
            urbBuf.putInt(16, bufferLength);
        //    System.out.println("Bufferlänge = " +bufferLength);   // 12288; // 4*3*1024 
        }

        public void setActualLength(int actualLength) {
            urbBuf.putInt(20, actualLength);
        //    System.out.println("actualLength = " +actualLength);   // 0
        }

        public void setStartFrame(int startFrame) {
            urbBuf.putInt(24, startFrame);
           // System.out.println("startFrame = " +startFrame);   // 0
        }

        public int getNumberOfPackets() {
            return urbBuf.getInt(28);
        }

        public void setNumberOfPackets(int numberOfPackets) {
            if (numberOfPackets < 0 || numberOfPackets > maxPackets) {
                throw new IllegalArgumentException();
            }
            urbBuf.putInt(28, numberOfPackets);
        }

        public void setErrorCount(int errorCount) {
            urbBuf.putInt(32, errorCount);
            
        }

        /**
         * signal to be sent on completion, or 0 if none should be sent
         *
         * @param signr sigNr
         */
        public void setSigNr(int signr) {
            urbBuf.putInt(36, signr);
        }

        public int getUserContext() {
            return urbBuf.getInt(40);
        }

        public void setUserContext(int userContext) {
            urbBuf.putInt(40, userContext);
            //Benutzerkontext = userContext;
            //System.out.println("userContext = " +userContext);
        }

        public void setPacketLength(int packetNo, int length) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            urbBuf.putInt(urbBaseSize + packetNo * packetDescSize, length);    // für packetNo = 0 == urbBaseSize = 44 packetDescSize = 12 packetNo = 0
            
            //System.out.println("urbBaseSize = " +urbBaseSize);
            //System.out.println("packetDescSize = " +packetDescSize);
            //System.out.println("packetNo = " +packetNo);
        }

        public int getPacketLength(int packetNo) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            return urbBuf.getInt(urbBaseSize + packetNo * packetDescSize);
        }

        public void setPacketActualLength(int packetNo, int actualLength) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            urbBuf.putInt(urbBaseSize + packetNo * packetDescSize + 4, actualLength);
        }

        public int getPacketActualLength(int packetNo) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            return urbBuf.getInt(urbBaseSize + packetNo * packetDescSize + 4);
        }

        public void setPacketStatus(int packetNo, int status) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            urbBuf.putInt(urbBaseSize + packetNo * packetDescSize + 8, status);
        }

        public int getPacketStatus(int packetNo) {
           // System.out.println("sadcasdor = ");
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            return urbBuf.getInt(urbBaseSize + packetNo * packetDescSize + 8);
            //System.out.println("Endpunktadresse vo");
        }
    }

    

    /**
     * This class represents an isochronous data transfer request that can be queued with the USB device driver.
     * One request object contains multiple data packets.
     * Multiple request objects may be queued at a time.
     * <p>
     * The sequence of actions on a <code>Request</code> object is typically:
     * <pre>
     *   UsbIso.getRequest()
     *   Request.initialize()
     *   ... For output: Set packet data to be sent to device ...
     *   Request.submit()
     *   repeatedly:
     *      UsbIso.reapRequest()
     *      ... For input: Check status and process received packet data ...
     *      ... For output: Check status ...
     *      Request.initialize()
     *      ... For output: Set packet data to be sent to device ...
     *      Request.submit()</pre>
     */
    public class Request {
        //long a = Urb.a;
        private boolean initialized;
        private boolean queued;
        private Urb urb;
        public long urbAddr;
        private Memory buffer;
        private int endpointAddr;
        int rc ;
        public Pointer p;

        private Request() {
            
            //System.out.println("urbBufAddr in long = " + a);
            
            urb = new Urb(maxPacketsPerRequest);
            urbAddr = urb.getNativeUrbAddr();
            p = urb.getNativeUrbPointer();
            int bufSize = maxPacketsPerRequest * maxPacketSize;
            //System.out.println(" bufSize = " +bufSize);   //   bufSize = 12288
            buffer = new Memory(bufSize);
            urb.setUserContext(requests.size());
            requests.add(this);
            
        }

        /**
         * Initializes this <code>Request</code> object for the next {@link #submit}.
         * For input, an initialized <code>Request</code> object can usually be submitted without change.
         * For output, data has to be copied into the packet data buffers before the <code>Request</code> object is submitted.
         *
         * @param endpointAddr The address of an isochronous USB endpoint.
         *                     For Android, this is the value returned by <code>UsbEndpoint.getAddress()</code>.
         */
        public void initialize(int endpointAddr) {
            if (queued) {
                throw new IllegalStateException();
            }
            this.endpointAddr = endpointAddr;
            urb.setEndpoint(endpointAddr);
            urb.setType(USBDEVFS_URB_TYPE_ISO);
            urb.setFlags(USBDEVFS_URB_ISO_ASAP);
            urb.setBuffer(buffer);
            urb.setBufferLength((int) buffer.size());
            urb.setActualLength(0);
            urb.setStartFrame(0);
            setPacketCount(maxPacketsPerRequest);
            urb.setErrorCount(0);
            urb.setSigNr(0);
            urb.setStatus(-1);
            for (int packetNo = 0; packetNo < maxPacketsPerRequest; packetNo++) {
                urb.setPacketLength(packetNo, maxPacketSize);
                urb.setPacketActualLength(packetNo, 0);
                urb.setPacketStatus(packetNo, -1);
            }
            initialized = true;
        }

        /**
         * Submits this request to the USB device driver.
         * The request is added to the queue of active requests.
         */
        public void submit() throws IOException {
            if (!initialized || queued) {
                throw new IllegalStateException();
            }
            initialized = false;
            //System.out.println("urbBufAddr in long = " + a);
            //System.out.println("vor IOCTL Submit URBAdresse = " +urbAddr);
           // urbAddr = urb.getNativeUrbAddr();
            System.out.println("vor Submit IOCTL Pointer = " +p);
            
            int rc = libc.ioctl(fileDescriptor, USBDEVFS_SUBMITURB, p); 
       
            //int rc = nativeIOCTLsenden(fileDescriptor, USBDEVFS_SUBMITURB);
            //System.out.println("nach URBAdresse = " +urbAddr);
           // System.out.println("URBAdresse");
            if (rc != 0) {
                throw new IOException("ioctl(USBDEVFS_SUBMITURB) failed, rc=" + rc + ".");
            }
            queued = true;
        }

        /**
         * Cancels a queued request.
         * The request remains within the queue amd must be removed from the queue by calling {@link UsbIso#reapRequest}.
         */
        public void cancel() throws IOException {
            int rc;
            try {
                rc = libc.ioctl(fileDescriptor, USBDEVFS_DISCARDURB, urbAddr);
            } catch (LastErrorException e) {
                if (e.getErrorCode() == EINVAL) {                 // This happens if the request has already completed.
                    return;
                }
                throw e;
            }
            if (rc != 0) {
                throw new IOException("ioctl(USBDEVFS_DISCARDURB) failed, rc=" + rc);
            }
        }
        
        /**
         * Returns the Status of this request.
         */
        
      
        public int getUrbStaus() {
            return urb.getStatus();
        }

        /**
         * Returns the endpoint address associated with this request.
         */
        public int getEndpointAddr() {
            return endpointAddr;
        }

        /**
         * Returns the packet count of this request.
         */
        
      
        public int getPacketCount() {
            return urb.getNumberOfPackets();
        }

        /**
         * May be used to modify the packet count.
         * The default packet count is <code>maxPacketsPerRequest</code> (see <code>UsbIso</code> constructor).
         */
        public void setPacketCount(int n) {
            if (n < 1 || n > maxPacketsPerRequest) {
                throw new IllegalArgumentException();
            }
            urb.setNumberOfPackets(n);
        }

        /**
         * Returns the completion status code of a packet.
         * For normal completion the status is 0.
         */
        public int getPacketStatus(int packetNo) {
            return urb.getPacketStatus(packetNo);
        }

        /**
         * May be used to modify the length of data to request for the packet.
         * The default packet length is <code>maxPacketSize</code> (see <code>UsbIso</code> constructor).
         */
        public void setPacketLength(int packetNo, int length) {
            if (length < 0 || length > maxPacketSize) {
                throw new IllegalArgumentException();
            }
            urb.setPacketLength(packetNo, length);
        }

        /**
         * Returns the amount of data that was actually transferred for the packet.
         * When reading, this is the number of data bytes received from the device.
         */
        public int getPacketActualLength(int packetNo) {
            return urb.getPacketActualLength(packetNo);
        }

        /**
         * Used to provide data to be sent to the device.
         */
        public void setPacketData(int packetNo, byte[] buf, int len) {
            if (packetNo < 0 || packetNo >= maxPacketsPerRequest || len > maxPacketSize) {
                throw new IllegalArgumentException();
            }
            buffer.write(packetNo * maxPacketSize, buf, 0, len);
        }

        /**
         * Used to retrieve data that has been received from the device.
         */
        public void getPacketData(int packetNo, byte[] buf, int len) {
            if (packetNo < 0 || packetNo >= maxPacketsPerRequest || len > maxPacketSize) {
                throw new IllegalArgumentException();
            }
            buffer.read(packetNo * maxPacketSize, buf, 0, len);
        }
    }
    
    public void submitUrbNative() {
        int rc = nativeIOCTLsenden(fileDescriptor, USBDEVFS_SUBMITURB);
        
    }
    
    public static class Usbdevfs_iso_packet_desc extends Structure {
	public int length;
	public int actual_length;
	public int status;
        
        @Override
        protected List getFieldOrder() {
            return Arrays.asList(
                    "length",
                    "actual_length",
                    "status");
        }
    }
    
    public class Usbdevfs_urb extends Structure {
	public char type;
	public char endpoint;
	public int status;
	public int flags;
	public Pointer puffer;
	public int buffer_length;
	public int actual_length;
	public int start_frame;
        public int number_of_packets;	/* Only used for isoc urbs */
	public int error_count;
	public int signr;	/* signal to be sent on completion, or 0 if none should be sent. */
	public Pointer usercontext;
        public Usbdevfs_iso_packet_desc usbdevfs_iso_packet_desc;
        //public Usbdevfs_iso_packet_desc[] usbdevfs_iso_packet_desc = new Usbdevfs_iso_packet_desc[8];
        //Usbdevfs_iso_packet_desc usbdevfs_iso_packet_desc = new Usbdevfs_iso_packet_desc();
        //Structure[] structs = usbdevfs_iso_packet_desc.toArray(8);
        //Usbdevfs_iso_packet_desc[] usbdevfs_iso_packet_desc = (Usbdevfs_iso_packet_desc[])usbdevfs_iso_packet_desc.toArray(8);
	//struct usbdevfs_iso_packet_desc iso_frame_desc[0];
     
        
        @Override
        protected List getFieldOrder() {
            return Arrays.asList(
                    "type",
                    "endpoint",
                    "status",
                    "flags",
                    "puffer",
                    "buffer_length",
                    "actual_length",
                    "start_frame",
                    "number_of_packets",
                    "error_count",
                    "signr",
                    "usercontext",
                    "usbdevfs_iso_packet_desc");
        }
        
    }
    
    public void submitUsbdevfs_urb(int anzPakete, int benutzerkontext) throws LastErrorException {
        
        Pointer zeiger = new Memory (8);
        zeiger.setInt(0, 0);
        System.out.println("vor Usbdevfs_urb");
        Usbdevfs_urb p = new Usbdevfs_urb();
        System.out.println("nach Usbdevfs_urb");
        p.type = USBDEVFS_URB_TYPE_ISO;
        p.endpoint = 0x81;
        p.status = -1;
        p.flags = USBDEVFS_URB_ISO_ASAP;
        p.puffer = buffer;
        p.buffer_length = 3072;
        p.actual_length = 0;
        p.start_frame = 0;
        p.number_of_packets = 1;
        p.error_count = 0;
        p.usercontext = zeiger;
        System.out.println("vor for (int packetNo = 0;");
        /*for (int packetNo = 0; packetNo < maxPacketsPerRequest; packetNo++) {
            //p.usbdevfs_iso_packet_desc.actual_length;
            p.usbdevfs_iso_packet_desc[packetNo].actual_length = 0;
            p.usbdevfs_iso_packet_desc[packetNo].length = maxPacketSize;
            p.usbdevfs_iso_packet_desc[packetNo].status = -1;
        }*/
        p.usbdevfs_iso_packet_desc.actual_length = 0;
        p.usbdevfs_iso_packet_desc.length = maxPacketSize;
                p.usbdevfs_iso_packet_desc.status = -1;
        p.write();
        
        System.out.println("vor Submit IOCTL Pointer = " +p.getPointer());
                
        int rc = libc.ioctl(fileDescriptor, USBDEVFS_SUBMITURB, p.getPointer());
        if (rc != 0) {
            throw new LastErrorException("ioctl(USBDEVFS_SUBMITURB) failed, rc=" + rc + ".");
        }
        
        System.out.println("nach Submit IOCTL Pointer = " +p.getPointer());
        
        
        rc = libc.ioctl(fileDescriptor, USBDEVFS_REAPURB, p.getPointer());
        if (rc != 0) {
            throw new LastErrorException("ioctl(USBDEVFS_REAPURB) failed, rc=" + rc + ".");
        }
        
        System.out.println("nach USBDEVFS_REAPURB IOCTL Pointer = " +p.getPointer());
           
        
        
            
    }

    
    
    

}