package io.github.honggi82.vr3dmobile.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.honggi82.vr3dmobile.R
import io.github.honggi82.vr3dmobile.sensor.TiltTracker
import io.github.honggi82.vr3dmobile.storage.ProjectRepository
import io.github.honggi82.vr3dmobile.ui.Ui.asPill
import io.github.honggi82.vr3dmobile.ui.Ui.dp

class ViewerActivity : Activity() {
    private lateinit var spatialView: SpatialImageView
    private lateinit var angleText: TextView
    private lateinit var tiltTracker: TiltTracker

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LanguageStore.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageId = intent.getStringExtra(EXTRA_PACKAGE_ID)
        val loaded = packageId?.let { ProjectRepository(this).load(it) }
        if (loaded == null) {
            Toast.makeText(this, R.string.project_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        buildUi()
        spatialView.setProject(loaded.second, loaded.first)
        tiltTracker = TiltTracker(this) { pitch, roll ->
            spatialView.setTilt(pitch, roll)
            angleText.text = getString(R.string.angle_readout, roll, pitch)
        }
        if (!tiltTracker.isAvailable) {
            Toast.makeText(this, R.string.sensor_unavailable, Toast.LENGTH_LONG).show()
        }
        enterImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        if (::tiltTracker.isInitialized) tiltTracker.start()
        enterImmersiveMode()
    }

    override fun onPause() {
        if (::tiltTracker.isInitialized) tiltTracker.stop()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::tiltTracker.isInitialized) tiltTracker.resetCalibration()
        enterImmersiveMode()
    }

    override fun onDestroy() {
        if (::spatialView.isInitialized) spatialView.close()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Ui.BACKGROUND) }
        spatialView = SpatialImageView(this)
        root.addView(spatialView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        top.addView(Button(this).apply {
            text = getString(R.string.viewer_library)
            asPill(0xbb071019.toInt())
            setOnClickListener { finish() }
        })
        angleText = TextView(this).apply {
            text = getString(R.string.angle_readout, 0f, 0f)
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            asPill(0xbb071019.toInt())
        }
        top.addView(angleText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) })
        root.addView(top, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        root.addView(Button(this).apply {
            text = getString(R.string.center_view)
            asPill(0xfff5f8ff.toInt(), Ui.BACKGROUND)
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { if (::tiltTracker.isInitialized) tiltTracker.center() }
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(24)
        })
        setContentView(root)
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    companion object {
        const val EXTRA_PACKAGE_ID = "package_id"
    }
}
