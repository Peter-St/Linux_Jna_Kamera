/*
 * Copyright 2019 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package humer.kamera;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Formatter;
import javax.xml.bind.DatatypeConverter;

/**
 *
 * @author peter
 */

//bDescriptorSubtype                  7 (FRAME_MJPEG)



public class PhraseUvcDescriptor {
    
    private final static byte VS_input_header = 0x01;
    private final static byte VS_still_image_frame = 0x03; 
    private final static byte VS_format_uncompressed = 0x04; 
    private final static byte VS_frame_uncompressed = 0x05;
    private final static byte VS_format_mjpeg = 0x06;
    private final static byte VS_frame_mjpeg = 0x07;
    private final static byte VS_colour_format = 0x0D;
    public final ArrayList<PhraseUvcDescriptor.FormatIndex> formatIndex = new ArrayList<>();
    
    
    ByteBuffer uvcData;
    
    
    public PhraseUvcDescriptor (ByteBuffer data) {
        this.uvcData = data;
    }
    
    private static void printData (byte [] formatData) {
        
        Formatter formatter = new Formatter();
            for (byte b : formatData) {
                formatter.format("0x%02x ", b);
            }
            String hex = formatter.toString();

            System.out.println("hex " + hex);
    }
            
    
    
    public int phraseUvcData() {
        try {
        ArrayList<byte []> frameData = new ArrayList<>();
        int formatcnt = 0;
        byte[] formatData = null;
        FormatIndex.Videoformat videoFormat; 
        int positionAbsolute = 0;
        int posStart, posEnd;
        do  {
            uvcData.position(positionAbsolute);
            int pos = uvcData.position();
            byte descSize = uvcData.get(pos);
            byte descType = uvcData.get(pos +1);
            byte descSubType = uvcData.get(pos + 2);
            
            
            if (descSubType == VS_format_uncompressed) {
                formatData = new byte [descSize];
                uvcData.get(formatData, 0 ,descSize);
                frameData = new ArrayList<>();
                printData(formatData);

            }
            else if (descSubType == VS_frame_uncompressed) {
                byte [] uncompressedFrameData = new byte [descSize];
                uvcData.get(uncompressedFrameData, 0 ,descSize);
                frameData.add(uncompressedFrameData);
                if (uvcData.get(pos + descSize + 2) != VS_frame_uncompressed) {
                    videoFormat = FormatIndex.Videoformat.yuy2;
                    FormatIndex formatUncomprIndex = new FormatIndex(formatData, frameData);
                    formatUncomprIndex.init();
                    formatIndex.add(formatUncomprIndex);
                }
            }
            else if (descSubType == VS_format_mjpeg) {
                formatData = new byte [descSize];
                uvcData.get(formatData, 0 ,descSize);
                frameData = new ArrayList<>();
            }
            else if (descSubType == VS_frame_mjpeg) {
                byte [] uncompressedFrameData = new byte [descSize];
                uvcData.get(uncompressedFrameData, 0 ,descSize);
                frameData.add(uncompressedFrameData);
                if (uvcData.get(pos + descSize + 2) != VS_frame_uncompressed) {
                    videoFormat = FormatIndex.Videoformat.yuy2;
                    FormatIndex formatUncomprIndex = new FormatIndex(formatData, frameData);
                    formatUncomprIndex.init();
                    formatIndex.add(formatUncomprIndex);
                } 
            }
            positionAbsolute += descSize;
        } while (uvcData.limit() > positionAbsolute);
        System.out.println("UvcDescriptor finished.");
        return 0;
        
        } catch ( Exception e ) {e.printStackTrace(); }
        
        return -1;
    }
    
    
    
    
    public void getFrameIntervallPerFrameIndex () {
        
    }
    
    public FormatIndex getFormatIndex(int n) {
        return formatIndex.get(n);
    }
    
    
    public static class FormatIndex {
        
        public ArrayList<PhraseUvcDescriptor.FormatIndex.FrameIndex> frameIndex = new ArrayList<>();
        public final byte[] formatData;
        public final ArrayList<byte []> frameData;
        public int formatIndexNumber;
        public int numberOfFrameDescriptors;
        public enum Videoformat {yuy2, mjpeg}
        public Videoformat videoformat;
        public String guidFormat = new String();
        
        
        public FormatIndex(byte[] format, ArrayList<byte []> frame){
            this.formatData = format;
            this.frameData = frame;
        }
        
        public void init() {
            // add more formats later ..
                       
            
            formatIndexNumber = formatData[3]; 
            numberOfFrameDescriptors = formatData[4];
            System.out.println("(FormatData) formatIndexNumber = " + formatIndexNumber);
            System.out.println("(FormatData) formatData[2] = " + formatData[2]);System.out.println("(FormatData) formatData[2] = " + formatData[2]);
            if (formatData[2] ==  VS_format_uncompressed ) {
                // Guid Data
                Formatter formatter = new Formatter();
            
                for (int b=0; b<16 ; b++) {
                    formatter.format("%02x", formatData[(b + 5) & 0xFF]);
                }   
                guidFormat = formatter.toString();
                System.out.println("guidFormat = " + guidFormat);
                if (guidFormat.equals("5955593200001000800000aa00389b71") ) {
                    videoformat = Videoformat.yuy2;
                    System.out.println("videoformat = Videoformat.yuy2");
                }
                else guidFormat = "unknown";
            }
            if (formatData[2] ==  VS_format_mjpeg ) {
                videoformat = Videoformat.mjpeg;
            }
            for (int i = 0; i < frameData.size(); i++) {
                byte[] buf = new byte [frameData.get(i).length];
                buf = frameData.get(i);
                int index = buf[3];
                int pos = 5;
                int width = ((buf[pos+1] & 0xFF) << 8) | (buf[pos] & 0xFF); 
                //int width = (buf[7]  << 8)  |  buf[6] & 0xFF ;
                int height = ((buf[pos+3] & 0xFF) << 8)  |  (buf[pos+2] & 0xFF) ;
                int [] frameintervall = new int[(buf.length - 26) /4];
                pos = 26;
                int x = 0;
                do {
                    frameintervall[x] = (buf[pos + 3] << 24) | ((buf[pos + 2] & 0xFF) << 16) | ((buf[pos + 1] & 0xFF) << 8) | (buf[pos] & 0xFF);
                System.out.println("frameintervall[x] = " + frameintervall[x]);
                    x++;
                    pos += 4;
                } while (buf.length > pos);
                FrameIndex frameIndexData = new FrameIndex(index, width, height, frameintervall);
                frameIndex.add(frameIndexData);
            }
        }
        
        public FrameIndex getFrameIndex(int n) {
            return frameIndex.get(n);
        }
        
        public static class FrameIndex{
            
            int frameIndex;
            int [] dwFrameInterval;
            int wWidth;
            int wHeight;
            
            public FrameIndex(int index, int width, int height, int[] frameInterval){
                this.frameIndex = index;
                this.wWidth = width;
                this.wHeight = height;
                this.dwFrameInterval = frameInterval;
                
            }
        }
    }
}
    
/*

//// PRINT BYTEBUFFER:


Bytebuffer = uvcData
int n = uvcData.limit();
            uvcData.rewind(); // Already done by flip I think.
            byte[] data = new byte[n];
            uvcData.get(data);
            
            Formatter formatter = new Formatter();
            for (byte b : data) {
                formatter.format("0x%02x ", b);
            }
            String hex = formatter.toString();

            System.out.println("hex " + hex);
*/