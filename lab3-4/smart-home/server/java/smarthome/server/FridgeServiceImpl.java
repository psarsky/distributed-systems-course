package smarthome.server;

import io.grpc.stub.StreamObserver;
import smarthome.grpc.ItemRequest;
import smarthome.grpc.FridgeControlGrpc;
import smarthome.grpc.FridgeId;
import smarthome.grpc.FridgeState;
import smarthome.grpc.SetFridgePowerRequest;
import smarthome.grpc.SetFridgeTempRequest;
import smarthome.server.model.Fridge;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

public class FridgeServiceImpl extends FridgeControlGrpc.FridgeControlImplBase {
    private final Deployment deployment;

    public FridgeServiceImpl(Deployment deployment) {
        this.deployment = deployment;
    }

    private Fridge require(String id) {
        Fridge f = deployment.fridges.get(id);
        if (f == null) {
            throw new NoSuchElementException("No fridge with id '" + id + "' on " + deployment.serverName);
        }
        return f;
    }

    private void respond(StreamObserver<FridgeState> obs, Supplier<FridgeState> op) {
        try {
            obs.onNext(op.get());
            obs.onCompleted();
        } catch (RuntimeException e) {
            obs.onError(GrpcErrors.toStatus(e).asRuntimeException());
        }
    }

    @Override
    public void get(FridgeId request, StreamObserver<FridgeState> obs) {
        respond(obs, () -> {
            Fridge f = require(request.getId());
            System.out.printf("[Fridge %s] Get%n", f.getId());
            return f.toProto();
        });
    }

    @Override
    public void setPower(SetFridgePowerRequest request, StreamObserver<FridgeState> obs) {
        respond(obs, () -> {
            Fridge f = require(request.getId());
            System.out.printf("[Fridge %s] SetPower(%s)%n", f.getId(), request.getPower());
            return f.setPower(request.getPower());
        });
    }

    @Override
    public void setFridgeTarget(SetFridgeTempRequest request, StreamObserver<FridgeState> obs) {
        respond(obs, () -> {
            Fridge f = require(request.getId());
            System.out.printf("[Fridge %s] SetFridgeTarget(%.1f)%n", f.getId(), request.getTargetC());
            return f.setFridgeTarget(request.getTargetC());
        });
    }

    @Override
    public void setFreezerTarget(SetFridgeTempRequest request, StreamObserver<FridgeState> obs) {
        respond(obs, () -> {
            Fridge f = require(request.getId());
            System.out.printf("[Fridge %s] SetFreezerTarget(%.1f)%n", f.getId(), request.getTargetC());
            return f.setFreezerTarget(request.getTargetC());
        });
    }

    @Override
    public void addItem(ItemRequest request, StreamObserver<FridgeState> obs) {
        respond(obs, () -> {
            Fridge f = require(request.getId());
            System.out.printf("[Fridge %s] AddItem(%s)%n", f.getId(), request.getItem());
            return f.addItem(request.getItem());
        });
    }

    @Override
    public void removeItem(ItemRequest request, StreamObserver<FridgeState> obs) {
        respond(obs, () -> {
            Fridge f = require(request.getId());
            System.out.printf("[Fridge %s] RemoveItem(%s)%n", f.getId(), request.getItem());
            return f.removeItem(request.getItem());
        });
    }
}
