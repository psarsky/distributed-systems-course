package servantmgmt.server;

import ServantMgmt.Counter;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.Identity;

import java.nio.file.Files;
import java.nio.file.Path;

public class DedicatedCounterServant implements Counter {
    private final IdentityRegistry identityRegistry;
    private final String objectId;
    private int value = 0;

    public DedicatedCounterServant(IdentityRegistry identityRegistry, Identity identity) {
        this.identityRegistry = identityRegistry;
        this.objectId = identity.name;
        System.out.printf(
                "[DEDICATED][servant@%s] instantiated for %s%n",
                Integer.toHexString(System.identityHashCode(this)),
                objectId
        );
    }

    @Override
    public synchronized int getValue(Current current) {
        System.out.printf(
                "[DEDICATED][servant@%s] op=getValue target=%s value=%d%n",
                Integer.toHexString(System.identityHashCode(this)),
                identityRegistry.id(current.id),
                value
        );
        return value;
    }

    @Override
    public synchronized void setValue(int newValue, Current current) {
        value = newValue;
        System.out.printf(
                "[DEDICATED][servant@%s] op=setValue target=%s value=%d%n",
                Integer.toHexString(System.identityHashCode(this)),
                identityRegistry.id(current.id),
                value
        );
    }

    @Override
    public synchronized int add(int delta, Current current) {
        value += delta;
        System.out.printf(
                "[DEDICATED][servant@%s] op=add target=%s delta=%d value=%d%n",
                Integer.toHexString(System.identityHashCode(this)),
                identityRegistry.id(current.id),
                delta,
                value
        );
        return value;
    }

    public synchronized void loadFromFile() {
        try {
            Path path = Path.of("/tmp/dedicated/" + objectId + ".txt");
            if (Files.exists(path)) {
                String content = Files.readString(path);
                value = Integer.parseInt(content.trim());

                System.out.printf(
                        "[DEDICATED] restored %s = %d%n",
                        objectId,
                        value
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void saveToFile() {
        try {
            Path dir = Path.of("/tmp/dedicated");
            Files.createDirectories(dir);

            Path path = dir.resolve(objectId + ".txt");
            Files.writeString(
                    path,
                    Integer.toString(value)
            );

            System.out.printf(
                    "[DEDICATED] saved %s = %d%n",
                    objectId,
                    value
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
