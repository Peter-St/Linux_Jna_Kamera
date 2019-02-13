# Linux_Jna_Kamera
Camera stream over Jna.

Still Under Development !

Explaination: (To know how the program works)


1) 


Read out the camera specifications with the terminal command:
 " lsusb -v -d xxxx:xxxx "  -->
  xxxx:xxxx = ProductID : VendorID


2)


Set the camera specifications: (Set different values for you camera device in the Kam.java file) (Edit the following values)

  BUS = 1;
  DEVICE = 5;
  
  --> Bus & Vendor ID --> Will be set automatically to find the camera in near future
 
  ALT_SETTING;
  
  maxPacketSize;
  
  --> To set the packet size of the stream (to try out different settings)
  
  private static final int camStreamingInterfaceNum = 1;
  private static final int camControlInterfaceNum = 0;
  private static final int endpunktadresse = 0x81;
  
   --> Will be found automatically in near future
    

  private int                   camFormatIndex
  
  private int                   camFrameIndex
    
  imageWidth
  
  imageHeight
  
  camFrameInterval
    
  --> here you set the Frame Format, The Frame Resolution and the Intervall of the frames, which you receive from the camera.
  
  packetsPerRequest
  activeUrbs
  
  --> here you can set different setting for the camera stream: More Packets means bigger Usb Request Blocks. The activeUrbs are the number of Request blocks at same time.



3) (This step is, when you press the Kamera button in the program)


Take a look at the camera frames you receive with your settings. To know how big be a Frame should be, you can look at the output of the controlltransfer of the camera in the log: maxVideoFrameSize, This value is returned from the camera and should be the valid frame size (The value is calculated by " Imagewidth x Imagehight x 2 ").
  The first method: testIsochronousRead1 shows you how the frames is structered by the camera. Different camerasetting == Different Frame structers. Try it out with different setting and look at the output. The eof hint shows the framesize in the log. For valid camera settings the size should be the same as maxFrameSize value of the controlltransfer. You can use the serach function in the log ...
  
  So far ...
  Some new features will be added soon (Videostream ... )
  
  
  Peter

  
