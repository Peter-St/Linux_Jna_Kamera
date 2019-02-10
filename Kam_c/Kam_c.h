/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */



/* 
 * File:   Kam_c.h
 * Author: peter
 *
 * Created on 10. Februar 2019, 05:39
 */

#include <jni.h>

#ifndef KAM_C_H
#define KAM_C_H

#ifdef __cplusplus
extern "C" {
#endif

    
    
    JNIEXPORT void JNICALL Java_humer_kamera_Kam_usbIsoLinux
        (JNIEnv *, jobject);
    
    JNIEXPORT jint JNICALL Java_humer_kamera_Kam_kameraSchliessen
    (JNIEnv *, jobject );



#ifdef __cplusplus
}
#endif

#endif /* KAM_C_H */

