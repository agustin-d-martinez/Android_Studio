//
// Created by rawthil on 25/10/19.
//


#include "signal_proces_lib.hpp"
#include "fft.h"
#include <jni.h>

#include <iostream>    // C++ standard IO header
#include <math.h>

using namespace std;

inline void FFT(short int dir,long m,double *x,double *y);

extern "C"
JNIEXPORT jstring JNICALL Java_com_atleastitworks_ejemplo_1signals_MainActivity_getNativeString(
        JNIEnv *env, jobject obj) {

    cout << "Libreria en C++ nativo funcionando!" << endl;

    return env->NewStringUTF("Libreria en C++ nativo funcionando!");
}



extern "C"
JNIEXPORT jdoubleArray JNICALL Java_com_atleastitworks_ejemplo_1signals_MainActivity_calcularFFT(
        JNIEnv *env, jobject obj, jshortArray entrada, jint elementos) {

    //First get a pointer to the elements within the jshortArray
    jsize len = env->GetArrayLength(entrada);
    jboolean isCopy1;
    jshort *entrada_short = env->GetShortArrayElements(entrada, &isCopy1);



    // Entrada en double
    double* val_real=NULL;
    val_real = new double[elementos];
    // array de componente imaginario inicializado en cero
    double* val_img;
    val_img = new double[elementos]();
    //  Paso la entrada a double
    for (int i = 0 ; i < elementos ; i++)
        val_real[i] = (double) entrada_short[i];


    auto startTime = std::chrono::high_resolution_clock::now();

    // Calculo la FFT
    Fft_transform(val_real, val_img, elementos);

    auto endTime = std::chrono::high_resolution_clock::now();
    // Calculate the duration
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(endTime - startTime);

    // Convert the duration to a double in seconds
    double seconds = duration.count() / 1e6;

    // Print the elapsed time
    std::cout << "Time elapsed: " << seconds << " seconds" << std::endl;


    // Declaro la salida
    double* out_fft=NULL;
    out_fft = new double[elementos];
    // Paso los numeros al formato esperado: [real_0, Img_0, Real_1, Img_1, ... , Real_n/2, Img_n/2]
    // (solo la parte real)
    int out_count = 0;
    for (int i = 0; i < (elementos/2); i++)
    {
        out_fft[out_count] = val_real[i];
        out_count++;
        out_fft[out_count] = val_img[i];
        out_count++;
    }




    jdoubleArray output = env->NewDoubleArray( elementos );
    env->SetDoubleArrayRegion( output, 0, elementos, &out_fft[0] );


    return output;
}
