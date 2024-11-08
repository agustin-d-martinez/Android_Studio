package com.example.app_acelerometro

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private lateinit var pelota: ImageView
    private lateinit var textoX: TextView
    private lateinit var textoY: TextView

    private var accX = 0.0f
    private var accY = 0.0f
    private var velX = 0.0f
    private var velY = 0.0f
    private var posX = 0.0f
    private var posY = 0.0f
    private var friccion = 0.988f


    private var lastTime: Long =  0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        pelota = findViewById(R.id.bola)
        textoX = findViewById(R.id.text_x)
        textoY = findViewById(R.id.text_y)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        //--POSICION INICIAL-----------
        posX = pelota.x
        posY = pelota.y
        lastTime = System.currentTimeMillis()
    }

    override fun onSensorChanged(event: SensorEvent) {
        var currentTime: Long = 0
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            currentTime = System.currentTimeMillis()
        }
        val dTime: Float = if (lastTime != 0L) (currentTime - lastTime) / 1000f else 0f

        lastTime = currentTime
        accX = event.values[0]
        accY = event.values[1]

        velX -= accX * dTime
        velY += accY * dTime

        velX *= friccion
        velY *= friccion

        posX += velX * dTime * 200
        posY += velY * dTime * 200

        // --------PARA QUE NO SE VAYA DE LA PANTALLA-----------------------
        val maxX = resources.displayMetrics.widthPixels - pelota.width
        val maxY = resources.displayMetrics.heightPixels - pelota.height

        if (posX <= 0f || posX >= maxX ){
            velX = - velX
        }
        if (posY <= 0f || posY >= maxY ){
            velY = - velY
        }
        posX = posX.coerceIn(0f, maxX.toFloat())
        posY = posY.coerceIn(0f, maxY.toFloat())
        // -----------------------------------------------------------------
        textoX.text = String.format("%.4f", accX)
        textoY.text = String.format("%.4f", accY)
        textoX.setTextColor(Color.RED)
        pelota.x = posX
        pelota.y = posY
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onResume() {
        super.onResume()
        acelerometro?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}