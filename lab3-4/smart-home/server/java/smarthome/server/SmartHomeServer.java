package smarthome.server;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import smarthome.server.model.Fridge;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SmartHomeServer {

    private static final long TICK_PERIOD_SECONDS = 5;
    private static final double TICK_STEP_C = 0.5;

    public static void main(String[] args) throws Exception {
        String configName = args.length > 0
                ? args[0]
                : System.getenv().getOrDefault("SERVER_CONFIG", "server1.config");

        Deployment deployment = Deployment.load(Path.of(configName));

        Server server = Grpc.newServerBuilderForPort(deployment.port, InsecureServerCredentials.create())
                .addService(new DirectoryServiceImpl(deployment))
                .addService(new FridgeServiceImpl(deployment))
                .addService(new CameraServiceImpl(deployment))
                .build()
                .start();

        System.out.printf("=== %s started on port %d ===%n", deployment.serverName, deployment.port);
        System.out.printf("Fridges: %d, Cameras: %d%n",
                deployment.fridges.size(), deployment.cameras.size());
        for (var info : deployment.listDeviceInfos(smarthome.grpc.DeviceType.DEVICE_TYPE_UNSPECIFIED)) {
            System.out.printf("  - %-6s %-10s %-8s @ %s%n",
                    info.getType(), info.getId(), info.getSubtype(), info.getLocation());
        }
        System.out.println("Waiting for requests... (Ctrl+C to stop)");

        ScheduledExecutorService simulation = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "temperature-simulation");
            t.setDaemon(true);
            return t;
        });
        simulation.scheduleAtFixedRate(() -> {
            for (Fridge f : deployment.fridges.values()) {
                f.tick(TICK_STEP_C);
            }
        }, TICK_PERIOD_SECONDS, TICK_PERIOD_SECONDS, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down " + deployment.serverName + " ...");
            simulation.shutdownNow();
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
