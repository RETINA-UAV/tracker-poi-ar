package com.retina_uav.tracker_poi_ar

import android.os.Bundle
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
import kotlin.math.*


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private lateinit var sceneView: ArSceneView
    private lateinit var loadingView: View
    private lateinit var statusText: TextView
    private lateinit var startInfoButton: ExtendedFloatingActionButton
    private lateinit var placePOIButton: ExtendedFloatingActionButton

    private lateinit var geospatialPoseText: TextView
    private lateinit var arText: TextView
    private lateinit var viewNode: ViewNode

    private var earth: Earth? = null
    private var modelNode: ArModelNode? = null

    private var sizeModel: Float = 1.8f
    private var isStart: Boolean = false

    data class Model(
        val fileLocation: String,
        val scaleUnits: Float? = null,
        val placementMode: PlacementMode = PlacementMode.BEST_AVAILABLE,
        val applyPoseRotation: Boolean = true
    )

    data class PointGPS(
        val latitude: Double,
        val longitude: Double
    )

    private val marker_model = Model(
        fileLocation = "marker.glb",
        scaleUnits = 1f,
        placementMode = PlacementMode.BEST_AVAILABLE,
        applyPoseRotation = false
    )

    private lateinit var myCurrentPOI: PointGPS

    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        placePOIButton = findViewById<ExtendedFloatingActionButton>(R.id.placePOIButton).apply {
            // Add system bar margins
            val bottomMargin = (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
            doOnApplyWindowInsets { systemBarsInsets ->
                (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                    systemBarsInsets.bottom + bottomMargin
            }
            setOnClickListener { placeModelNode() }
        }
        startInfoButton = findViewById<ExtendedFloatingActionButton>(R.id.startInfoButton).apply {
            setOnClickListener {
                viewNode.isVisible = true
                isStart = true
            }
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

            if(isStart) {
                updateARText()
            }
        })
    }

    fun updateARText() {
        if (!::arText.isInitialized) {
            arText = viewNode.renderable!!.view as TextView
        }
        if (::myCurrentPOI.isInitialized) {
            earth?.let {
                val lat1 = it.cameraGeospatialPose.latitude
                val lon1 = it.cameraGeospatialPose.longitude

                val lat2 = myCurrentPOI.latitude
                val lon2 = myCurrentPOI.longitude

                arText.text = "Dist : " + "%.2f".format(distanceGPS(lat1, lon1, lat2, lon2)) + " m"
            } ?: run {arText.text = "FAILED"}
        }
    }

    private fun updateGeospatialPoseText(earth: Earth) {
        val geospatialPose = earth.getCameraGeospatialPose()
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

                val quaternion = Rotation(0f, 0f, 0f)
                val altitude = it.cameraGeospatialPose.altitude - 1

                val geospatialHitPose = it.getGeospatialPose(modelNode?.hitResult?.hitPose)
                val latitude = geospatialHitPose.latitude
                val longitude = geospatialHitPose.longitude
                myCurrentPOI = PointGPS(latitude, longitude)

                val rotation = Rotation(0f, 0f, 0f)

                earthAnchor = it.createAnchor(latitude, longitude, altitude, quaternion)

                modelNode?.anchor = earthAnchor
            }
        } ?: run{ modelNode?.anchor() }

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
                autoAnimate = true,
                scaleToUnits = marker_model.scaleUnits,
                // Place the model origin at the bottom center

                centerOrigin = Position(y = -1.0f)
            ) {
                sceneView.planeRenderer.isVisible = true
                isLoading = false
            }
            onAnchorChanged = { node, _ ->
                placePOIButton.isGone = node.isAnchored
            }
            onHitResult = { node, _ ->
                placePOIButton.isGone = !node.isTracking
            }
        }

        viewNode = ViewNode().apply {
            parent = modelNode
            loadView(this@MainActivity, lifecycle, R.layout.view_text)
            isEditable = true
            position = Position(0.0f, sizeModel + 0.1f, 0.0f)
            scale = Scale(2f)
            isVisible = false
        }

        sceneView.addChild(modelNode!!)
        sceneView.selectedNode = modelNode
    }

    private fun distanceGPS(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371
        val dLat = Math.toRadians(lat2 - lat1);
        val dLon = Math.toRadians(lon2 - lon1);
        val originLat = Math.toRadians(lat1);
        val destinationLat = Math.toRadians(lat2);

        val a = sin(dLat / 2).pow(2.toDouble()) + sin(dLon / 2).pow(2.toDouble()) * cos(originLat) * cos(destinationLat);
        val c = 2 * asin(sqrt(a));
        return earthRadiusKm * c * 1000
    }
}