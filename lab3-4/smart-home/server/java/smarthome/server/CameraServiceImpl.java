package smarthome.server;

import io.grpc.stub.StreamObserver;
import smarthome.grpc.CameraControlGrpc;
import smarthome.grpc.CameraId;
import smarthome.grpc.CameraState;
import smarthome.grpc.MoveRequest;
import smarthome.grpc.PresetRequest;
import smarthome.grpc.SetCameraPowerRequest;
import smarthome.grpc.SetRecordingRequest;
import smarthome.server.model.Camera;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

public class CameraServiceImpl extends CameraControlGrpc.CameraControlImplBase {
    private final Deployment deployment;

    public CameraServiceImpl(Deployment deployment) {
        this.deployment = deployment;
    }

    private Camera require(String id) {
        Camera c = deployment.cameras.get(id);
        if (c == null) {
            throw new NoSuchElementException("No camera with id '" + id + "' on " + deployment.serverName);
        }
        return c;
    }

    private void respond(StreamObserver<CameraState> obs, Supplier<CameraState> op) {
        try {
            obs.onNext(op.get());
            obs.onCompleted();
        } catch (RuntimeException e) {
            obs.onError(GrpcErrors.toStatus(e).asRuntimeException());
        }
    }

    @Override
    public void get(CameraId request, StreamObserver<CameraState> obs) {
        respond(obs, () -> {
            Camera c = require(request.getId());
            System.out.printf("[Camera %s] Get%n", c.getId());
            return c.toProto();
        });
    }

    @Override
    public void setPower(SetCameraPowerRequest request, StreamObserver<CameraState> obs) {
        respond(obs, () -> {
            Camera c = require(request.getId());
            System.out.printf("[Camera %s] SetPower(%s)%n", c.getId(), request.getPower());
            return c.setPower(request.getPower());
        });
    }

    @Override
    public void setRecording(SetRecordingRequest request, StreamObserver<CameraState> obs) {
        respond(obs, () -> {
            Camera c = require(request.getId());
            System.out.printf("[Camera %s] SetRecording(%s)%n", c.getId(), request.getRecording());
            return c.setRecording(request.getRecording());
        });
    }

    @Override
    public void move(MoveRequest request, StreamObserver<CameraState> obs) {
        respond(obs, () -> {
            Camera c = require(request.getId());
            System.out.printf("[Camera %s] Move(dpan=%.1f, dtilt=%.1f, dzoom=%.1f)%n",
                    c.getId(), request.getPanDelta(), request.getTiltDelta(), request.getZoomDelta());
            return c.move(request.getPanDelta(), request.getTiltDelta(), request.getZoomDelta());
        });
    }

    @Override
    public void savePreset(PresetRequest request, StreamObserver<CameraState> obs) {
        respond(obs, () -> {
            Camera c = require(request.getId());
            System.out.printf("[Camera %s] SavePreset(%s)%n", c.getId(), request.getName());
            return c.savePreset(request.getName());
        });
    }

    @Override
    public void goToPreset(PresetRequest request, StreamObserver<CameraState> obs) {
        respond(obs, () -> {
            Camera c = require(request.getId());
            System.out.printf("[Camera %s] GoToPreset(%s)%n", c.getId(), request.getName());
            return c.goToPreset(request.getName());
        });
    }

    @Override
    public void removePreset(PresetRequest request, StreamObserver<CameraState> obs) {
        respond(obs, () -> {
            Camera c = require(request.getId());
            System.out.printf("[Camera %s] RemovePreset(%s)%n", c.getId(), request.getName());
            return c.removePreset(request.getName());
        });
    }
}
