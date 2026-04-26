package servantmgmt.server;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Identity;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Properties;
import com.zeroc.Ice.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class Server {
    private static final Path ICE_CONFIG_PATH = Path.of("server.config");
    private final String dedicatedCategory = "dedicated";
    private final String sharedCategory = "shared";
    private final Set<String> dedicatedNames = Set.of("ded-01", "ded-02", "ded-03", "ded-04", "ded-05");
    private final Set<String> sharedNames = Set.of("shr-01", "shr-02", "shr-03", "shr-04", "shr-05");

    public void run(String[] args) {
        int status = 0;
        IdentityRegistry identityRegistry = new IdentityRegistry(
                dedicatedCategory,
                sharedCategory,
                dedicatedNames,
                sharedNames
        );

        if (!Files.exists(ICE_CONFIG_PATH)) {
            throw new RuntimeException("Missing Ice config file: " + ICE_CONFIG_PATH.toAbsolutePath());
        }

        String[] initArgs = new String[]{"--Ice.Config=" + ICE_CONFIG_PATH.toAbsolutePath()};

        try (Communicator communicator = Util.initialize(initArgs)) {
            Properties properties = communicator.getProperties();
            if (properties.getProperty("ServantAdapter.Endpoints").isEmpty()) {
                properties.setProperty("ServantAdapter.Endpoints", "tcp -h 0.0.0.0 -p 10000");
            }

            ObjectAdapter adapter = communicator.createObjectAdapter("ServantAdapter");

            SharedCounterServant sharedServant = new SharedCounterServant(identityRegistry);
            for (String name : sharedNames) {
                adapter.add(sharedServant, new Identity(name, sharedCategory));
            }
            System.out.printf(
                    "Registered shared strategy: %d identities -> one servant@%s%n",
                    sharedNames.size(),
                    Integer.toHexString(System.identityHashCode(sharedServant))
            );

            LazyDedicatedServantLocator locator = new LazyDedicatedServantLocator(identityRegistry);
            adapter.addServantLocator(locator, dedicatedCategory);
            System.out.println("Registered dedicated strategy via ServantLocator + lazy ASM initialization");

            System.out.println("Known identities:");
            for (String name : dedicatedNames) {
                System.out.println("  - " + dedicatedCategory + "/" + name);
            }
            for (String name : sharedNames) {
                System.out.println("  - " + sharedCategory + "/" + name);
            }

            adapter.activate();
            communicator.waitForShutdown();
        } catch (Exception e) {
            status = 1;
            e.printStackTrace(System.err);
        }
        System.exit(status);
    }

    public static void main(String[] args) {
        new Server().run(args);
    }
}
