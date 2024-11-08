package com.example.kotlin_acceelerometer_example

import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast


class MainActivity : AppCompatActivity(), SensorEventListener {

    var mLastX: kotlin.Double = 0.0
    var mLastY: kotlin.Double = 0.0
    var mLastZ: kotlin.Double = 0.0

    private var mInitialized = false
    private lateinit var mSensorManager: SensorManager
    private lateinit var mAccelerometer: Sensor


    // variables del Exponential Moving Average
    private val alpha_decay = 0.25
    private var ema_x = 0.0
    private var ema_y = 0.0
    private var ema_z = 0.0
    private var ema_count_x = 0.0
    private var ema_count_y = 0.0
    private var ema_count_z = 0.0

    // Calibracion
    private var CALIBRATION_FLAG = false
    private var NOISE_X = 0.02
    private var NOISE_Y = 0.02
    private var NOISE_Z = 0.02
    private var calib_count = 0
    private val CALIB_SAMPLES = 100
    private val CALIB_CONSTANT = 1.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mInitialized = false
        CALIBRATION_FLAG = false


        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Boton de calibracion
        val boton_cal = findViewById<Button>(R.id.cal_button)
        boton_cal.setOnClickListener { calibrar_ruido() }

    }


    private fun calibrar_ruido() {
        val context = applicationContext
        val text: CharSequence = "Calibrando, no mueva el dispositivo."
        val duration = Toast.LENGTH_LONG
        val toast = Toast.makeText(context, text, duration)
        toast.show()
        CALIBRATION_FLAG = true
    }


    override fun onSensorChanged(event: SensorEvent) {

        // Kotlin es null-safe, java no (https://kotlinlang.org/docs/null-safety.html)
        // Es necesario checkear por la nulidad de la variable o handlear el caso de nulidad en cada
        // variable que dependa de esta. Es mas manejar aca el problema de nulidad y despues
        // seguir sin necesidad de agregar mas codigo

        val tvX = findViewById<TextView>(R.id.x_axis)
        val tvX_ang = findViewById<TextView>(R.id.ang_x)
        val tvY = findViewById<TextView>(R.id.y_axis)
        val tvY_ang = findViewById<TextView>(R.id.ang_y)
        val tvZ = findViewById<TextView>(R.id.z_axis)
        val tvZ_ang = findViewById<TextView>(R.id.ang_z)

        val mod_g_text = findViewById<TextView>(R.id.modulo_g_text)


        val iv = findViewById<ImageView>(R.id.image)


        val x: Float = event.values[0]
        val y: Float = event.values[1]
        val z: Float = event.values[2]

        if (!mInitialized) {
            mLastX = x.toDouble()
            ema_x = 0.0
            mLastY = y.toDouble()
            ema_y = 0.0
            mLastZ = z.toDouble()
            ema_z = 0.0

            ema_count_x = 0.0
            ema_count_y = 0.0
            ema_count_z = 0.0

            tvX.text = "0.0"
            tvX_ang.text = "0.0"
            tvY.text = "0.0"
            tvY_ang.text = "0.0"
            tvZ.text = "0.0"
            tvZ_ang.text = "0.0"
            mod_g_text.text = "El modulo de aceleracion actual es de... 0.0 m/seg^2"
            mInitialized = true
        }

        if ( mInitialized && !CALIBRATION_FLAG)
        {
            // Actualizo el moving average exponencial,
            // siempre que detectemos un cambio mas alla del piso de ruido (empirico)
            if (Math.abs(mLastX - x) > NOISE_X) {
                ema_x = x + (1 - alpha_decay) * ema_x
                ema_count_x = 1 + (1 - alpha_decay) * ema_count_x
            }
            mLastX = x.toDouble()
            if (Math.abs(mLastY - y) > NOISE_Y) {
                ema_y = y + (1 - alpha_decay) * ema_y
                ema_count_y = 1 + (1 - alpha_decay) * ema_count_y
            }
            mLastY = y.toDouble()
            if (Math.abs(mLastZ - z) > NOISE_Z) {
                ema_z = z + (1 - alpha_decay) * ema_z
                ema_count_z = 1 + (1 - alpha_decay) * ema_count_z
            }
            mLastZ = z.toDouble()


            // Asigno el nuevo valor


            // Asigno el nuevo valor
            val use_x = ema_x / ema_count_x
            val use_y = ema_y / ema_count_y
            val use_z = ema_z / ema_count_z


            tvX.setText(String.format("%.4f", use_x))

            tvY.setText(String.format("%.4f", use_y))

            tvZ.setText(String.format("%.4f", use_z))


            if (Math.abs(use_x) > Math.abs(use_y)) {
                iv.setImageResource(R.drawable.shaker_fig_1)
            } else if (Math.abs(use_y) > Math.abs(use_x)) {
                iv.setImageResource(R.drawable.shaker_fig_2)
            }
            /*
            else {iv.setVisibility(View.INVISIBLE);}
             */

            // Calculo el modulo
            /*
            else {iv.setVisibility(View.INVISIBLE);}
             */

            // Calculo el modulo
            val mod_g_act: Double =
                Math.sqrt(Math.pow(use_x, 2.0) + Math.pow(use_y, 2.0) + Math.pow(use_z, 2.0))
            mod_g_text.text =
                "El modulo de aceleracion actual es de... " + String.format(
                    "%.2f",
                    mod_g_act
                ) + " m/seg^2"


            // Calculo el angulo
            val g = 9.8 // m/seg^2

            var ang_med_x = 0.0
            var ang_med_y = 0.0
            var ang_med_z = 0.0

            ang_med_x = Math.round(Math.asin(use_x / g) * 180.0 / Math.PI).toDouble()
            ang_med_y = Math.round(Math.asin(use_y / g) * 180.0 / Math.PI).toDouble()
            ang_med_z = Math.round(Math.asin(use_z / g) * 180.0 / Math.PI).toDouble()

            tvX_ang.setText(String.format("%.2f", ang_med_x))

            tvY_ang.setText(String.format("%.2f", ang_med_y))

            tvZ_ang.setText(String.format("%.2f", ang_med_z))
        }

        // Modo calibracion
        if (CALIBRATION_FLAG) {
            if (calib_count == 0) {
                // Reinicio los promedios
                ema_x = Math.abs(mLastX - x)
                ema_y = Math.abs(mLastY - y)
                ema_z = Math.abs(mLastZ - z)
                mLastX = x.toDouble()
                mLastY = y.toDouble()
                mLastZ = z.toDouble()
            } else {
                // Ahora hago un promedio normal, ya que el
                // dispositivo se encuentra siempre en el mismo estado
                // durante la calibracion (no hay que moverlo)
                ema_x = (calib_count * ema_x + Math.abs(mLastX - x)) / (calib_count + 1)
                ema_y = (calib_count * ema_x + Math.abs(mLastY - y)) / (calib_count + 1)
                ema_z = (calib_count * ema_x + Math.abs(mLastZ - z)) / (calib_count + 1)
                mLastX = x.toDouble()
                mLastY = y.toDouble()
                mLastZ = z.toDouble()

                // Esto se llama "running moving average (RMA)"
                // Tiene esa forma ya que nuestro N (calib_count) empieza
                // en 0 en lugar de 1, como la expresion matematica.
            }
            if (calib_count > CALIB_SAMPLES) {
                // asigno los nuevos valores de ruido
                NOISE_X = ema_x * CALIB_CONSTANT
                NOISE_Y = ema_y * CALIB_CONSTANT
                NOISE_Z = ema_z * CALIB_CONSTANT
                // Reseteo el contador
                calib_count = 0
                // Salgo del estado calibracion
                CALIBRATION_FLAG = false
                // Me pongo en modo no inicializado (asi reinicio el EMA)
                mInitialized = false
                val context = applicationContext
                val text: CharSequence = "Ruido: X = " + String.format(
                    "%.4f",
                    NOISE_X
                ) + "Ruido: Y = " + String.format(
                    "%.4f",
                    NOISE_Y
                ) + "Ruido: Z = " + String.format("%.4f", NOISE_Z)
                val duration = Toast.LENGTH_LONG
                val toast = Toast.makeText(context, text, duration)
                toast.show()
            }
            calib_count++
        }

    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // TODO("Not yet implemented")
    }


    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
    }
}
