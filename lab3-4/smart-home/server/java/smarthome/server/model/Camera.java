package smarthome.server.model;

import smarthome.grpc.CameraState;
import smarthome.grpc.CameraSubtype;
import smarthome.grpc.PowerState;
import smarthome.grpc.Preset;
import smarthome.grpc.PtzExtras;
import smarthome.grpc.PtzPosition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class Camera {
    private final String id;
    private final CameraSubtype subtype;
    private final String location;
    private final String description;

    private PowerState power = PowerState.POWER_ON;
    private boolean recording = false;

    private double pan = 0.0;
    private double tilt = 0.0;
    private double zoom = 1.0;

    private final Map<String, double[]> presets = new LinkedHashMap<>();

    public Camera(String id, CameraSubtype subtype, String location, String description) {
        this.id = id;
        this.subtype = subtype;
        this.location = location;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public CameraSubtype getSubtype() {
        return subtype;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    private boolean ptzCapable() {
        return subtype == CameraSubtype.PTZ;
    }

    private void requirePtz() {
        if (!ptzCapable()) {
            throw new IllegalStateException("camera '" + id + "' is FIXED and has no PTZ head");
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public synchronized CameraState setPower(PowerState newPower) {
        if (newPower == PowerState.POWER_STATE_UNSPECIFIED) {
            throw new IllegalArgumentException("power must be ON or OFF");
        }
        if (newPower == PowerState.POWER_OFF) {
            this.recording = false;
        }
        this.power = newPower;
        return toProto();
    }

    public synchronized CameraState setRecording(boolean on) {
        if (power != PowerState.POWER_ON) {
            throw new IllegalStateException("camera '" + id + "' is powered off");
        }
        this.recording = on;
        return toProto();
    }

    public synchronized CameraState move(double panDelta, double tiltDelta, double zoomDelta) {
        requirePtz();
        pan = clamp(pan + panDelta, -180.0, 180.0);
        tilt = clamp(tilt + tiltDelta, -90.0, 90.0);
        zoom = clamp(zoom + zoomDelta, 1.0, 10.0);
        return toProto();
    }

    public synchronized CameraState savePreset(String name) {
        requirePtz();
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("preset name must not be empty");
        }
        presets.put(trimmed, new double[]{pan, tilt, zoom});
        return toProto();
    }

    public synchronized CameraState goToPreset(String name) {
        requirePtz();
        String trimmed = name.trim();
        double[] p = presets.get(trimmed);
        if (p == null) {
            throw new NoSuchElementException("camera '" + id + "' has no preset '" + trimmed + "'");
        }
        pan = p[0];
        tilt = p[1];
        zoom = p[2];
        return toProto();
    }

    public synchronized CameraState removePreset(String name) {
        requirePtz();
        String trimmed = name.trim();
        if (presets.remove(trimmed) == null) {
            throw new NoSuchElementException("camera '" + id + "' has no preset '" + trimmed + "'");
        }
        return toProto();
    }

    public synchronized CameraState toProto() {
        CameraState.Builder b = CameraState.newBuilder()
                .setId(id)
                .setSubtype(subtype)
                .setPower(power)
                .setRecording(recording);
        if (ptzCapable()) {
            PtzExtras.Builder ptz = PtzExtras.newBuilder()
                    .setPosition(PtzPosition.newBuilder()
                            .setPan(pan)
                            .setTilt(tilt)
                            .setZoom(zoom)
                    );
            for (Map.Entry<String, double[]> e : presets.entrySet()) {
                double[] p = e.getValue();
                ptz.addPresets(Preset.newBuilder()
                        .setName(e.getKey())
                        .setPosition(PtzPosition.newBuilder()
                                .setPan(p[0])
                                .setTilt(p[1])
                                .setZoom(p[2])
                        )
                );
            }
            b.setPtz(ptz);
        }
        return b.build();
    }
}
