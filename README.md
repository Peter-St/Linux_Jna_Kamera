# Linux_Jna_Kamera
Camera stream over Jna.

Still Under Development !

Explaination: (To know how the program works)


1) 


Read out the camera specifications with the terminal command:
  
- list with `lsusb` to get all connected devices
- Detail informations can be received with Vendor+Produkt ID: `lsusb -v -d <vendor>:<product>`
- Variant over: `lsusb -v -s <bus>:<device>`


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


- Take a look at the camera frames you receive with your settings. To know how big be a Frame should be, you can look at the output of the controlltransfer of the camera in the log: maxVideoFrameSize, This value is returned from the camera and should be the valid frame size (The value is calculated by " `Imagewidth x Imagehight x 2` ").
  The first method: testIsochronousRead1 shows you how the frames is structered by the camera. Different camerasetting == Different Frame structers. Try it out with different setting and look at the output. The eof hint shows the framesize in the log. For valid camera settings the size should be the same as maxFrameSize value of the controlltransfer. You can use the serach function in the log ...
  
  
So far ...
Some new features will be added soon (Videostream ... )
  
  
Peter

The first Method has the following outpu:

Output from the control transfer:
-Initial streaming parms: hint=0x0 format=1 frame=1 frameInterval=2000000 keyFrameRate=0 pFrameRate=0 compQuality=0 compWindowSize=0 delay=0 maxVideoFrameSize=0 maxPayloadTransferSize=0
-Probed streaming parms: hint=0x0 format=1 frame=1 frameInterval=2000000 keyFrameRate=0 pFrameRate=0 compQuality=0 compWindowSize=0 delay=0 maxVideoFrameSize=614400 maxPayloadTransferSize=3000
-Final streaming parms: hint=0x0 format=1 frame=1 frameInterval=2000000 keyFrameRate=0 pFrameRate=0 compQuality=0 compWindowSize=0 delay=0 maxVideoFrameSize=614400 maxPayloadTransferSize=3000

The first line are the values you set in the program, to connect the camera. (Initial streaming parms}
The secound line are the values from the camera, which the camera returned from your values.
And in the third line are the new saved and final values from the usb camera.

Outpuf from the first Method: testisochronousstream1:

(sample)
- I/UsbCamTest1: requests=317 packetCnt=317 packetErrorCnt=0 packet0Cnt=5, packet12Cnt=0, packetDataCnt=312 packetHdr8cCnt=123 frameCnt=57
- I/UsbCamTest1: 1/0 len=1280 data=0c 8c 00 00 00 00 9c 1e 4b 4b 31 05 10 80 10 80 10 80 10 80 10 80 10 80 10 80 10 80 10 80 10 80
- I/UsbCamTest1: 2/0 len=1280 data=0c 8c 00 00 00 00 0c d5 66 4b 3f 06 10 80 10 80 10 80 10 80 10 80 10 80 10 80 10 80 10 80 10 80
- I/UsbCamTest1: 13/0 len=304 data=0c 8e 00 00 00 00 1f a3 fd 4b f7 03 10 80 10 80 10 80 10 80 10 80 10 80 10 80 10 80 10 80 10 80 EOF frameLen=10436

The first line shows a summary of the output. Here you see How many frame were collected, how many errors have been detected and you can take a look at the packetsize of the requests.
The secound line is a line from a packet of a URB: 1/0 means first paket in request number 0. --> 2/0 means second package .. The data shows the offsets wich were transmitted.
The third line shows the data of a package and the hint: EOF frameLen=10436.  --> For Example here a frame ends with a length of 10436 wich is not 614400 as we expected from the controltransfer, so you may have to change some values of you program to get a valid frame size.

