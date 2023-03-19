package com.retina_uav.tracker_poi_ar

import android.os.Bundle
import android.os.CountDownTimer
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.ar.core.Anchor
import com.google.ar.core.Earth
import com.google.ar.core.GeospatialPose
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.createAnchor
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ViewNode
import io.github.sceneview.utils.doOnApplyWindowInsets
import io.github.sceneview.utils.setFullScreen


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private lateinit var sceneView: ArSceneView
    private lateinit var loadingView: View
    private lateinit var statusText: TextView
    private lateinit var placeModelButton: ExtendedFloatingActionButton
    private lateinit var timerButton: ExtendedFloatingActionButton

    private lateinit var geospatialPoseText: TextView
    private lateinit var arText: TextView
    private lateinit var viewNode: ViewNode

    private var earth: Earth? = null
    private var modelNode: ArModelNode? = null

    data class Model(
        val fileLocation: String,
        val scaleUnits: Float? = null,
        val placementMode: PlacementMode = PlacementMode.BEST_AVAILABLE,
        val applyPoseRotation: Boolean = true
    )

    private val marker_model = Model(
        fileLocation = "marker.glb",
        scaleUnits = 1f,
        placementMode = PlacementMode.BEST_AVAILABLE,
        applyPoseRotation = false
    )

    private val timer = object: CountDownTimer(30000, 1000) {

        override fun onTick(millisUntilFinished: Long) {
            arText.text = "Counter : " + (millisUntilFinished / 1000)
        }

        override fun onFinish() {
            arText.text = "End"
        }
    }

    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.CAMERA), 1)*/

        setFullScreen(
            findViewById(R.id.rootView),
            fullScreen = true,
            hideSystemBars = true,
            fitsSystemWindows = false
        )

        statusText = findViewById(R.id.statusText)
        sceneView = findViewById<ArSceneView?>(R.id.sceneView).apply {
            geospatialEnabled = true
            onArTrackingFailureChanged = { reason ->
                statusText.text = reason?.getDescription(context)
                statusText.isGone = reason == null
            }
            planeRenderer.isShadowReceiver = false
        }

        loadingView = findViewById(R.id.loadingView)

        timerButton = findViewById<ExtendedFloatingActionButton>(R.id.timerButton).apply {
            // Add system bar margins
            val bottomMargin = (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
            doOnApplyWindowInsets { systemBarsInsets ->
                (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                    systemBarsInsets.bottom + bottomMargin
            }
            setOnClickListener { newModelNode() }
        }
        placeModelButton = findViewById<ExtendedFloatingActionButton>(R.id.placeModelButton).apply {
            setOnClickListener { placeModelNode() }
        }

        newModelNode()

        geospatialPoseText = findViewById(R.id.geospatialPoseText)
    }

    override fun onStart() {
        super.onStart()

        sceneView.lifecycle.addObserver(onArFrame = { frame ->
            earth?.let {
                if (it.trackingState == TrackingState.TRACKING)
                    updateGeospatialPoseText(it)
                else
                    geospatialPoseText.text = it.earthState.toString() + " - " + it.trackingState.toString()
            } ?: run {
                earth = sceneView.arSession?.earth!!
            }
        })
    }

    fun functionTestView() {
        if (!::arText.isInitialized) {
            arText = viewNode.renderable!!.view as TextView
        }

        timer.start()
    }

    private fun updateGeospatialPoseText(earth: Earth) {
        val geospatialPose: GeospatialPose = earth.getCameraGeospatialPose()
        val quaternion = geospatialPose.eastUpSouthQuaternion
        val poseText = resources
            .getString(
                R.string.geospatial_pose,
                geospatialPose.latitude,
                geospatialPose.longitude,
                geospatialPose.horizontalAccuracy,
                geospatialPose.altitude,
                geospatialPose.verticalAccuracy,
                quaternion[0],
                quaternion[1],
                quaternion[2],
                quaternion[3],
                geospatialPose.orientationYawAccuracy
            )
        runOnUiThread { geospatialPoseText.text = poseText }
    }

    private fun placeModelNode() {
        earth?.let {
            var earthAnchor: Anchor? = null
            if (it.trackingState == TrackingState.TRACKING) {
                geospatialPoseText.text = "TRACKING ON !"

                val altitude = it.cameraGeospatialPose.altitude - 1

                val geospatialHitPose = it.getGeospatialPose(modelNode?.hitResult?.hitPose)
                val latitude = geospatialHitPose.latitude
                val longitude = geospatialHitPose.longitude
                val rotation = Rotation(0f, 0f, 0f)

                earthAnchor = it.createAnchor(latitude, longitude, altitude, rotation)

                modelNode?.anchor = earthAnchor
            }
        } ?: modelNode?.anchor()

        placeModelButton.isVisible = false
        sceneView.planeRenderer.isVisible = false
    }

    private fun newModelNode() {
        isLoading = true
        modelNode?.takeIf { !it.isAnchored }?.let {
            sceneView.removeChild(it)
            it.destroy()
        }

        modelNode = ArModelNode(marker_model.placementMode).apply {
            applyPoseRotation = marker_model.applyPoseRotation
            loadModelGlbAsync(
                context = this@MainActivity,
                glbFileLocation = marker_model.fileLocation,
                autoAnimate = false,
                scaleToUnits = marker_model.scaleUnits,
                // Place the model origin at the bottom center

                centerOrigin = Position(y = -1.0f)
            ) {
                sceneView.planeRenderer.isVisible = true
                isLoading = false
            }
            onAnchorChanged = { node, _ ->
                placeModelButton.isGone = node.isAnchored
            }
            onHitResult = { node, _ ->
                placeModelButton.isGone = !node.isTracking
            }
        }

        viewNode = ViewNode().apply {
            parent = modelNode
            loadView(this@MainActivity, lifecycle, R.layout.view_text)
            isEditable = true
            position = Position(0.0f, 1.26f, 0.0f)
            scale = Scale(1f)
        }

        sceneView.addChild(modelNode!!)
        // Select the model node by default (the model node is also selected on tap)
        sceneView.selectedNode = modelNode
    }
}