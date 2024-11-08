package com.atleastitworks.ejemplo_signals;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;

import static java.lang.Math.sqrt;

public class MainActivity extends AppCompatActivity {

    //---------------------------------------------------------------------------------------
    //-------------------------- Declaraciones Generales ------------------------------------
    //---------------------------------------------------------------------------------------

    // Flags de control de la aplicacion
    private boolean USE_CPP_FFT = false;
    private boolean USE_JAVA_FFT = true;
    private boolean stopped = true;
    // Con este flag avisamos que hay data nueva a la FFT, es un semaforo mal hecho
    boolean buffer_ready = false;

    // Defino los buffers, potencia de 2 para mas placer y por la FFT
    private int POW_FREC_SHOW = 10;
    private int POW_TIME_SHOW = 8;
    private int POW_FFT_BUFFER = 16;

    private int BUFFER_SIZE_SHOW_FREQ = (int) Math.pow(2,POW_FREC_SHOW);
    private int BUFFER_SIZE_SHOW_TIME = (int) Math.pow(2,POW_TIME_SHOW);
    private int BUFFER_SIZE = (int) Math.pow(2,POW_FFT_BUFFER);

    // Contador de tiempo de ejecucion
    private double time_exe = 0.0;

    //---------------------------------------------------------------------------------------
    //-------------------------- NATIVE Cpp -------------------------------------------------
    //---------------------------------------------------------------------------------------
    // Cargo la libreria en C nativo "signal_proces-lib.so" (la que hicimos acá)
    static
    {
        System.loadLibrary("signal_proces-lib");
    }

    // Delcaro las funciones que voy a utilizar de la libreria
    private native String getNativeString(); // Esta funcion esta contenida en la libreria
    private native double[] calcularFFT(short[] input, int elementos);


    //---------------------------------------------------------------------------------------
    //-------------------------- Libreria FFT JAVA ------------------------------------------
    //---------------------------------------------------------------------------------------
    // Creamos la clase para hacer la FFT
    // ver:  https://github.com/wendykierp/JTransforms
    // Para que esto ande debemos poner la ependencia en "build.gradle (Module: app)" :
    // dentro de "dependencies" ponemos:
    // implementation 'com.github.wendykierp:JTransforms:3.1'
    private DoubleFFT_1D fft = new DoubleFFT_1D(BUFFER_SIZE);
    // Este es el buffer de entrada a la FFT, que quiere doubles...
    double[] buffer_double = new double[BUFFER_SIZE];



    //---------------------------------------------------------------------------------------
    //-------------------------- Captura de audio -------------------------------------------
    //---------------------------------------------------------------------------------------
    // Declaramos la clase para grabar audio
    private AudioRecord ar = null;
    private int SAMPLE_RATE = 44100; // en Hz
    // Buffer donde sale el valor crudo del microfono
    short[] buffer = new short[BUFFER_SIZE];


    //---------------------------------------------------------------------------------------
    //-------------------------- Libreria de ploteo de señales ------------------------------
    //---------------------------------------------------------------------------------------
    // Aca abajo van las declaraciones de ploteo....
    // ver: https://github.com/PhilJay/MPAndroidChart
    // Para que esto ande debemos poner la ependencia en "build.gradle (Module: app)" :
    // dentro de "dependencies" ponemos:
    // implementation 'com.github.PhilJay:MPAndroidChart:v2.2.4'

    // Cada cuanto refrescamos el plot, lo calculamos en funcion del tamaño de los buffers
    // y la frecuencia de muestreo
    private int PLOTS_REFRESH_MS;
    // Esta variable la uso para recorrer la salida del microfono
    private int buffer_counter = 0;

    //  Creamos las clases del grafico de tiempo
    private LineChart grafico_tiempo;
    private ArrayList<Entry> LineEntry_tiempo = new ArrayList<>(BUFFER_SIZE_SHOW_TIME);
    private ArrayList<String> labels_tiempo = new ArrayList<>(BUFFER_SIZE_SHOW_TIME);
    LineDataSet dataSet_tiempo;
    LineData data_tiempo;

    // Creamos las clases del grafico de FFT
    private LineChart grafico_frecuencia;
    private ArrayList<Entry> LineEntry_frecuencia = new ArrayList<>(BUFFER_SIZE_SHOW_FREQ);
    private ArrayList<String> labels_frecuencia = new ArrayList<>(BUFFER_SIZE_SHOW_FREQ);
    LineDataSet dataSet_frec;
    LineData data_frec;


