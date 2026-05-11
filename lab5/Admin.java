package space;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Admin {

    void main() throws Exception {
        try (Connection conn = Common.openConnection()) {
            Channel ch = conn.createChannel();
            Common.declareTopology(ch);

            // spy queue: receives a copy of EVERY message in the system
            String spy = "admin.spy";
            ch.queueDeclare(spy, true, false, false, null);
            ch.queueBind(spy, Common.EXCHANGE, "#");

            DeliverCallback spyCallback = (_, delivery) -> {
                String key = delivery.getEnvelope().getRoutingKey();
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[SPY] (" + key + ") " + body);
            };
            ch.basicConsume(spy, true, spyCallback, _ -> {});

            System.out.println("Administrator online. Commands:");
            System.out.println("  a <msg>   - broadcast to all Agencies");
            System.out.println("  c <msg>   - broadcast to all Carriers");
            System.out.println("  all <msg> - broadcast to Agencies AND Carriers");
            System.out.println("  q         - quit");

            Scanner sc = new Scanner(System.in);
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.isBlank()) continue;
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("q")) break;

                String routingKey;
                String payload;
                if (trimmed.toLowerCase().startsWith("all ")) {
                    routingKey = Common.ADMIN_ALL;
                    payload = trimmed.substring(4);
                } else if (trimmed.toLowerCase().startsWith("a ")) {
                    routingKey = Common.ADMIN_AGENCIES;
                    payload = trimmed.substring(2);
                } else if (trimmed.toLowerCase().startsWith("c ")) {
                    routingKey = Common.ADMIN_CARRIERS;
                    payload = trimmed.substring(2);
                } else {
                    System.out.println("Unknown command");
                    continue;
                }

                ch.basicPublish(Common.EXCHANGE, routingKey, null, payload.getBytes(StandardCharsets.UTF_8));
                System.out.println("[ADMIN] sent (" + routingKey + ") " + payload);
            }
        }
    }
}
