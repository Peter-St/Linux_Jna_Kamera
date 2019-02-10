/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

#include <jni.h>
#include "Kam_c.h"
#include <libusb-1.0/libusb.h>
#include <errno.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>
#include <usb.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>




///////// Kamera Vendor und Product ID /////////////

int VENDOR_ID   = 0x05c8;
int PRODUCT_ID  = 0x0233;
char bus[] = "1";
char device[] = "3";
////////  Kamera Variablen ////////////////////
    
    int camStreamingInterfaceNum = 1;
    int camControlInterfaceNum = 0;
    int endpunktadresse = 0x81;
    
    int camFormatIndex = 1;                       // bFormatIndex: 1 = uncompressed
    int camFrameIndex = 5;                        // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
    
    int camFrameInterval = 666666;                 // 333333 YUV = 30 fps // 666666 YUV = 15 fps
    uint8_t bConfigurationValue = 1;
    
    int camStreamingAltSetting = 7;              // 7 = 3*1024 bytes packet size // 6 = 3*896 // 5 = 2*1024 // 4 = 2*768 // 3 = 1x 1024 // 2 = 1x 512 // 1 = 128 // 
#define maxPacketSize 3072
    int packetsPerRequest =  8;
#define  ANZAHL_URBS 16

    
#define imageWidth 640
#define imageHeight 480



// #define USBDEVFS_REAPURBNDELAY _IOW('U', 13, void *)
// USB codes:
	// Request types (bmRequestType):

                    #define  RT_STANDARD_INTERFACE_SET = 0x01
                    #define  RT_CLASS_INTERFACE_SET     0x21
                    #define  RT_CLASS_INTERFACE_GET     0xA1
	// Video interface subclass codes:
		    #define  SC_VIDEOCONTROL            0x01
		    #define  SC_VIDEOSTREAMING          0x02
                    #define CLASS_VIDEO                 0x14
		    // Standard request codes:
		    #define  SET_INTERFACE              0x0b
		    // Video class-specific request codes:
		    #define  SET_CUR                     0x01
		    #define  GET_CUR                     0x81
		    // VideoControl interface control selectors (CS):
		    #define  VC_REQUEST_ERROR_CODE_CONTROL  0x02
		    // VideoStreaming interface control selectors (CS):
		    #define  VS_PROBE_CONTROL              0x01
		    #define  VS_COMMIT_CONTROL             0x02
		    #define  VS_STILL_PROBE_CONTROL        0x03
		    #define  VS_STILL_COMMIT_CONTROL       0x04
		    #define  VS_STREAM_ERROR_CODE_CONTROL  0x06
		    #define  VS_STILL_IMAGE_TRIGGER_CONTROL  0x05

    
#define UVC_STREAM_EOH	(1 << 7)
#define UVC_STREAM_ERR	(1 << 6)
#define UVC_STREAM_STI	(1 << 5)
#define UVC_STREAM_RES	(1 << 4)
#define UVC_STREAM_SCR	(1 << 3)
#define UVC_STREAM_PTS	(1 << 2)
#define UVC_STREAM_EOF	(1 << 1)
#define UVC_STREAM_FID	(1 << 0)

    
    ////// Java Methods  //////////////

jmethodID javaVideoParameter;
    jmethodID javaUsbJniReap;
    jmethodID javaRequestCntReap;
    jmethodID javareapcomplete ;
    jmethodID javaEinstellungDerKamera;
    jmethodID javaPackageCntReap;
    jmethodID javaReapreset;
    jmethodID javaUsbJniReapStream;
    jmethodID javaDateiHandlungSetzen;
    jclass class;
    JNIEnv *envg;
    jobject objg;

    struct usbdevfs_ctrltransfer ctrl;
    
char *busnummer;
char *geraetenummer;

int camStreamingInterfaceNum, camControlInterfaceNum, endpunktadresse, ausgewaehlteKamera;

int fd, i, j, result = -1;
int ep_video = 0X81;//mpEndpoint->GetEndpointIndex();            (Foxlink) 
int usedStreamingParmsLen;


