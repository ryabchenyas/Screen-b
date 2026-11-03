# Screen Bridge — architecture

## Target device
Samsung Android phone.

## MVP stages
1. Ask the user for Android's MediaProjection permission.
2. Capture screen frames in a foreground service while the user-approved session is active.
3. Show clear streaming status and provide a stop action.
4. Add secure transport to a companion computer or backend.
5. Add optional camera streaming only after a separate user permission.

## Important
The initial prototype does not silently capture or transmit anything. Android requires explicit user approval for screen capture. This repository is the source project; APK packaging and device testing are separate steps.
