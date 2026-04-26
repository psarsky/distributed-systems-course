package servantmgmt.server;

import ServantMgmt.Counter;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.Object;
import com.zeroc.Ice.ObjectNotExistException;
import com.zeroc.Ice.OperationNotExistException;
import com.zeroc.Ice.ServantLocator;
import com.zeroc.Ice.Util;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

public class LazyDedicatedServantLocator implements ServantLocator {
    private final IdentityRegistry identityRegistry;
    private final java.lang.Object lock = new java.lang.Object();
    private final LinkedHashMap<String, DedicatedCounterServant> dedicatedByIdentity = new LinkedHashMap<>(16, 0.75f, true);
    private final Counter metadataServant = new MetadataOnlyServant();
    private final int MAX_SERVANTS = 3;

    public LazyDedicatedServantLocator(IdentityRegistry identityRegistry) {
        this.identityRegistry = identityRegistry;
    }

    @Override
    public LocateResult locate(Current current) {
        String target = identityRegistry.id(current.id);
        String op = current.operation;

        if (identityRegistry.isUnknown(current.id)) {
            System.out.printf("[LOCATOR] unknown identity=%s op=%s%n", target, op);
            throw new ObjectNotExistException();
        }

        if (op.startsWith("ice_")) {
            System.out.printf(
                    "[LOCATOR] metadata operation op=%s on %s -> no dedicated servant instantiation%n",
                    op,
                    target
            );
            return new LocateResult(metadataServant, null);
        }

        synchronized (lock) {

            DedicatedCounterServant servant = dedicatedByIdentity.get(target);
            if (servant != null) {
                System.out.printf(
                        "[LOCATOR] ASM hit op=%s target=%s servant@%s%n",
                        op,
                        target,
                        Integer.toHexString(System.identityHashCode(servant))
                );
                return new LocateResult(servant, null);
            }

            DedicatedCounterServant created = new DedicatedCounterServant(identityRegistry, current.id);
            created.loadFromFile();

            current.adapter.add(created, current.id);
            dedicatedByIdentity.put(target, created);

            if (dedicatedByIdentity.size() > MAX_SERVANTS) {
                Iterator<Map.Entry<String, DedicatedCounterServant>> it = dedicatedByIdentity.entrySet().iterator();

                Map.Entry<String, DedicatedCounterServant> eldest = it.next();
                it.remove();

                String evictedId = eldest.getKey();
                DedicatedCounterServant evictedServant = eldest.getValue();

                System.out.printf(
                        "[EVICTOR] removing servant %s servant@%s%n",
                        evictedId,
                        Integer.toHexString(System.identityHashCode(evictedServant))
                );

                evictedServant.saveToFile();

                current.adapter.remove(Util.stringToIdentity(evictedId));
            }

            System.out.printf(
                    "[LOCATOR] lazy-created and registered dedicated servant in ASM for %s (total dedicated=%d)%n",
                    target,
                    dedicatedByIdentity.size()
            );
            return new LocateResult(created, null);
        }
    }

    @Override
    public void finished(Current current, Object servant, java.lang.Object cookie) {
        // No-op by design
    }

    @Override
    public void deactivate(String category) {
        System.out.println("[LOCATOR] deactivate category=" + category);
    }

    private static final class MetadataOnlyServant implements Counter {
        @Override
        public int getValue(Current current) {
            throw new OperationNotExistException();
        }

        @Override
        public void setValue(int newValue, Current current) {
            throw new OperationNotExistException();
        }

        @Override
        public int add(int delta, Current current) {
            throw new OperationNotExistException();
        }
    }
}