JNIEXPORT void JNICALL Java_humer_kamera_Kam_usbIsoLinux
  (JNIEnv *env, jobject obj) {
    
    int r, i, ret;
    
    // Erstellen der Geräteaddresse
    
    busnummer = &bus;
    geraetenummer = &device;
    char *adresse;
    adresse = malloc(24);
    strcpy(adresse, "/dev/bus/usb/00");
    char *adresse2;
    adresse2 = malloc(4);
    strcpy(adresse2, "/00");
    strcat(adresse, busnummer);
    strcat(adresse, adresse2);
    strcat(adresse, geraetenummer);
    printf("Die Kameraadresse lautet: %s\n", adresse); 
    
    //fd =  open(adresse , O_RDWR, 0666);
    fd =  open(adresse , O_RDWR);
    
    free(adresse);
    //free(busnummer);
    free(adresse2);
    //free(geraetenummer);
        
    printf("\nDateibeschreiber nativ ermittelt.\nEr hat folgenden Wert = %d\n", fd);
    fflush(stdout);
    
    struct usbdevfs_ioctl command;
    struct usbdevfs_getdriver getdrv;
    memset(&getdrv, 0, sizeof(getdrv));
    for(i=0; i<2; i++) {
        //printf(" Interface:  %d\n ", i);
        getdrv.interface = i;
        ret = ioctl(fd, USBDEVFS_GETDRIVER, &getdrv);
        if (ret < 0)  printf("Kein Treiber am Interface %d.\n", i);
        else { printf(" Treiber von folgenden Interface %d erhalten. Rückmeldung = %d\n", i, ret);
            command.ifno = i;
            command.ioctl_code = USBDEVFS_DISCONNECT;
            command.data = NULL;
            ret = ioctl(fd, USBDEVFS_IOCTL, &command);
            if (ret < 0)  printf(" Aushängen des Treibers fehlgeschlagen von folgendem Interface:  %d\n %d %d\n", i, ret, errno);
            else printf(" Aushängen des Treibers am Interface:  %d erfolgreich. Rückmeldung: %d\n", i, ret);
        }
        ret = ioctl(fd, USBDEVFS_CLAIMINTERFACE, &i);
        if (ret < 0)   printf("Einhängen von Interface fehlgeschlagen %d\n %d %d\n", i, ret, errno);       
        else  printf("Interface %d eingehängt\n", i); 
    }
    fflush(stdout);
     printf("vor den Klassen\n"); 
    fflush(stdout);
    class = (*env)->GetObjectClass(env, obj);
    javaEinstellungDerKamera = (*env)->GetMethodID(env, class, "einstellungDerKamera", "(IIIIIIII)V");
    javaVideoParameter = (*env)->GetMethodID(env, class, "videoParameter", "([B)V");
    javaDateiHandlungSetzen = (*env)->GetMethodID(env, class, "dateiHandlungSetzen", "(I)V");
 
    (*env)->CallVoidMethod(env, obj, javaDateiHandlungSetzen, fd);
    
    
    interfaceSetztenGeraetetreiber(camStreamingInterfaceNum, 0);
    
    ioctlControltransfer(env ,obj, javaVideoParameter);
    
    interfaceSetztenGeraetetreiber(camStreamingInterfaceNum, camStreamingAltSetting);
    
    
    switch(camFrameIndex) {
            case 1: (*env)->CallVoidMethod(env, obj, javaEinstellungDerKamera, camStreamingAltSetting, (jint *)  maxPacketSize, (jint *)packetsPerRequest,
               (jint *) ANZAHL_URBS, (jint *) camFormatIndex, (jint *) camFrameIndex, 640, 360); break;
            case 5: (*env)->CallVoidMethod(env, obj, javaEinstellungDerKamera, camStreamingAltSetting, (jint *)  maxPacketSize, (jint *)packetsPerRequest,
               (jint *) ANZAHL_URBS, (jint *) camFormatIndex, (jint *) camFrameIndex, 640, 480); break;
            //case 6: cv::imencode(".jpg", rgbBild, videoframeRGB, param); break;
    }
        
    envg = env;
    objg = obj;
    /////////////////////////////////////Vorbereitung der Spoeicherung in eine Bin Datei //////////////////////////////////
    FILE *pFile;
    pFile = fopen("/home/peter/Foxlink/data2.bin", "w");
    if(pFile != NULL)
    {
        fwrite(NULL, 0, 0, pFile);
        fclose(pFile);
    }
    //dateiBeschreiber = open("/home/peter/Foxlink/data2.bin",O_RDWR);
    ///////////////////////////////////// Ende Vorbereitung der Spoeicherung in eine Bin Datei //////////////////////////////////
  

}





