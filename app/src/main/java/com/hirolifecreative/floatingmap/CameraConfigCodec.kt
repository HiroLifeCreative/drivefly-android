package com.hirolifecreative.floatingmap
import org.json.JSONObject

/** Used by storage, cross-process previews and rotation state. Older preferences stay valid. */
object CameraConfigCodec {
    fun decode(json: JSONObject) = CameraConfig(json.optInt("tilt",60),json.optInt("distancePercent",100),
        json.optBoolean("speedZoomEnabled",false),json.optInt("slowMph",15),json.optInt("fastMph",60),
        json.optInt("slowDistanceChange",20),json.optInt("fastDistanceChange",-20),json.optBoolean("nativePuck3D",false),json.optInt("puckTilt",0),json.optInt("puckSizePercent",100)).normalized()
    fun encode(config: CameraConfig): JSONObject {
        val c=config.normalized()
        return JSONObject().put("tilt",c.tilt).put("distancePercent",c.distancePercent).put("speedZoomEnabled",c.speedZoomEnabled)
            .put("slowMph",c.slowMph).put("fastMph",c.fastMph).put("slowDistanceChange",c.slowDistanceChange).put("fastDistanceChange",c.fastDistanceChange).put("nativePuck3D",c.nativePuck3D).put("puckTilt",c.puckTilt).put("puckSizePercent",c.puckSizePercent)
    }
}
