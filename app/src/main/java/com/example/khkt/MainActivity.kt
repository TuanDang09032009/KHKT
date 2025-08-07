package com.example.khkt

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.content.Intent
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.khkt.ui.theme.KHKTTheme
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import androidx.compose.ui.tooling.data.position
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.google.android.gms.maps.model.Marker
import java.util.Random

class BeginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_begin)

        val buttonToMain: Button = findViewById<Button>(R.id.button_to_main)

        buttonToMain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var personMarker: Marker? = null
    private var currentPersonLocation: LatLng? = null

    private val MAX_VISIBLE_ZOOM_FOR_PERSON_MARKER = 7f
    private val DEFAULT_ZOOM_FOR_FOLLOWING = 17f

    private val locationUpdateHandler = Handler(Looper.getMainLooper())
    private lateinit var locationUpdateRunnable: Runnable
    private val UPDATE_INTERVAL_MS: Long = 2000
    private val random = Random()
    private var isFollowingCharacter = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val buttonToHeart: Button = findViewById<Button>(R.id.btnToHeart)
        val buttonToNotification: Button = findViewById<Button>(R.id.btnToNotification)
        val buttonToSOS: Button = findViewById<Button>(R.id.btnTOSOS)
        val buttonToLocation: Button = findViewById<Button>(R.id.btnToLocation)

        buttonToHeart.setOnClickListener {
            val intent = Intent(this, Heartactivity::class.java)
            startActivity(intent)
        }

        buttonToNotification.setOnClickListener {
            val intent = Intent(this, Notificationactivity::class.java)
            startActivity(intent)
        }

        buttonToSOS.setOnClickListener {
            val intent = Intent(this, Sosactivity::class.java)
            startActivity(intent)
        }

        buttonToLocation.setOnClickListener {
            val intent = Intent(this, Locationactivity::class.java)
            startActivity(intent)
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment_container) as SupportMapFragment?
        if (mapFragment == null) {
            Log.e("MainActivity", "Map fragment is null")
            return
        }
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Log.d("MainActivity", "Google Map is ready.")

        // Tính toán kích thước icon ban đầu dựa trên zoom khởi tạo
        currentIconTargetDp =
            getIconTargetDpForZoom(DEFAULT_ZOOM_FOR_FOLLOWING) // Đổi tên hàm cho rõ nghĩa
        Log.d(
            "MainActivity",
            "Initial icon DP size set to: $currentIconTargetDp for zoom: $DEFAULT_ZOOM_FOR_FOLLOWING"
        )

        val initialLocation = LatLng(10.762622, 106.660172)
        updateCharacterLocationAndCamera(initialLocation, DEFAULT_ZOOM_FOR_FOLLOWING, true)

        mMap.setOnCameraIdleListener {
            val currentZoom = mMap.cameraPosition.zoom
            Log.d("MainActivity", "Camera idle. Current zoom: $currentZoom")

            personMarker?.isVisible = currentZoom >= MAX_VISIBLE_ZOOM_FOR_PERSON_MARKER

            val newTargetIconDp = getIconTargetDpForZoom(currentZoom)

            if (newTargetIconDp != currentIconTargetDp && personMarker != null) {
                Log.i(
                    "MainActivity",
                    "Zoom changed sufficiently. Updating icon size from $currentIconTargetDp dp to $newTargetIconDp dp for zoom $currentZoom"
                )
                currentIconTargetDp = newTargetIconDp // Cập nhật kích thước hiện tại

                val newIcon = bitmapDescriptorFromVector(
                    R.drawable.ic_person_pin,
                    currentIconTargetDp,
                    currentIconTargetDp
                )

                if (newIcon != null) {
                    personMarker?.setIcon(newIcon)
                    Log.d(
                        "MainActivity",
                        "Person marker icon updated to size ${currentIconTargetDp}dp."
                    )
                } else {
                    Log.e(
                        "MainActivity",
                        "Failed to create new icon for zoom $currentZoom and size ${currentIconTargetDp}dp"
                    )
                }
            } else if (personMarker == null) {
                Log.w(
                    "MainActivity",
                    "Camera idle but personMarker is null. Cannot update icon size."
                )
            }
        }

        setupContinuousLocationUpdates()
        startLocationUpdates()
    }

    private fun updateCharacterLocationAndCamera(
        newLocation: LatLng,
        targetZoom: Float,
        forceMoveCamera: Boolean = false
    ) {
        currentPersonLocation = newLocation

        if (personMarker == null) {
            // Lần đầu tạo marker, sử dụng currentIconTargetDp (đã được tính trong onMapReady)
            Log.d(
                "MainActivity",
                "Creating person marker for the first time with icon size: $currentIconTargetDp dp"
            )
            val initialIcon = bitmapDescriptorFromVector(
                R.drawable.ic_person_pin,
                currentIconTargetDp,
                currentIconTargetDp
            )

            if (initialIcon == null) {
                Log.e(
                    "MainActivity",
                    "Failed to create initial personIcon (size ${currentIconTargetDp}dp)."
                )
                // Fallback hoặc xử lý lỗi khác nếu cần
                return
            }
            // Sửa lỗi cú pháp .title("Person").title("Ban o day") -> chỉ một .title()
            val markerOptions =
                MarkerOptions().position(newLocation).title("Bạn ở đây").icon(initialIcon)
            personMarker = mMap.addMarker(markerOptions)
            Log.d(
                "MainActivity",
                "Person marker created at $newLocation with icon size $currentIconTargetDp dp"
            )
        } else {
            personMarker?.position = newLocation
            // Kích thước icon sẽ được cập nhật bởi onCameraIdleListener
        }

        if ((isFollowingCharacter || forceMoveCamera) && ::mMap.isInitialized) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, targetZoom))
        }
    }

    private fun bitmapDescriptorFromVector(
        vectorResId: Int,
        desiredWidthInDp: Int? = null,
        desiredHeightInDp: Int? = null
    ): BitmapDescriptor? {
        return ContextCompat.getDrawable(this, vectorResId)?.let { vectorDrawable ->
            val wPx = desiredWidthInDp?.let { dpToPx(it) } ?: vectorDrawable.intrinsicWidth
            val hPx = desiredHeightInDp?.let { dpToPx(it) } ?: vectorDrawable.intrinsicHeight

            if (wPx <= 0 || hPx <= 0) {
                Log.e(
                    "MainActivity",
                    "Vector drawable has invalid dimensions (w:$wPx, h:$hPx) for ResId: $vectorResId after dp conversion"
                )
                return null
            }

            vectorDrawable.setBounds(0, 0, wPx, hPx)
            val bitmap = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            vectorDrawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private fun setupContinuousLocationUpdates() {
        locationUpdateRunnable = Runnable {
            currentPersonLocation?.let {
                val newlat = it.latitude + (random.nextDouble() - 0.5) * 0.0005
                val newlng = it.longitude + (random.nextDouble() - 0.5) * 0.0005
                val simulatedNewLocation = LatLng(newlat, newlng)

                updateCharacterLocationAndCamera(
                    simulatedNewLocation,
                    mMap.cameraPosition.zoom.coerceAtLeast(DEFAULT_ZOOM_FOR_FOLLOWING)
                )
            } ?: run {
                val defaultLocation = LatLng(10.762622, 106.660172)
                updateCharacterLocationAndCamera(defaultLocation, DEFAULT_ZOOM_FOR_FOLLOWING, true)
            }

            if (isFollowingCharacter) {
                locationUpdateHandler.postDelayed(locationUpdateRunnable, UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private var currentIconTargetDp: Int = 36

    private fun getIconTargetDpForZoom(zoom: Float): Int {
        Log.d("IconScaling", "Calculating icon DP for zoom: $zoom")
        val dp = when {
            zoom < 8f -> 18
            zoom < 10f -> 24
            zoom < 12f -> 30
            zoom < 14f -> 36
            zoom < 16f -> 42
            zoom < 18f -> 48
            else -> 54
        }
        Log.d("IconScaling", "Target DP: $dp for zoom: $zoom")
        return dp
    }

    private fun startLocationUpdates() {
        locationUpdateHandler.removeCallbacks(locationUpdateRunnable)
        locationUpdateHandler.post(locationUpdateRunnable)
    }

    private fun stopLocationUpdates() {
        locationUpdateHandler.removeCallbacks(locationUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (isFollowingCharacter && currentPersonLocation != null && ::mMap.isInitialized) {
            startLocationUpdates()
            // Khi resume, camera có thể đã ở mức zoom khác, cập nhật lại icon nếu cần
            currentIconTargetDp = getIconTargetDpForZoom(mMap.cameraPosition.zoom)
            val resumedIcon = bitmapDescriptorFromVector(
                R.drawable.ic_person_pin,
                currentIconTargetDp,
                currentIconTargetDp
            )
            if (resumedIcon != null) {
                personMarker?.setIcon(resumedIcon)
            }
            mMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    currentPersonLocation!!,
                    mMap.cameraPosition.zoom.coerceAtLeast(DEFAULT_ZOOM_FOR_FOLLOWING)
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
}

class Heartactivity : AppCompatActivity() {

    private lateinit var textResult : TextView
    private lateinit var hrTextView : TextView
    private lateinit var spo2TextView : TextView

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateRunnable: Runnable
    private val UPDATE_INTERVAL_MS: Long = 2000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        supportActionBar?.hide()

        setContentView(R.layout.activity_heart)

        textResult = findViewById(R.id.TestResult)
        hrTextView = findViewById(R.id.Hr)
        spo2TextView = findViewById(R.id.spo2)

        setupAutoUpdate()
    }

    private fun setupAutoUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
                val hr = (45..130).random()
                val spo2 = (85..100).random()

                updateUI(hr, spo2)
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }

        handler.post(updateRunnable)
    }

    private fun updateUI(hr: Int, spo2: Int) {
        hrTextView.text = hr.toString()
        spo2TextView.text = spo2.toString()

        when (getHealthStatusColor(hr, spo2)) {
            "GREEN" -> {
                textResult.text = "Bình Thường"
                textResult.setTextColor(Color.parseColor("#C8E6C9"))
            }
            "YELLOW" -> {
                textResult.text = "Cảnh Báo Nhẹ"
                textResult.setTextColor(Color.parseColor("#FFF9C4"))
            }
            "RED" -> {
                textResult.text = "Nguy Hiểm"
                textResult.setTextColor(Color.parseColor("#FFCDD2"))
            }

        }
    }

    private fun getHealthStatusColor(hr: Int, spo2: Int): String {
        return when {
            hr < 50 || hr > 120 || spo2 < 90 -> "RED"
            hr in 50..59 || hr in 101..120 || spo2 in 90..94 -> "YELLOW"
            hr in 60..100 && spo2 >= 95 -> "GREEN"
            else -> "YELLOW"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }
}

class Notificationactivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        supportActionBar?.hide()

        setContentView(R.layout.activity_notification)
    }
}

class Sosactivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sos)
    }
}

class Locationactivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        supportActionBar?.hide()

        setContentView(R.layout.activity_location)
    }
}