JNIEXPORT jint JNICALL Java_humer_kamera_Kam_kameraSchliessen
  (JNIEnv *env, jclass obj) {
    int ret;
    printf("Übertragung beendet\n"); 
    interfaceSetztenGeraetetreiber(camStreamingInterfaceNum, 0);
    for (int if_num = 0; if_num < (camStreamingInterfaceNum + 1); if_num++) {
        ret = ioctl(fd, USBDEVFS_RELEASEINTERFACE, &if_num);
        if (ret < 0) {  printf("Aushkängen vom Interface %d fehlgeschlagen: %d, Der Error lautet: %d\n",if_num, ret, errno);
        } else { printf("Interface %d  erfolgreich ausgehängt. Die Rückmeldung lautet: %d\n",if_num , ret); }
    }
        
    ret = close (fd);
    if (ret < 0) {
        printf("UsbDateibeschreiber konnte nicht geschlossen werden%d, Der Error lautet: %d\n", ret, errno);
    } else { printf("UsbDateibeschreiber erfolgreich geschlossen: %d\n" , ret); }
    fflush(stdout);
    (*env)->CallVoidMethod(env, obj, javareapcomplete);
    fflush(stdout);
  }


void interfaceSetztenGeraetetreiber (int interface, int altsetting){
    struct usbdevfs_setinterface setif;
    setif.altsetting = altsetting;
    setif.interface = interface;
    int ret = ioctl(fd, USBDEVFS_SETINTERFACE, &setif);
    if (ret == 0)  printf("Interface %d erfolgreich auf das Altsetting %d gesetzt. Die Rückmeldung lautet: %d.\n", camStreamingInterfaceNum, setif.altsetting,ret );
    else  printf(" WARNUNG: Interface Alteinstellungen nicht gesetzt. Rückmeldung: %d, Fehlermeldung: %d\n", ret, errno);
    fflush(stdout);
}


