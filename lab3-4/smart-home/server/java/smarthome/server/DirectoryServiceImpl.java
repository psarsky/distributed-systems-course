package smarthome.server;

import io.grpc.stub.StreamObserver;
import smarthome.grpc.DeviceList;
import smarthome.grpc.DirectoryGrpc;
import smarthome.grpc.ListDevicesRequest;

public class DirectoryServiceImpl extends DirectoryGrpc.DirectoryImplBase {
    private final Deployment deployment;

    public DirectoryServiceImpl(Deployment deployment) {
        this.deployment = deployment;
    }

    @Override
    public void listDevices(ListDevicesRequest request, StreamObserver<DeviceList> responseObserver) {
        System.out.printf("[Directory] ListDevices(filter=%s)%n", request.getTypeFilter());
        DeviceList reply = DeviceList.newBuilder()
                .setServerName(deployment.serverName)
                .addAllDevices(deployment.listDeviceInfos(request.getTypeFilter()))
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
