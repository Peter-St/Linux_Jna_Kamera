
package humer.kamera;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import static humer.kamera.Ioctl._IO;
import static humer.kamera.Ioctl._IOR;
import static humer.kamera.Ioctl._IOW;
import static humer.kamera.Ioctl._IOWR;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

public interface usbdevice_fs {
    public static final byte USBDEVFS_URB_TYPE_ISO = 0;
    public static final int USBDEVFS_URB_ISO_ASAP = 2;

    public static final int USBDEVFS_MAXDRIVERNAME = 255;
    
    // IOCTL function codes:
    public static final int USBDEVFS_SETINTERFACE = _IOR('U', 4, new usbdevfs_setinterface().size());
    public static final int USBDEVFS_SUBMITURB = _IOR('U', 10, new Urb.usbdevfs_urb().size());
    public static final int USBDEVFS_DISCARDURB = _IO('U', 11);
    public static final int USBDEVFS_REAPURB = _IOW('U', 12, Pointer.SIZE);
    public static final int USBDEVFS_REAPURBNDELAY = _IOW('U', 13, Pointer.SIZE);
    public static final int USBDEVFS_CLEAR_HALT = _IOR('U', 21, 4);
    public static final int USBDEVFS_RELEASEINTERFACE = _IOR('U', 16, 4);
    public static final int USBDEVFS_GETDRIVER = _IOW('U', 8, new usbdevfs_getdriver().size());
    public static final int USBDEVFS_IOCTL = _IOWR('U', 18, new usbdevfs_ioctl().size());
    public static final int USBDEVFS_DISCONNECT = _IO('U', 22);
    public static final int USBDEVFS_CONNECT = _IO('U', 23);
    public static final int USBDEVFS_CLAIMINTERFACE = _IOR('U', 15, 4);
    public static final int USBDEVFS_CONTROL = _IOWR('U', 0, new usbdevfs_ctrltransfer().size());

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
            int urbSize = urbBaseSize + maxPackets * packetDescSize;
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

    public static class usbdevfs_getdriver extends Structure {
        public int ifno;
        public byte[] driver = new byte[USBDEVFS_MAXDRIVERNAME + 1];

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("ifno", "driver");
        }
    }

    public static class usbdevfs_ioctl extends Structure {
        public int ifno;
        public int ioctl_code;
        public Pointer data;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("ifno", "ioctl_code", "data");
        }
    }

    public static class usbdevfs_ctrltransfer extends Structure {
        public byte bRequestType;
        public byte bRequest;
        public short wValue;
        public short wIndex;
        public short wLength;
        public int timeout; /* in ms */
        public Pointer data;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("bRequestType", "bRequest", "wValue", "wIndex", "wLength", "timeout", "data");
        }
    }

    /**
     * Modeled after struct usbdevfs_setinterface in <linuxKernel>/include/uapi/linux/usbdevice_fs.h.
     */
    public static class usbdevfs_setinterface extends Structure {
        public int interfaceId;
        public int altsetting;

        @Override
        protected List getFieldOrder() {
            return Arrays.asList(
                    "interfaceId",
                    "altsetting");
        }
    }
}