void ioctlControltransfer (JNIEnv *env, jobject obj, jmethodID javaVideoParameter){
    
    
    jboolean isCopy;
    int	retval, len;
    uint8_t buffer[26];
    for (i=0; i<26; i++) {  buffer[i] = 0x00; }
    //buffer[0] = bConfigurationValue; // what fields shall be kept fixed (0x01: dwFrameInterval)
    buffer[0] = 0x01;          // what fields shall be kept fixed (0x01: dwFrameInterval)
    buffer[1] = 0x00;          // 
    buffer[2] = camFormatIndex;          // video format index
    buffer[3] = camFrameIndex;          // video frame index // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
    //char hexdata[4];
    //printf("hexValue = %x", camFrameInterval);
    buffer[4] = camFrameInterval & 0xFF;
    buffer[5] = (camFrameInterval>>8) & 0xFF;
    buffer[6] = (camFrameInterval>>16) & 0xFF;
    buffer[7] = (camFrameInterval>>24) & 0xFF;
    
    ctrl.wValue = (VS_PROBE_CONTROL << 8);
    ctrl.wIndex = camStreamingInterfaceNum;
    ctrl.wLength = sizeof(buffer);
    ctrl.timeout = 2000;	// USB should t/o after 5 seconds.
    ctrl.data = &buffer;
    
    printf("Gewünschte Videoparameter:           ");
        for (int i = 0; i < sizeof(buffer); i++)
            if (buffer[i] != 0){
            printf("[%d ] ", buffer[i]);}
    printf("\n");
    fflush(stdout);
    
    jbyteArray array = (*env)->NewByteArray(env, 26);
    // creat bytes from byteUrl
    jbyte *bytes = (*env)->GetByteArrayElements(env, array, 0);
    int a;
    for (a = 0; a < 26; a++) { bytes[a] = buffer[a]; }
    // move from the temp structure to the java structure
    (*env)->SetByteArrayRegion(env, array, 0, 26, bytes);
    (*env)->CallVoidMethod(env, obj, javaVideoParameter, array);
    //(*env)->ReleaseByteArrayElements(env, array, bytes, 0);
    
    ctrl.bRequestType = RT_CLASS_INTERFACE_SET;
    ctrl.bRequest = SET_CUR;
    
    len = ioctl (fd, USBDEVFS_CONTROL, &ctrl);
    
    if (len !=  sizeof(buffer)) printf("Camera initialization failed. Streaming parms probe set failed, len= %d. Fehlermeludng: %d, Error = %s\n", len, errno, strerror(errno));
    else { printf("Camera initialization success, len= %d.\n", len);      }
    printf("\n");
    fflush(stdout);
    
    ctrl.bRequestType = RT_CLASS_INTERFACE_GET;
    ctrl.bRequest = GET_CUR;
    len = ioctl (fd, USBDEVFS_CONTROL, &ctrl);
    if (len != sizeof(buffer)) {
        printf("Camera initialization failed. Streaming parms probe set failed, len= %d.\n", len);
    }
    /*printf("Sondierte Videoflussparameter:       ");
    for (int i = 0; i < sizeof(buffer); i++)
        if (buffer[i] != 0){
            printf("[%d ] ", buffer[i]);}*/
    
    array = (*env)->NewByteArray(env, 26);
    // creat bytes from byteUrl
    *bytes = (*env)->GetByteArrayElements(env, array, 0);
    for (a = 0; a < 26; a++) {
        bytes[a] = buffer[a];
    }
    // move from the temp structure to the java structure
    (*env)->SetByteArrayRegion(env, array, 0, 26, bytes);
    (*env)->CallVoidMethod(env, obj, javaVideoParameter, array);
    //(*env)->ReleaseByteArrayElements(env, array, bytes, 0);
                
    
    ctrl.bRequest = SET_CUR;
    ctrl.bRequestType = RT_CLASS_INTERFACE_SET;
    ctrl.wValue = (VS_COMMIT_CONTROL << 8);
    len = ioctl (fd, USBDEVFS_CONTROL, &ctrl);
    if (len != sizeof(buffer)) { printf("Camera initialization failed. Streaming parms commit set failed, len= %d.", len); }
    else printf("Kontrolltransfer erfolgreich. len= %d.\n", len);
    ctrl.bRequest = GET_CUR;
    ctrl.bRequestType = RT_CLASS_INTERFACE_GET;
    ctrl.wValue = (VS_COMMIT_CONTROL << 8);
    len = ioctl (fd, USBDEVFS_CONTROL, &ctrl);
    if (len != sizeof(buffer)) {
           printf("Camera initialization failed. Streaming parms commit get failed, len= %d.", len);
        }
        
    /*printf("\nAbschließende Videoflussparameter:   ");
    for (int i = 0; i < sizeof(buffer); i++)
        if (buffer[i] != 0){
        printf("[%d ] ", buffer[i]);}
    printf("\n");*/
    
    
    array = (*env)->NewByteArray(env, 26);
    // creat bytes from byteUrl
    *bytes = (*env)->GetByteArrayElements(env, array, 0);
    for (a = 0; a < 26; a++) {
        bytes[a] = buffer[a];
    }
    // move from the temp structure to the java structure
    (*env)->SetByteArrayRegion(env, array, 0, 26, bytes);
    (*env)->CallVoidMethod(env, obj, javaVideoParameter, array);
    (*env)->ReleaseByteArrayElements(env, array, bytes, 0);
                
    fflush(stdout);
}



