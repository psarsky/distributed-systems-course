package servantmgmt.server;

import ServantMgmt.Counter;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.Identity;
import com.zeroc.Ice.ObjectNotExistException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SharedCounterServant implements Counter {
    private final IdentityRegistry identityRegistry;
    private final ConcurrentMap<String, CounterState> stateByObject = new ConcurrentHashMap<>();

    public SharedCounterServant(IdentityRegistry identityRegistry) {
        this.identityRegistry = identityRegistry;
    }

    private static final class CounterState {
        private int value = 0;
    }

    @Override
    public int getValue(Current current) {
        CounterState state = stateFor(current.id);
        synchronized (state) {
            System.out.printf(
                    "[SHARED][servant@%s] op=getValue target=%s value=%d%n",
                    Integer.toHexString(System.identityHashCode(this)),
                    identityRegistry.id(current.id),
                    state.value
            );
            return state.value;
        }
    }

    @Override
    public void setValue(int newValue, Current current) {
        CounterState state = stateFor(current.id);
        synchronized (state) {
            state.value = newValue;
            System.out.printf(
                    "[SHARED][servant@%s] op=setValue target=%s value=%d%n",
                    Integer.toHexString(System.identityHashCode(this)),
                    identityRegistry.id(current.id),
                    state.value
            );
        }
    }

    @Override
    public int add(int delta, Current current) {
        CounterState state = stateFor(current.id);
        synchronized (state) {
            state.value += delta;
            System.out.printf(
                    "[SHARED][servant@%s] op=add target=%s delta=%d value=%d%n",
                    Integer.toHexString(System.identityHashCode(this)),
                    identityRegistry.id(current.id),
                    delta,
                    state.value
            );
            return state.value;
        }
    }

    private CounterState stateFor(Identity id) {
        if (identityRegistry.isUnknown(id)) {
            throw new ObjectNotExistException();
        }

        String key = identityRegistry.id(id);
        return stateByObject.computeIfAbsent(key, k -> {
            System.out.printf(
                    "[SHARED][servant@%s] created logical state for %s%n",
                    Integer.toHexString(System.identityHashCode(this)),
                    k
            );
            return new CounterState();
        });
    }
}
