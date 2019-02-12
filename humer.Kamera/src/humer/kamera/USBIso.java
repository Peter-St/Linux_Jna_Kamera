// Copyright 2015 Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland
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

package humer.kamera;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;
import static humer.kamera.Ioctl._IO;
import static humer.kamera.Ioctl._IOR;
import static humer.kamera.Ioctl._IOW;
import humer.kamera.USBIso.Request;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * An USB isochronous transfer controller.
 * <p>
 * This class is used to read and write from an isochronous endpoint of an USB device.
 * It uses JNA to access the USBFS API via IOCTL calls.
 * USBFS is available in the Linux kernel and can be accessed from an Android application.
 * <p>
 * This class is independent of Android and could also be used under other Linux based operating systems.
 * <p>
 * The following program logic may be used e.g. for reading the video data stream from an UVC compliant camera:
 * <pre>
 *   ... set streaming parameters via control channel (SET_CUR VS_COMMIT_CONTROL, etc.) ...
 *   usbIso.preallocateRequests(n);
 *   usbIso.setInterface(interfaceId, altSetting);       // enable streaming
 *   for (int i = 0; i &lt; n; i++) {                       // submit initial transfer requests
 *      Request req = usbIso.getRequest();
 *      req.initialize(endpointAddr);
 *      req.submit(); }
 *   while (...) {                                       // streaming loop
 *      Request req = usbIso.reapRequest(true);          // wait for next request completion
 *      .. process received data ...
 *      req.initialize(endpointAddr);                    // re-use the request
 *      req.submit(); }                                  // re-submit the request
 *   usbIso.setInterface(interfaceId, 0);                // disable streaming
 *   usbIso.flushRequests();                             // remove pending requests</pre>
 *
 * Note that for e.g. an USB2 UVC camera, data packets arrive at a rate of 125 microseconds per packet.
 * This corresponds to 8000 packets per second. Each packet may contain up to 3072 bytes.
 *
 * @see <a href="https://www.kernel.org/doc/htmldocs/usb/usbfs.html">USBFS</a>
 * @see <a href="http://en.wikipedia.org/wiki/Java_Native_Access">JNA</a>
 * @see <a href="http://en.wikipedia.org/wiki/Ioctl">IOCTL</a>
 */
@SuppressWarnings({"PointlessBitwiseExpression", "unused", "SpellCheckingInspection"})
public class USBIso {
    
 

// Note: The layout and size of the USBFS structures matches that of Linux Kernel 3.2 and 3.14
// for ARM 32 bit. For other environments (X86, 64 bit, future Linux kernels), it might be
// necessary to adjust some values.

    private static final int usbSetIntSize = new Usbdevfs_setinterface().size();                // size of struct usbdevfs_setinterface
    private static final byte USBDEVFS_URB_TYPE_ISO = 0;
    private static final int USBDEVFS_URB_ISO_ASAP = 2;

    // IOCTL function codes:
    public static final int USBDEVFS_SETINTERFACE = _IOR('U', 4, new Usbdevfs_setinterface().size());
    public static final int USBDEVFS_SUBMITURB = _IOR('U', 10, new USBIso.Urb.usbdevfs_urb().size());
    public static final int USBDEVFS_DISCARDURB = _IO('U', 11);
    public static final int USBDEVFS_REAPURB = _IOW('U', 12, Pointer.SIZE);
    public static final int USBDEVFS_REAPURBNDELAY = _IOW('U', 13, Pointer.SIZE);
    public static final int USBDEVFS_CLEAR_HALT = _IOR('U', 21, 4);

    //--- Native data structures ---------------------------------------------------
    private static final int EAGAIN = 11;
    private static final int ENODEV = 19;

    //--- Request object -----------------------------------------------------------
    private static final int EINVAL = 22;

