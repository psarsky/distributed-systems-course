package smarthome.server.model;

import smarthome.grpc.FreezerExtras;
import smarthome.grpc.FridgeState;
import smarthome.grpc.FridgeSubtype;
import smarthome.grpc.PowerState;
import smarthome.grpc.SmartExtras;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class Fridge {
    private final String id;
    private final FridgeSubtype subtype;
    private final String location;
    private final String description;

    private PowerState power = PowerState.POWER_ON;
    private double fridgeTempC;
    private double fridgeTargetC;
    private double freezerTempC;
    private double freezerTargetC;
    private boolean doorOpen = false;
    private final List<String> contents = new ArrayList<>();

    public Fridge(String id, FridgeSubtype subtype, String location, String description) {
        this.id = id;
        this.subtype = subtype;
        this.location = location;
        this.description = description;

        this.fridgeTargetC = 5.0;
        this.fridgeTempC = 5.0;
        if (hasFreezer()) {
            this.freezerTargetC = -18.0;
            this.freezerTempC = -18.0;
        }
        if (subtype == FridgeSubtype.SMART) {
            this.contents.add("Milk");
            this.contents.add("Eggs");
        }
    }

    public String getId() {
        return id;
    }

    public FridgeSubtype getSubtype() {
        return subtype;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    private boolean hasFreezer() {
        return subtype == FridgeSubtype.FREEZER || subtype == FridgeSubtype.SMART;
    }

    private boolean isSmart() {
        return subtype == FridgeSubtype.SMART;
    }

    public synchronized FridgeState setPower(PowerState newPower) {
        if (newPower == PowerState.POWER_STATE_UNSPECIFIED) {
            throw new IllegalArgumentException("power must be ON or OFF");
        }
        this.power = newPower;
        return toProto();
    }

    public synchronized FridgeState setFridgeTarget(double targetC) {
        if (targetC < 1.0 || targetC > 10.0) {
            throw new IllegalArgumentException("fridge target must be within 1.0 - 10.0 C, got " + targetC);
        }
        this.fridgeTargetC = targetC;
        return toProto();
    }

    public synchronized FridgeState setFreezerTarget(double targetC) {
        if (!hasFreezer()) {
            throw new IllegalStateException("fridge '" + id + "' (" + subtype + ") has no freezer");
        }
        if (targetC < -30.0 || targetC > -10.0) {
            throw new IllegalArgumentException("freezer target must be within -30.0 - -10.0 C, got " + targetC);
        }
        this.freezerTargetC = targetC;
        return toProto();
    }

    public synchronized FridgeState addItem(String item) {
        if (!isSmart()) {
            throw new IllegalStateException("fridge '" + id + "' (" + subtype + ") has no inventory");
        }
        String trimmed = item.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("item must not be empty");
        }
        this.contents.add(trimmed);
        return toProto();
    }

    public synchronized FridgeState removeItem(String item) {
        if (!isSmart()) {
            throw new IllegalStateException("fridge '" + id + "' (" + subtype + ") has no inventory");
        }
        String trimmed = item.trim();
        if (!this.contents.remove(trimmed)) {
            throw new NoSuchElementException("fridge '" + id + "' has no item '" + trimmed + "'");
        }
        return toProto();
    }

    public synchronized void tick(double step) {
        if (power != PowerState.POWER_ON) {
            return;
        }
        fridgeTempC = approach(fridgeTempC, fridgeTargetC, step);
        if (hasFreezer()) {
            freezerTempC = approach(freezerTempC, freezerTargetC, step);
        }
    }

    private static double approach(double current, double target, double step) {
        if (Math.abs(target - current) <= step) {
            return target;
        }
        return current + Math.signum(target - current) * step;
    }

    public synchronized FridgeState toProto() {
        FridgeState.Builder b = FridgeState.newBuilder()
                .setId(id)
                .setSubtype(subtype)
                .setPower(power)
                .setFridgeTempC(fridgeTempC)
                .setFridgeTargetC(fridgeTargetC)
                .setDoorOpen(doorOpen);
        if (isSmart()) {
            b.setSmart(SmartExtras.newBuilder()
                    .setFreezerTempC(freezerTempC)
                    .setFreezerTargetC(freezerTargetC)
                    .addAllContents(contents)
            );
        } else if (hasFreezer()) {
            b.setFreezer(FreezerExtras.newBuilder()
                    .setFreezerTempC(freezerTempC)
                    .setFreezerTargetC(freezerTargetC)
            );
        }
        return b.build();
    }
}
