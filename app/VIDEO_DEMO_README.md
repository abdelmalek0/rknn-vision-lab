This demo shows how to play video from RTSP/HTTP URLs, device camera (CameraX), or local files using Java on Android.

Files added:
- `src/main/java/com/smartprints/rknn_vision_lab/video/VideoDemoActivity.java` - Activity demonstrating ExoPlayer + CameraX usage.
- `src/main/res/layout/activity_video_demo.xml` - Layout with PlayerView and PreviewView.
- `build.gradle` (app) updated to add ExoPlayer and CameraX dependencies.
- `AndroidManifest.xml` updated with required permissions and activity entry.

How to use:
- Launch `VideoDemoActivity` from your app (e.g., start via an Intent from `MainActivity`).
- Enter an RTSP/HTTP URL and tap "Play URL" to stream via ExoPlayer (requires network/rtsp server).
- Tap "Pick File" to choose a local video (storage access via system picker).
- Tap "Camera" to start device camera preview using CameraX.

Notes & permissions:
- Camera, RECORD_AUDIO, and INTERNET permissions are requested. For Android 11+ storage access is via system picker so WRITE_EXTERNAL_STORAGE may not be necessary, but included for older devices.
- ExoPlayer RTSP support needs the `extension-rtsp` artifact.

Limitations & next steps:
- Add error listeners and connection retries for RTSP streams.
- Add UI to switch front/back camera and record camera stream if needed.
- Improve permission UX and handle permanently denied permissions (open settings).
