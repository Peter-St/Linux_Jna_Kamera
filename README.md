Linux_Jna_Kamera
----------------

Camera stream over Jna.

Still Under Development !


License
-------

    Copyright 2019 Peter Stoiber

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.




Explaination: (To know how the program works) (You can make all the main changes in the file: Kam.java)

1)  Read out the camera specifications with the terminal command:
  
- list with `lsusb` to get all connected devices
- Detail informations can be received with Vendor+Produkt ID: `lsusb -v -d <vendor>:<product>`
- Variant over: `lsusb -v -s <bus>:<device>`


2) Set the camera specifications: 

Adjust the settings in Kam.java, headed by "REQUIRED CONFIGURATION". Use the
values gathered from lsusb.

3) This step is, when you press in the menu "Isoread" the button "IsoRead1"

Take a look at the camera frames you receive with your settings on STDOUT. To
know how big be a Frame should be, you can look at the output of the controll
transfer of the camera in the log: maxVideoFrameSize, This value is returned
from the camera and should be the valid frame size (The value is calculated by
`Imagewidth x Imagehight x 2`).

The IsochronousRead1 class shows you how the frames are structered
by the camera. Different camerasetting == Different Frame structers. Try it
out with different setting and look at the output. The eof hint shows the
framesize in the log. For valid camera settings the size should be the same as
maxFrameSize value of the controlltransfer. You can use the search function in
the log ...

The stream data will also be dumped to the file: `target/test.dump`


Depending on the configured paramters the file can be player with vlc (parameters depend on camera settings):


```shell

vlc --demux rawvideo --rawvid-fps 15 --rawvid-width 640 --rawvid-height 480 --rawvid-chroma YUYV test.dump

```

4) To Display the Frames directly in an JPanel you can press the Kamera Button:

-  Note: 2 Camera formats are supported now: uncompressed YUY2 and MJPEG
-  you have to do the setting for the camera in the menu "AutoFind / Edit / Open / Save".
-  To search the camera automatically, you can press the button: "Automatic search a camera" (it uses usb4Java)
-  To edit the camera setting use the menu: "Edit / Save" --> you can save the settings to a file and restore them later under the menupoint "Restore Camera Settings".
-  To add additional formats for your camera, you can make changes to the "ConvertStream.java" file. Here it is possible to add more methods for your camera format. JavaCV is used to encode the Frame from uncompressed to Jpg. Check out the convertions on the JavaCV HP or search in the net for examples. JavaCV is close to OpenCV.
---------------------------------


Program output of the Isoread1 Button:

Output from the control transfer:
- Initial streaming parms: hint=0x0 format=1 frame=1 frameInterval=2000000 keyFrameRate=0 pFrameRate=0 compQuality=0 compWindowSize=0 delay=0 maxVideoFrameSize=0 maxPayloadTransferSize=0
- Probed streaming parms: hint=0x0 format=1 frame=1 frameInterval=2000000 keyFrameRate=0 pFrameRate=0 compQuality=0 compWindowSize=0 delay=0 maxVideoFrameSize=614400 maxPayloadTransferSize=3000
- Final streaming parms: hint=0x0 format=1 frame=1 frameInterval=2000000 keyFrameRate=0 pFrameRate=0 compQuality=0 compWindowSize=0 delay=0 maxVideoFrameSize=614400 maxPayloadTransferSize=3000

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
