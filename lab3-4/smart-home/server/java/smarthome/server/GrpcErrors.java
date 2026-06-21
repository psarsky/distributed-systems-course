package smarthome.server;

import io.grpc.Status;

import java.util.NoSuchElementException;

final class GrpcErrors {
    private GrpcErrors() {}

    static Status toStatus(RuntimeException e) {
        return switch (e) {
            case NoSuchElementException _ -> Status.NOT_FOUND.withDescription(e.getMessage());
            case IllegalArgumentException _ -> Status.INVALID_ARGUMENT.withDescription(e.getMessage());
            case IllegalStateException _ -> Status.FAILED_PRECONDITION.withDescription(e.getMessage());
            default -> Status.INTERNAL.withDescription(String.valueOf(e.getMessage()));
        };
    }
}
