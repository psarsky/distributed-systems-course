package space;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Agency {

    void main() throws Exception {
        Scanner sc = new Scanner(System.in);

        String n;
        while (true) {
            System.out.print("Agency name: ");
            if (!sc.hasNextLine()) return;
            n = sc.nextLine().trim();
            if (!n.isEmpty()) break;
            System.out.println("Name cannot be empty.");
        }

        final String name = n;

        int orderCounter = 1;

        try (Connection conn = Common.openConnection()) {
            Channel ch = conn.createChannel();
            Common.declareTopology(ch);

            String inbox = "agency." + name;
            ch.queueDeclare(inbox, true, false, false, null);
            ch.queueBind(inbox, Common.EXCHANGE, Common.confirmationKey(name));
            ch.queueBind(inbox, Common.EXCHANGE, Common.ADMIN_AGENCIES);
            ch.queueBind(inbox, Common.EXCHANGE, Common.ADMIN_ALL);

            DeliverCallback onMsg = (_, delivery) -> {
                String key = delivery.getEnvelope().getRoutingKey();
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                if (key.startsWith("admin.")) {
                    System.out.println("[ADMIN -> " + name + "] " + body);
                } else {
                    System.out.println("[CONFIRMATION -> " + name + "] " + body);
                }
            };
            ch.basicConsume(inbox, true, onMsg, _ -> {});

            System.out.println("Agency '" + name + "' running. Commands:");
            System.out.println("  p|persons  - order person transport");
            System.out.println("  c|cargo    - order cargo transport");
            System.out.println("  s|sat      - order satellite placement");
            System.out.println("  q|quit     - exit");

            while (sc.hasNextLine()) {
                String line = Common.normalize(sc.nextLine());
                if (line.isEmpty()) continue;
                if (line.startsWith("q")) break;

                String svc = Common.resolveService(line);
                if (svc == null) {
                    System.out.println("Unknown command");
                    continue;
                }

                int id = orderCounter++;
                String body = "agency=" + name + "; order=" + id + "; type=" + svc;

                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .deliveryMode(2)
                        .replyTo(name)
                        .messageId(name + "#" + id)
                        .build();

                ch.basicPublish(Common.EXCHANGE, Common.orderKey(svc), props, body.getBytes(StandardCharsets.UTF_8));
                System.out.println("[ORDER <- " + name + "] sent: " + body);
            }
        }
    }
}
