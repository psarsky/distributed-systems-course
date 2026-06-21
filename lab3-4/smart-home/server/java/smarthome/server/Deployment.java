package smarthome.server;

import smarthome.grpc.CameraSubtype;
import smarthome.grpc.DeviceInfo;
import smarthome.grpc.DeviceType;
import smarthome.grpc.FridgeSubtype;
import smarthome.server.model.Camera;
import smarthome.server.model.Fridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Deployment {
    public final String serverName;
    public final int port;
    public final Map<String, Fridge> fridges = new LinkedHashMap<>();
    public final Map<String, Camera> cameras = new LinkedHashMap<>();

    private Deployment(String serverName, int port) {
        this.serverName = serverName;
        this.port = port;
    }

    public static Deployment load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            throw new IOException("Missing config file: " + configPath.toAbsolutePath());
        }

        String serverName = "Smart-Home-Server";
        int port = 50051;
        List<String[]> deviceLines = new ArrayList<>();

        for (String rawLine : Files.readAllLines(configPath)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("server.name")) {
                serverName = valueAfterEquals(line);
            } else if (line.startsWith("listen.port")) {
                port = Integer.parseInt(valueAfterEquals(line));
            } else {
                String[] parts = line.split("\\|");
                if (parts.length != 5) {
                    throw new IOException("Bad device line (need 5 fields): " + line);
                }
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim();
                }
                deviceLines.add(parts);
            }
        }

        Deployment d = new Deployment(serverName, port);
        for (String[] p : deviceLines) {
            d.addDevice(p[0], p[1], p[2], p[3], p[4]);
        }
        return d;
    }

    private void addDevice(String type, String id, String subtype, String location, String description) {
        switch (type.toLowerCase()) {
            case "fridge" -> fridges.put(id, new Fridge(id, FridgeSubtype.valueOf(subtype), location, description));
            case "camera" -> cameras.put(id, new Camera(id, CameraSubtype.valueOf(subtype), location, description));
            default -> throw new IllegalArgumentException("Unknown device type: " + type);
        }
    }

    private static String valueAfterEquals(String line) {
        return line.substring(line.indexOf('=') + 1).trim();
    }

    public List<DeviceInfo> listDeviceInfos(DeviceType filter) {
        List<DeviceInfo> out = new ArrayList<>();
        if (filter == DeviceType.DEVICE_TYPE_UNSPECIFIED || filter == DeviceType.FRIDGE) {
            for (Fridge f : fridges.values()) {
                out.add(DeviceInfo.newBuilder()
                        .setId(f.getId())
                        .setType(DeviceType.FRIDGE)
                        .setSubtype(f.getSubtype().name())
                        .setLocation(f.getLocation())
                        .setDescription(f.getDescription())
                        .build()
                );
            }
        }
        if (filter == DeviceType.DEVICE_TYPE_UNSPECIFIED || filter == DeviceType.CAMERA) {
            for (Camera c : cameras.values()) {
                out.add(DeviceInfo.newBuilder()
                        .setId(c.getId())
                        .setType(DeviceType.CAMERA)
                        .setSubtype(c.getSubtype().name())
                        .setLocation(c.getLocation())
                        .setDescription(c.getDescription())
                        .build()
                );
            }
        }
        return out;
    }
}