    //--- Main logic ---------------------------------------------------------------
    private int fileDescriptor;
    private ArrayList<Request> requests = new ArrayList<>();
    private static int maxPacketsPerRequest;
    private int maxPacketSize;
        
    

    
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
        this.fileDescriptor = fileDescriptor;
        this.maxPacketsPerRequest = maxPacketsPerRequest;
        this.maxPacketSize = maxPacketSize;
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
        int rc = Libc.INSTANCE.ioctl(fileDescriptor, USBDEVFS_SETINTERFACE, p.getPointer());
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
    public Request reapRequest(boolean wait) throws IOException {
     
        PointerByReference urbPointer = new PointerByReference();
        int func = wait ? USBDEVFS_REAPURB : USBDEVFS_REAPURBNDELAY;
        int rc;
      
        rc = Libc.INSTANCE.ioctl(fileDescriptor, func, urbPointer);
          
        if (rc != 0) {
            throw new IOException("ioctl(USBDEVFS_REAPURB*) failed, rc=" + rc + ".");
        }
        
        Urb urb = new Urb(urbPointer.getValue());
        int urbNdx = urb.getUserContext();
        if (urbNdx < 0 || urbNdx >= requests.size()) {
            throw new IOException("URB.userContext returned by ioctl(USBDEVFS_REAPURB*) is out of range.");
        }
        Request req = requests.get(urbNdx);
        if (! req.getUrb().getNativeUrbAddr().equals(urbPointer.getValue())) {
            throw new IOException("Address of URB returned by ioctl(USBDEVFS_REAPURB*) does not match.");
        }
        if (!req.queued) {
            throw new IOException("URB returned by ioctl(USBDEVFS_REAPURB*) was not queued.");
        }
        req.queued = false;
        req.initialized = false;
        return req;
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
    /**
     * This class is modeled after struct usbdevfs_urb in <linuxKernel>/include/linux/usbdevice_fs.h
     * At first I implemented the URB structure directly using com.sun.jna.Structure, but that was extremely slow.
     * Therefore byte offsets are now used to access the fields of the structure.
     */
    public static class Urb {
        /**
         * At the end of usbdevfs_urb follows an array of usbdevfs_iso_packet_desc
         * these are not modelled in this case, as JNA gets the offsets wrong in
         * this case
         */
        public static final class usbdevfs_urb extends Structure {
            public byte type;
            public byte endpoint;
            public int status;
            public int flags;
            public Pointer buffer;
            public int buffer_length;
            public int actual_length;
            public int start_frame;
            public int number_of_packets_stream_id; // this is a union
            public int error_count;
            public int signr;
            public Pointer usercontext;

            @Override
            protected List<String> getFieldOrder() {
                return Arrays.asList("type", "endpoint", "status", "flags",
                        "buffer", "buffer_length", "actual_length","start_frame",
                        "number_of_packets_stream_id", "error_count",
                        "signr", "usercontext");
            }

            @Override
            public int fieldOffset(String field) {
                return super.fieldOffset(field);
            }
        }

        public static final class usbdevfs_iso_packet_desc extends Structure {
            public int length;
            public int actual_length;
            public int status;

            @Override
            protected List<String> getFieldOrder() {
                return Arrays.asList("length", "actual_length", "status");
            }

            @Override
            public int fieldOffset(String field) {
                return super.fieldOffset(field);
            }
        }

        /**
         * Base size of native URB (without iso_frame_desc) in bytes
         */
        public static final int urbBaseSize;

        /**
         * Size of struct usbdevfs_iso_packet_desc
         */
        private static final int packetDescSize;

        public static final int usbdevfs_urb_type;
        public static final int usbdevfs_urb_endpoint;
        public static final int usbdevfs_urb_status;
        public static final int usbdevfs_urb_flags;
        public static final int usbdevfs_urb_buffer;
        public static final int usbdevfs_urb_buffer_length;
        public static final int usbdevfs_urb_actual_length;
        public static final int usbdevfs_urb_start_frame;
        public static final int usbdevfs_urb_number_of_packets_stream_id;
        public static final int usbdevfs_urb_error_count;
        public static final int usbdevfs_urb_signr;
        public static final int usbdevfs_urb_usercontext;
        public static final int usbdevfs_iso_packet_desc_length;
        public static final int usbdevfs_iso_packet_desc_actual_length;
        public static final int usbdevfs_iso_packet_desc_status;

        static {
            usbdevfs_urb urb = new Urb.usbdevfs_urb();
            usbdevfs_iso_packet_desc desc = new Urb.usbdevfs_iso_packet_desc();
            urbBaseSize = urb.size();
            packetDescSize = desc.size();
            usbdevfs_urb_type = urb.fieldOffset("type");
            usbdevfs_urb_endpoint = urb.fieldOffset("endpoint");
            usbdevfs_urb_status = urb.fieldOffset("status");
            usbdevfs_urb_flags = urb.fieldOffset("flags");
            usbdevfs_urb_buffer = urb.fieldOffset("buffer");
            usbdevfs_urb_buffer_length = urb.fieldOffset("buffer_length");
            usbdevfs_urb_actual_length = urb.fieldOffset("actual_length");
            usbdevfs_urb_start_frame = urb.fieldOffset("start_frame");
            usbdevfs_urb_number_of_packets_stream_id = urb.fieldOffset("number_of_packets_stream_id");
            usbdevfs_urb_error_count = urb.fieldOffset("error_count");
            usbdevfs_urb_signr = urb.fieldOffset("signr");
            usbdevfs_urb_usercontext = urb.fieldOffset("usercontext");
            usbdevfs_iso_packet_desc_length = desc.fieldOffset("length");
            usbdevfs_iso_packet_desc_actual_length = desc.fieldOffset("actual_length");
            usbdevfs_iso_packet_desc_status = desc.fieldOffset("status");
        }

        private ByteBuffer urbBuf;
        private final Pointer urbBufPointer;
        private int maxPackets;

        public Urb(int maxPackets) {
            this.maxPackets = maxPackets;
            int urbSize = urbBaseSize + maxPackets * packetDescSize;
            urbBuf = ByteBuffer.allocateDirect(urbSize);
            urbBuf.order(ByteOrder.nativeOrder());
            urbBufPointer = Native.getDirectBufferPointer(urbBuf);
        }

        public Urb(Pointer urbPointer) {
            //this.maxPackets = this.getNumberOfPackets();
            int urbSize = urbBaseSize + maxPacketsPerRequest * packetDescSize;
            this.urbBufPointer = urbPointer;
            this.urbBuf = urbBufPointer.getByteBuffer(0, urbSize);
        }

        public Pointer getNativeUrbAddr() {
            return urbBufPointer;
        }

        public void setType(byte type) {
            urbBuf.put(usbdevfs_urb_type, type);
        }

        public void setEndpoint(byte endpoint) {
            urbBuf.put(usbdevfs_urb_endpoint,  endpoint);
        }

        public int getStatus() {
            return urbBuf.getInt(usbdevfs_urb_status);
        }

        public void setStatus(int status) {
            urbBuf.putInt(usbdevfs_urb_status, status);
        }

        public void setFlags(int flags) {
            urbBuf.putInt(usbdevfs_urb_flags, flags);
        }

        public void setBuffer(Pointer buffer) {
            if(Pointer.SIZE == 4) {
                urbBuf.putInt(usbdevfs_urb_buffer, (int) Pointer.nativeValue(buffer));
            } else if (Pointer.SIZE == 8) {
                urbBuf.putLong(usbdevfs_urb_buffer, Pointer.nativeValue(buffer));
            } else {
                throw new IllegalStateException("Unhandled Pointer Size: " + Pointer.SIZE);
            }
        }

        public void setBufferLength(int bufferLength) {
            urbBuf.putInt(usbdevfs_urb_buffer_length, bufferLength);
        }

        public void setActualLength(int actualLength) {
            urbBuf.putInt(usbdevfs_urb_actual_length, actualLength);
        }

        public void setStartFrame(int startFrame) {
            urbBuf.putInt(usbdevfs_urb_start_frame, startFrame);
        }

        public int getNumberOfPackets() {
            return urbBuf.getInt(usbdevfs_urb_number_of_packets_stream_id);
        }

        public void setNumberOfPackets(int numberOfPackets) {
            if (numberOfPackets < 0 || numberOfPackets > maxPackets) {
                throw new IllegalArgumentException();
            }
            urbBuf.putInt(usbdevfs_urb_number_of_packets_stream_id, numberOfPackets);
        }

        public void setErrorCount(int errorCount) {
            urbBuf.putInt(usbdevfs_urb_error_count, errorCount);
        }

        /**
         * signal to be sent on completion, or 0 if none should be sent
         *
         * @param signr sigNr
         */
        public void setSigNr(int signr) {
            urbBuf.putInt(usbdevfs_urb_signr, signr);
        }

        public int getUserContext() {
            if(Pointer.SIZE == 4) {
                return urbBuf.getInt(usbdevfs_urb_usercontext);
            } else if (Pointer.SIZE == 8) {
                return (int) urbBuf.getLong(usbdevfs_urb_usercontext);
            } else {
                throw new IllegalStateException("Unhandled Pointer Size: " + Pointer.SIZE);
            }
        }

        public void setUserContext(int userContext) {
            if(Pointer.SIZE == 4) {
                urbBuf.putInt(usbdevfs_urb_usercontext, userContext);
            } else if (Pointer.SIZE == 8) {
                urbBuf.putLong(usbdevfs_urb_usercontext, userContext);
            } else {
                throw new IllegalStateException("Unhandled Pointer Size: " + Pointer.SIZE);
            }
        }

        public void setPacketLength(int packetNo, int length) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            urbBuf.putInt(urbBaseSize + packetNo * packetDescSize + usbdevfs_iso_packet_desc_length, length);    // für packetNo = 0 == urbBaseSize = 44 packetDescSize = 12 packetNo = 0
        }

        public int getPacketLength(int packetNo) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            return urbBuf.getInt(urbBaseSize + packetNo * packetDescSize + usbdevfs_iso_packet_desc_length);
        }

