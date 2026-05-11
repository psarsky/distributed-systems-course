package space;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public final class Common {
    public static final String EXCHANGE = "space.topic";

    public static final String SVC_PERSONS   = "persons";
    public static final String SVC_CARGO     = "cargo";
    public static final String SVC_SATELLITE = "satellite";

    public static final String[] ALL_SERVICES = { SVC_PERSONS, SVC_CARGO, SVC_SATELLITE };

    public static final String ADMIN_AGENCIES = "admin.agencies";
    public static final String ADMIN_CARRIERS = "admin.carriers";
    public static final String ADMIN_ALL      = "admin.all";

    private Common() {}

    public static String orderQueue(String service)   { return "orders." + service; }
    public static String orderKey(String service)     { return "order." + service; }
    public static String confirmationKey(String agency) { return "confirmation." + agency; }

    public static Connection openConnection() throws Exception {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost("localhost");
        f.setPort(5672);
        f.setUsername("guest");
        f.setPassword("guest");
        return f.newConnection();
    }

    public static void declareTopology(Channel ch) throws Exception {
        ch.exchangeDeclare(EXCHANGE, BuiltinExchangeType.TOPIC, true);
        for (String s : ALL_SERVICES) {
            ch.queueDeclare(orderQueue(s), true, false, false, null);
            ch.queueBind(orderQueue(s), EXCHANGE, orderKey(s));
        }
    }

    public static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    public static String resolveService(String raw) {
        String n = normalize(raw);
        if (n.startsWith("p") || n.startsWith("o")) return SVC_PERSONS;
        if (n.startsWith("c") || n.startsWith("l")) return SVC_CARGO;
        if (n.startsWith("s")) return SVC_SATELLITE;
        return null;
    }
}
