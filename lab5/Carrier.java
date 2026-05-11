package space;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class Carrier {

    void main() throws Exception {
        Scanner sc = new Scanner(System.in);

        String n;
        while (true) {
            System.out.print("Carrier name: ");
            if (!sc.hasNextLine()) return;
            n = sc.nextLine().trim();
            if (!n.isEmpty()) break;
            System.out.println("Name cannot be empty.");
        }
        final String name = n;

        String s1, s2;
        while (true) {
            System.out.println("Available services: persons | cargo | satellite");
            System.out.print("First service:  ");
            if (!sc.hasNextLine()) return;
            s1 = Common.resolveService(sc.nextLine());
            System.out.print("Second service: ");
            if (!sc.hasNextLine()) return;
            s2 = Common.resolveService(sc.nextLine());
            if (s1 != null && s2 != null && !s1.equals(s2)) break;
            System.out.println("Carrier must declare 2 distinct valid services. Try again.");
        }
        Set<String> services = new HashSet<>(Arrays.asList(s1, s2));

        try (Connection conn = Common.openConnection()) {
            Channel ch = conn.createChannel();
            Common.declareTopology(ch);
            ch.basicQos(1);

            DeliverCallback orderHandler = (_, delivery) -> {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                String routingKey = delivery.getEnvelope().getRoutingKey();
                String agency = delivery.getProperties().getReplyTo();
                String orderId = delivery.getProperties().getMessageId();

                System.out.println("[CARRIER " + name + "] handling " + routingKey + " :: " + body);

                String reply = "carrier=" + name + " completed " + orderId + " (" + routingKey + ")";
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .messageId(orderId)
                        .build();
                ch.basicPublish(Common.EXCHANGE, Common.confirmationKey(agency), props, reply.getBytes(StandardCharsets.UTF_8));
                ch.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                System.out.println("[CARRIER " + name + "] confirmed " + agency);
            };

            for (String s : services) {
                ch.basicConsume(Common.orderQueue(s), false, orderHandler, _ -> {});
            }

            String adminInbox = "carrier." + name + ".admin";
            ch.queueDeclare(adminInbox, true, false, false, null);
            ch.queueBind(adminInbox, Common.EXCHANGE, Common.ADMIN_CARRIERS);
            ch.queueBind(adminInbox, Common.EXCHANGE, Common.ADMIN_ALL);
            ch.basicConsume(adminInbox, true, (_, delivery) -> {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[ADMIN -> " + name + "] " + body);
            }, _ -> {});

            System.out.println("Carrier '" + name + "' online. Services: " + services);
            System.out.println("Type 'q' to quit.");

            while (sc.hasNextLine()) {
                if (sc.nextLine().trim().equalsIgnoreCase("q")) break;
            }
        }
    }
}