    //---------------------------------------------------------------------------------------
    //-------------------------- Permisos de audio ------------------------------------------
    //---------------------------------------------------------------------------------------
    // Estas funciones de aca abajo salen de la documentación de Android, es un metodo
    // que pide permisos de microfono

    // Flag del pedido
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Pedimos permiso para grabar audio RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // Bloqueamos la pantalla siempre en modo retrato
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        final Button boton_cal = findViewById(R.id.boton_act);

        boton_cal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accion_boton_ini_fin();
            }
        });

        final Switch switch_fft = findViewById(R.id.FFT_switch);
        switch_fft.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                Context context = getApplicationContext();
                CharSequence text;

                if (isChecked) {
                    USE_CPP_FFT = true;
                    USE_JAVA_FFT = false;
                    text = "Usando FFT en C nativo.";
                } else {
                    USE_CPP_FFT = false;
                    USE_JAVA_FFT = true;
                    text = "Usando FFT en JAVA.";
                }
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }
        });
        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // Buscamos las implementaciones en el activity_main.xml de los dos graficos
        grafico_tiempo = findViewById(R.id.line_chart_tiempo);
        grafico_frecuencia = findViewById(R.id.line_chart_frecuencia);

        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // Pedimos permiso para grabar audio
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // Testeamos que se cargue bien la libreia nativa, mostrando una tostada...
        Context context = getApplicationContext();
        CharSequence text = getNativeString();
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();

        //------------------------------------------------------------------------------------------
        //-------------------------- INICIALIZO LOS GRAFICOS ---------------------------------------
        //------------------------------------------------------------------------------------------
        // Llenamos los buffers de señal a plotear con nada...
        for(int i=0;i<BUFFER_SIZE_SHOW_TIME;i++)
        {
            LineEntry_tiempo.add(new Entry(0.0f, i));
            labels_tiempo.add(String.valueOf(i));
        }
        for(int i=0;i<BUFFER_SIZE_SHOW_FREQ;i++)
        {
            LineEntry_frecuencia.add(new Entry(0.0f, i));
            labels_frecuencia.add(String.valueOf(i));
        }
        // Cargamos los datos en la clase que grafica
        dataSet_frec = new LineDataSet(LineEntry_frecuencia, "Frecuencia");
        data_frec = new LineData(labels_frecuencia, dataSet_frec);

        dataSet_tiempo = new LineDataSet(LineEntry_tiempo, "Tiempo");
        data_tiempo = new LineData(labels_tiempo, dataSet_tiempo);

        // Calculamos el tiempo de refresco de display para mostrar toda la señal antes que
        // se termine de grabar. No es en tiempo real, por lo que se ve mal a veces...
        PLOTS_REFRESH_MS = (int) ((((float)BUFFER_SIZE/(float)SAMPLE_RATE) / (float) BUFFER_SIZE_SHOW_TIME)*1000.0);

        // Seteamos los datos iniciales en los graficos
        grafico_tiempo.setData(data_tiempo);
        grafico_frecuencia.setData(data_frec);

        // Configuramos los ejes de los graficos (Esto es cosmetico mas que nada)
        XAxis xl = grafico_tiempo.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setSpaceBetweenLabels(5);
        xl.setEnabled(true);

        YAxis leftAxis = grafico_tiempo.getAxisLeft();
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setAxisMaxValue((float) Math.pow(2,10));
        leftAxis.setAxisMinValue((float) -Math.pow(2,10));
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = grafico_tiempo.getAxisRight();
        rightAxis.setEnabled(false);

        grafico_tiempo.setDescription("Señal temporal");

        XAxis xl_f = grafico_frecuencia.getXAxis();
        xl_f.setTextColor(Color.WHITE);
        xl_f.setDrawGridLines(true);
        xl_f.setAvoidFirstLastClipping(true);
        xl_f.setSpaceBetweenLabels(5);
        xl_f.setEnabled(true);

        YAxis leftAxis_f = grafico_frecuencia.getAxisLeft();
        leftAxis_f.setTextColor(Color.BLACK);
        leftAxis_f.setDrawGridLines(true);

        YAxis rightAxis_f = grafico_frecuencia.getAxisRight();
        rightAxis_f.setEnabled(false);

        grafico_frecuencia.setDescription("FFT");
        //------------------------------------------------------------------------------------------
        //-------------------------- FIN INICIALIZACION DE GRAFICOS --------------------------------
        //------------------------------------------------------------------------------------------







        //------------------------------------------------------------------------------------------
        //-------------------------- EJECUTO THREADS DE PROCESO ------------------------------------
        //------------------------------------------------------------------------------------------
        // Como tiene que funcionar en paralelo, necesitamos un par de threads

        //---------------------------------- CALCULO FFT ------------------------------------------
        // Este thread espera que el grabador de audio termine y hace la FFT. Solo mira el flag,
        // si esta en flase vuelve a dormir y si es true hace FFT.
        // La FFT usada va a depender del checkbox, que setea el flag
        new Thread(new Runnable() {

            @Override
            public void run() {
//                for(int i = 0; i < 500; i++) {
                while (true)
                {
                    if(!stopped)
                    {
                        if (USE_JAVA_FFT)
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    calcFFT();
                                }
                            });
                        else if (USE_CPP_FFT)
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {calcFFT_Cpp();
                                }
                            });
                    }

                    // sleep
                    try
                    {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException e)
                    {

                    }
                }
            }
        }).start();
        //------------------------------------------------------------------------------------------


        //---------------------------------- PLOT REFRESH ------------------------------------------
        // Con este thread vamos a refrescar los graficos con la nueva informacion
        new Thread(new Runnable() {

            @Override
            public void run() {

                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);

                while (true) {

                    if(!stopped)
                        runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mostrar_signals();
                        }
                    });

                    try {
                        Thread.sleep(PLOTS_REFRESH_MS);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }).start();
        //------------------------------------------------------------------------------------------


        // Fin...
    }




    // Metodos...


    //----------------------------------------------------------------------------------------------
    //------------------------------ Boton de iniciar / parar --------------------------------------
    //----------------------------------------------------------------------------------------------
    private void accion_boton_ini_fin()
    {
        boolean bail = false;
        final Button testButton = findViewById(R.id.boton_act);
        Switch fft_switch = findViewById(R.id.FFT_switch);

        // Si estaba encendido, solo apago
        if (!stopped)
        {
            stopped = true;
            testButton.setText("Start");
            return;
        }


        // Activo

        // Seteo tipo FFT
        if (fft_switch.isChecked())
        {
            USE_CPP_FFT = true;
            USE_JAVA_FFT = false;
        }
        else
        {
            USE_CPP_FFT = false;
            USE_JAVA_FFT = true;
        }


        // Informo que tipo de FFT voy a usar
        Context context = getApplicationContext();
        CharSequence text;
        if (USE_CPP_FFT)
            text = "Usando FFT en C nativo.";
        else if (USE_JAVA_FFT)
            text = "Usando FFT en JAVA.";
        else
        {
            text = "¿No seleecionaste FFT? ¡¡¿como llegue acá?!!";
            bail = true;
        }

        // Muestro tostada con información
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();



        // Si algo salio mal, no activo.
        if (bail)
            return;


        //---------------------------------- EJECUTO EL THREAD DE AUDIO RECORDER -------------------
        stopped = false;
        // Este thread va a estar siempre grabando audio
        new Thread(new Runnable() {
            @Override
            public void run() {
                getTime();
            }
        }).start();

        testButton.setText("Stop");


        return;

    }

    //----------------------------------------------------------------------------------------------
    //------------------------------ CALC FFT - Cpp ------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Este método hace la FFT, en Cpp
    private void calcFFT_Cpp()
    {
        // Solo si hay nuevos datos en el buffer...
        if (buffer_ready)
        {

            long startTime = System.currentTimeMillis();

            // Calculo FFT en la libreria de C
            buffer_double = calcularFFT(buffer, BUFFER_SIZE);

            time_exe = System.currentTimeMillis() - startTime;
            // Actualizo el tiempo de calculo

            // Actualizo plot
            updateFFT_values();

            // Terminamos de procesar el buffer, reseteamos el flag
            buffer_ready = false;
        }
    }


    //----------------------------------------------------------------------------------------------
    //------------------------------ CALC FFT - JAVA -----------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Este método hace la FFT
    private void calcFFT()
    {
        // Solo si hay nuevos datos en el buffer...
        if (buffer_ready)
        {

            long startTime = System.currentTimeMillis();

            // Pasamos a double como quiere la clase FFT
            for (int i = 0; i < BUFFER_SIZE; i++)
            {
                buffer_double[i] = buffer[i];
            }

            // HAcemos la FFT. La salida va a estar en el mismo buffer. Solo saca la parte
            // real (izquierda) de la FFT, intercalando la salida real y la imaginaria.
            fft.realForward(buffer_double);

            time_exe = System.currentTimeMillis() - startTime;

            // Actualizo plot
            updateFFT_values();

            // Terminamos de procesar el buffer, reseteamos el flag
            buffer_ready = false;
        }
    }

    //----------------------------------------------------------------------------------------------
    //------------------------------ Actualizar grafico FFT ----------------------------------------
    //----------------------------------------------------------------------------------------------
    private void updateFFT_values()
    {
        // obtenemos el modulo y mostramos en el grafico de FFT
        int buffer_mod_count = 0;
        for (int i = 0; i < BUFFER_SIZE_SHOW_FREQ; i++)
        {
            // calculamos el modulo
            double aux_mod = sqrt(buffer_double[buffer_mod_count]*buffer_double[buffer_mod_count] + buffer_double[buffer_mod_count+1]*buffer_double[buffer_mod_count+1]);

            // Adelantamos el index del buffer con un paso grande, submuestreando la salida real
            // asi no colgamos el grafico con muchos puntos.
            buffer_mod_count += 2^(POW_FFT_BUFFER-POW_FREC_SHOW);

            // Borramos el dato
            dataSet_frec.removeFirst();
            // Agregamos un nuevo
            dataSet_frec.addEntry(new Entry((float) aux_mod, i));
        }

        // Actualizamos el dataset
        data_frec.removeDataSet(0);
        data_frec.addDataSet(dataSet_frec);
        grafico_frecuencia.setData(data_frec);

        // Le avisamos que cambio y que lo actualice
        data_frec.notifyDataChanged();
        dataSet_frec.notifyDataSetChanged();
        grafico_frecuencia.invalidate();

        // Actualizo el texto del tiempo de calculo
        final TextView texto_tiempo = findViewById(R.id.text_time);
        texto_tiempo.setText("Tiempo calc. = "+time_exe+" ms");

    }


    //----------------------------------------------------------------------------------------------
    //------------------------------ CAPTURAR AUDIO ------------------------------------------------
    //----------------------------------------------------------------------------------------------
    private void getTime()
    {

        // Seteamos la prioridad
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        AudioRecord recorder = null;

        // intentamos crear el grqabador de audio y grabar...
        try {

            // Creamos el grabador
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE);

            // Empezamos a grabar
            recorder.startRecording();

            // Mientras no me digan que pare...
            while(!stopped) {

                // Leo las muestras de audio
                recorder.read(buffer,0,BUFFER_SIZE);

                // Si llego aca es que hay nueva info, seteo el flag para la FFT
                buffer_ready = true;

                // Reinicio el indice del plot de señal temporal
                buffer_counter = 0;
            }
        } catch(Throwable x) {
            //Log.w(TAG,"Error reading voice audio",x);
        } finally {
            //close();
        }
    }


    //----------------------------------------------------------------------------------------------
    //------------------------------ ACTUALIZAR PLOTS EN PANTALLA ----------------------------------
    //----------------------------------------------------------------------------------------------
    private void mostrar_signals()
    {

        // Obtengo los datos actuales del grafico
        LineData data = grafico_tiempo.getData();

        if (data != null) {
            // Obtengo el dataset actual
            ILineDataSet set = data.getDataSetByIndex(0);

            // Agrego una entrada
            data.addXValue(String.valueOf(data.getXValCount() + 1));
            data.addEntry(new Entry((float) buffer[buffer_counter], set.getEntryCount()), 0);

            // Le avisamos al grefico que cambio el dataset
            grafico_tiempo.notifyDataSetChanged();

            // Limitamos el numero de muestras a mostrar
            grafico_tiempo.setVisibleXRangeMaximum(BUFFER_SIZE_SHOW_TIME);

            // nos movemos al final
            grafico_tiempo.moveViewToX(data.getXValCount() - BUFFER_SIZE_SHOW_TIME + 1);

            // Actualizamos el indice de ploteo de buffer
            buffer_counter++;

        }

    }



}