        public void setPacketActualLength(int packetNo, int actualLength) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            urbBuf.putInt(urbBaseSize + packetNo * packetDescSize + usbdevfs_iso_packet_desc_actual_length, actualLength);
        }

        public int getPacketActualLength(int packetNo) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            return urbBuf.getInt(urbBaseSize + packetNo * packetDescSize + usbdevfs_iso_packet_desc_actual_length);
        }

        public void setPacketStatus(int packetNo, int status) {
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            urbBuf.putInt(urbBaseSize + packetNo * packetDescSize + usbdevfs_iso_packet_desc_status, status);
        }

        public int getPacketStatus(int packetNo) {
           // System.out.println("sadcasdor = ");
            if (packetNo < 0 || packetNo >= maxPackets) {
                throw new IllegalArgumentException();
            }
            return urbBuf.getInt(urbBaseSize + packetNo * packetDescSize + usbdevfs_iso_packet_desc_status);
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
        private boolean initialized;
        private boolean queued;
        private Urb urb;
        private Memory buffer;
        private int endpointAddr;

        private Request() {
            urb = new Urb(maxPacketsPerRequest);
            int bufSize = maxPacketsPerRequest * maxPacketSize;
            buffer = new Memory(bufSize);
            urb.setUserContext(requests.size());
            requests.add(this);
        }

        public Urb getUrb() {
            return urb;
        }

        /**
         * Initializes this <code>Request</code> object for the next {@link #submit}.
         * For input, an initialized <code>Request</code> object can usually be submitted without change.
         * For output, data has to be copied into the packet data buffers before the <code>Request</code> object is submitted.
         *
         * @param endpointAddr The address of an isochronous USB endpoint.
         *                     For Android, this is the value returned by <code>UsbEndpoint.getAddress()</code>.
         */
        public void initialize(byte endpointAddr) {
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
            // System.out.println("vor IOCTL Submit URBAdresse = " + urb.getNativeUrbAddr());
           // urbAddr = urb.getNativeUrbAddr();
            //System.out.println("nach native get URBAdresse = " +urbAddr);
            int rc = (Libc.INSTANCE).ioctl(fileDescriptor, USBDEVFS_SUBMITURB, urb.getNativeUrbAddr());

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
                rc = (Libc.INSTANCE).ioctl(fileDescriptor, USBDEVFS_DISCARDURB, urb.getNativeUrbAddr());
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

}
