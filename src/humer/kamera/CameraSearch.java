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

import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.usb4java.ConfigDescriptor;
import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.EndpointDescriptor;
import org.usb4java.Interface;
import org.usb4java.InterfaceDescriptor;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;


/**
 *
 * @author peter
 */
public class CameraSearch {
    
    
    private static javax.swing.JTextPane infoPanel;
    private static javax.swing.JScrollPane infoPanelScrollPane;
    private ArrayList<Device> device = new ArrayList<>();
    private PhraseUvcDescriptor phrasedUvcDescriptor;
    private int camera;
    StringBuilder stringBuilder;
    private String cameraDescripton;
    public int BUS, DEVICE;
    public byte endpunktadresse;
    public boolean uvcDescriptorPhrased = false;
    public int[] maxPacketSizeArray;
    
    
        
    
    
    public CameraSearch() {
      stringBuilder = new StringBuilder();
    }
    
    public PhraseUvcDescriptor getPhrasedUvcDescriptor() {
        return phrasedUvcDescriptor;
    }
    
    
    
    public String autoSearchTheCamera() {
        Device camDevice = null;
        Context context = new Context();
        try {        
            findCam(camDevice, context);
        } catch (Exception ex) {
            Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (device.size() > 0) {
            stringBuilder = new StringBuilder();
            listDevice(device.get(camera));
        }
        LibUsb.exit(context);
        device.removeAll(device);
        return cameraDescripton;
    }
    
    private void listDevice (Device camDevice) {
        //Device camDevice = device.get(camera);
        DEVICE = LibUsb.getDeviceAddress(camDevice);
        BUS = LibUsb.getBusNumber(camDevice);
        cameraDescripton = String.format("Camera adress: \n Bus = %03d \n Device = %03d", BUS, DEVICE);
        //infoPanel.setText(String.format("Es wurde folgende Kamera gewählt: \n Bus = %03d \n Device = %03d", BUS, DEVICE));
        stringBuilder.append(cameraDescripton);
        DeviceDescriptor descriptor = new DeviceDescriptor();
        int result = LibUsb.getDeviceDescriptor(camDevice, descriptor);
        if (result != LibUsb.SUCCESS)
            throw new LibUsbException("Unable to read device descriptor", result);
        Interface	usbInterface = null;
        EndpointDescriptor ep_desc;
        InterfaceDescriptor iDescriptor;
        ConfigDescriptor config = new ConfigDescriptor();
        System.out.println(" ");
        System.out.println("LIST DEVICE ");
        System.out.println(" ");
        stringBuilder.append("\n\nLIST DEVICE\n\n");
        // get for this device the number of all configuration possible 
        //normally 1 configuration per device.
        for (int i = 0; i < descriptor.bNumConfigurations(); i++) {
            result = LibUsb.getConfigDescriptor(camDevice, (byte) i, config);
            System.out.println("Es wurden   " +config.bNumInterfaces()+"   Interfaces gezählt."  );
	    stringBuilder.append(String.format("Es wurden:   %d    Interfaces gezählt.\n", config.bNumInterfaces())  );   
            for (int j = 0; j < config.bNumInterfaces(); j++) {
                usbInterface = config.iface()[j]; 
                System.out.println("");
                stringBuilder.append("\n");
                boolean first = true;
                System.out.println("Interface: " + (j));
                System.out.println("So viele Altsettings sind vorhanden: " + usbInterface.numAltsetting());
                if (j==1) maxPacketSizeArray = new int [usbInterface.numAltsetting()];
                                
                for (int k = 0; k < usbInterface.numAltsetting(); k++) { 
                        iDescriptor = usbInterface.altsetting()[k]; 
                        if (first) { 
                            if (iDescriptor.bInterfaceNumber() ==0) stringBuilder.append(String.format("CameraControlInterface:\n"))   ;
                            if (iDescriptor.bInterfaceNumber() ==1) stringBuilder.append(String.format("CameraStreamInterface:\n"))   ;
                            stringBuilder.append(String.format("Interface %d:   [ id = %d ]   [ class =  %d ]  [ subclass = %d ] [ protocol = %d ] \n\n", (j+1), iDescriptor.bInterfaceNumber(), iDescriptor.bInterfaceClass(), iDescriptor.bInterfaceSubClass(), iDescriptor.bInterfaceProtocol()    ) );
                            System.out.println("[ -Interface " + (j+1) + " - ] [: id=" + iDescriptor.bInterfaceNumber() + " ] [ class=" + iDescriptor.bInterfaceClass() + " ] [ subclass=" + iDescriptor.bInterfaceSubClass() + " ] [ protocol=" + iDescriptor.bInterfaceProtocol() +" ] ");
                        }
                        
                        
                        if (k == 0 && j == 1) {
                            // get the extra descriptor for the video resolution and video intervall
                            
                            ByteBuffer bb = ByteBuffer.allocate(iDescriptor.extraLength());
                            bb = iDescriptor.extra();
                            System.out.println("Extra Bytebuffer:\nByteBuffer length =" + bb.limit());
                            phrasedUvcDescriptor = new PhraseUvcDescriptor(bb);
                            int ret = phrasedUvcDescriptor.phraseUvcData();
                            if (ret == 0) {
                                uvcDescriptorPhrased = true;
                            }
                        }
                        
                        first = false;                        
                        int deviceInterface = (int) iDescriptor.bInterfaceNumber(); 
                        int endpoints = iDescriptor.bNumEndpoints();
                        //First Altsetting of the VideoStreamInterface has no Endpoint
                        //Normally 1 Endpoint per Altsetting
                        for (int o = 0; o < endpoints; o++) {
                            ep_desc = iDescriptor.endpoint()[o];
                            if (j==1 && k > 0) maxPacketSizeArray[k] = ep_desc.wMaxPacketSize();
                            System.out.println("[- Altsetting: " + k + "] [: Endpunktadresse = " + String.format("0x%01X", ep_desc.bEndpointAddress()) + "] [Length= " + ep_desc.bLength() + "] [ attrs= " + ep_desc.bmAttributes() + "] [ interval = " + ep_desc.bInterval() + "] [ maxPacketSize = " + ep_desc.wMaxPacketSize() + " ] [ type = " + ep_desc.bDescriptorType()+"] ");
                            stringBuilder.append(String.format("[- Altsetting: %d ] [: Endpunktadresse = 0x%01X ] [Length= %d] [ attrs= %d ] [ interval = %d ] [ maxPacketSize = %d ] [ type = %d ]\n", k , ep_desc.bEndpointAddress(), ep_desc.bLength(), ep_desc.bmAttributes(), ep_desc.bInterval(), ep_desc.wMaxPacketSize(), ep_desc.bDescriptorType()));
                            if ((ep_desc.bmAttributes() & LibUsb.TRANSFER_TYPE_MASK) == LibUsb.TRANSFER_TYPE_ISOCHRONOUS && ep_desc.wMaxPacketSize() != 0) {
                                endpunktadresse = ep_desc.bEndpointAddress();
                            }
                        }    
                }
            }
            if (endpunktadresse != 0) {
                System.out.println( String.format("Endpointadresse gesetzt: 0x%02X", (0xFF & endpunktadresse)));
            }
        }        
        LibUsb.freeConfigDescriptor(config);
        //stringBuilder.append(cameraDescripton);  
        cameraDescripton = stringBuilder.toString();
        
        //kam.stringBuilder.append(cameraDescripton);
        //kam.infoPanelSetText(cameraDescripton);
        //infoPanel.setText(cameraDescripton);
    } 
 			  			
   
    private void findCam(Device camDevice, Context context) throws Exception {
       // if (findeDieKamera == 0) {
            camDevice = findCameraDevice(context);            
            System.out.println("Camera found");

            if (camDevice == null) {
                throw new Exception("No USB camera device found."); }
      //  }
        
        
        //findeDieKamera = 1;
        }


    private Device findCameraDevice(Context context) {
        String dateiName;
        DeviceHandle dhandle = new DeviceHandle();
        // Read the USB device list
        DeviceDescriptor descriptor = new DeviceDescriptor();
	InterfaceDescriptor    iDescriptor = null;
	EndpointDescriptor    ep_desc = null;
	int		result = LibUsb.init(context);
        if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to initialize libusb.", result);
        DeviceList list = new DeviceList();
        result = LibUsb.getDeviceList(context, list);
        System.out.println("USB devices count = " + result);
        if (result < 0)
        {
            throw new LibUsbException("Unable to get device list", result);
        }
            for (Device usbDevice: list) {
                DEVICE = LibUsb.getDeviceAddress(usbDevice);
                BUS = LibUsb.getBusNumber(usbDevice);
                System.out.format("Device at: Bus %03d, Device %03d",  BUS, DEVICE);
                result = LibUsb.getDeviceDescriptor(usbDevice, descriptor);
                if (result != LibUsb.SUCCESS)
                    throw new LibUsbException("Unable to read device descriptor", result);
                if (checkDeviceHasVideoStreamingInterface(usbDevice, descriptor)) {    
                    result = LibUsb.getDeviceDescriptor(usbDevice, descriptor);                                        
                    dateiName = ("/dev/bus/usb/00" + BUS +"/00" + DEVICE);                    
                    System.out.format("\nFound a camera device. The connection name is: %s \n", dateiName);
                    device.add(usbDevice);
                    //return usbDevice;
                } else System.out.format("--> no Kamera Device (no UVC Device) --> skipping ...\n");
            }      
        LibUsb.freeDeviceList(list, true);
        if (device.isEmpty()) return null;
        else if (device.size() == 1) { 
            camera = 0;
            return device.get(0);
        }
        else {
            JOptionPane.showMessageDialog(null, String.format("---  %d   Camera's are connected! ---\nPlease see the overthiew in the next box. \n", device.size()),"More than one camera found", JOptionPane.INFORMATION_MESSAGE);
        for (int p = 0; p < device.size(); p++) {
            
            listDevice(device.get(p));
        }
        JOptionPane.showMessageDialog(null, stringBuilder.toString(),"Following camera are connected", JOptionPane.INFORMATION_MESSAGE);
        String name = JOptionPane.showInputDialog("Please type in which Camera to select:\n0 is the first camera\n1 is the second camera ... ");
        // get the user's input. note that if they press Cancel, 'name' will be null
        camera = Integer.parseInt(name);
        JOptionPane.showMessageDialog(null, camera ,"Camera choosen.", JOptionPane.INFORMATION_MESSAGE);
        return device.get(camera);
        }
    }
        
    private boolean checkDeviceHasVideoStreamingInterface (Device usbDevice, DeviceDescriptor descriptor) {
        return getVideoStreamingInterface(usbDevice, descriptor) != null; }

    private Interface getVideoControlInterface (Device usbDevice, DeviceDescriptor descriptor) {
        return findInterface(usbDevice, descriptor, LibUsb.CLASS_VIDEO, USBIso.SC_VIDEOCONTROL, false);
    	}


     private Interface getVideoStreamingInterface (Device usbDevice, DeviceDescriptor descriptor) {
        return findInterface(usbDevice, descriptor, LibUsb.CLASS_VIDEO, USBIso.SC_VIDEOSTREAMING, true); 
     	}



     private Interface findInterface (Device usbDevice, DeviceDescriptor descriptor, int interfaceClass, int interfaceSubclass, boolean withEndpoint) {
    	 Interface	usbInterface = null;
         ConfigDescriptor config = new ConfigDescriptor();
         int result;
         InterfaceDescriptor iDescriptor;
    	// get for this device the number of all configuration possible 
    	  for (int i = 0; i < descriptor.bNumConfigurations(); i++) { 
    	    
    		  // init config_desc, if failed, raised exception 
    		  if ((result = LibUsb.getConfigDescriptor(usbDevice, (byte) i, 
    				  config)) != LibUsb.SUCCESS) 
    			  throw new LibUsbException( 
    					  "Unable to initialize ConfigDescriptor.\n It may be null or do not exist", result); 
    		  for (int j = 0; j < config.bNumInterfaces(); j++) { 
    			  usbInterface = config.iface()[j]; 
    			  for (int k = 0; k < usbInterface.numAltsetting(); k++) { 
    				  iDescriptor = usbInterface.altsetting()[k]; 
    				  //if (iDescriptor.bInterfaceClass() != LibUsb.CLASS_VENDOR_SPEC) 
    				  // continue; 
    	     
    	     
    	     
    				  // get Number of interface from InterfaceDescriptor 
    				  int deviceInterface = (int) iDescriptor.bInterfaceNumber(); 
    	 
    				  for (int l = 0; l < iDescriptor.bNumEndpoints(); l++) { 
    					//  System.out.println("interface" + i);
    					//  if (iDescriptor.bInterfaceClass() == interfaceClass)		 {    	 System.out.println("Klasse passt");};
    					//  if (iDescriptor.bInterfaceSubClass() == interfaceSubclass) 	{    	 System.out.println("Unterklasse passt");};
    					//  if (!withEndpoint || iDescriptor.bNumEndpoints() > 0) 		{    	 System.out.println("Endpunkte passen");};
    				   
    					  
                                        if (iDescriptor.bInterfaceClass() == interfaceClass && iDescriptor.bInterfaceSubClass() == interfaceSubclass && (!withEndpoint || iDescriptor.bNumEndpoints() > 0)) {
                                            LibUsb.freeConfigDescriptor(config);
                                            return usbInterface;}}}
    		  	}			    
    	  }	
          LibUsb.freeConfigDescriptor(config);
  		return null;

     } 
     
    
}
