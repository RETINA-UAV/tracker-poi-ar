# Tracker points of interest in augmented reality

Augmented reality application based on the [SceneView library](https://github.com/SceneView/sceneview-android) and the [Google Geospatial API](https://developers.google.com/ar/develop/geospatial)

## Setup & Usage
Clone this repository and import it into your Android Studio.

To use the app, you will need to create an account for the Google Cloud Console and activate and set up the [ARCore API](https://console.cloud.google.com/apis/library/arcore).  
The API key needs to be defined in the `local.properties` as `YOUR_API_KEY`.

## Known issues

The permissions of the location must be done manually. Unfortunately, the location permissions via the application could not be done
