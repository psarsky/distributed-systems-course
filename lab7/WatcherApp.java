package watcher;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;


public class WatcherApp implements Watcher {
    private static final String NODE = "/a";
    private static final int SESSION_TIMEOUT_MS = 5000;

    private final ZooKeeper zk;
    private final String[] command;
    private final Dashboard dashboard;
    private final CountDownLatch connected = new CountDownLatch(1);

    private Process externalProcess;

    public WatcherApp(String connectString, String[] command) throws IOException, InterruptedException, KeeperException {
        this.command = command;
        this.dashboard = new Dashboard(this::buildTree);
        this.zk = new ZooKeeper(connectString, SESSION_TIMEOUT_MS, this);
        connected.await();
        initialSetup();
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getType() == Event.EventType.None) {
            switch (event.getState()) {
                case SyncConnected -> {
                    dashboard.setConnected(true);
                    connected.countDown();
                }
                case Disconnected -> dashboard.setConnected(false);
                case Expired -> {
                    dashboard.setConnected(false);
                    System.out.println("ZooKeeper session expired.");
                }
                default -> {}
            }
            return;
        }

        if (!NODE.equals(event.getPath())) {
            return;
        }

        try {
            switch (event.getType()) {
                case NodeCreated -> onCreated();
                case NodeDeleted -> onDeleted();
                case NodeChildrenChanged -> armChildrenWatch();
                case NodeDataChanged -> zk.exists(NODE, this);
                default -> {}
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void initialSetup() throws KeeperException, InterruptedException {
        Stat stat = zk.exists(NODE, this);
        if (stat != null) {
            startExternalApp();
            dashboard.setExists(true);
            armChildrenWatch();
        } else {
            dashboard.setExists(false);
        }
        System.out.println("Monitoring znode " + NODE + ". Commands: 'tree', 'quit/exit'.");
    }

    private void onCreated() throws KeeperException, InterruptedException {
        System.out.println(NODE + " was created.");
        startExternalApp();
        dashboard.setExists(true);
        armChildrenWatch();
        zk.exists(NODE, this);
    }

    private void onDeleted() throws KeeperException, InterruptedException {
        System.out.println(NODE + " was deleted.");
        stopExternalApp();
        dashboard.setExists(false);
        dashboard.setCount(0);
        zk.exists(NODE, this);
    }

    private void armChildrenWatch() throws InterruptedException {
        try {
            List<String> children = zk.getChildren(NODE, this);
            dashboard.setCount(children.size());
            System.out.println("Number of children of " + NODE + ": " + children.size());
        } catch (KeeperException.NoNodeException e) {
            dashboard.setCount(0);
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    private synchronized void startExternalApp() {
        if (externalProcess != null && externalProcess.isAlive()) {
            return;
        }
        try {
            externalProcess = new ProcessBuilder(command).inheritIO().start();
            System.out.println("External application started: " + String.join(" ", command));
        } catch (IOException e) {
            System.out.println("Failed to start external application: " + e.getMessage());
        }
    }

    private synchronized void stopExternalApp() {
        if (externalProcess != null) {
            externalProcess.descendants().forEach(ProcessHandle::destroy);
            externalProcess.destroy();
            try {
                if (!externalProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    externalProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                externalProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            externalProcess = null;
            System.out.println("External application stopped.");
        }
    }

    public String buildTree() {
        StringBuilder sb = new StringBuilder();
        try {
            if (zk.exists(NODE, false) == null) {
                sb.append("Znode ").append(NODE).append(" does not exist.");
            } else {
                appendTree(NODE, "", sb);
            }
        } catch (KeeperException | InterruptedException e) {
            sb.append("Error reading tree: ").append(e.getMessage());
        }
        return sb.toString();
    }

    private void appendTree(String path, String indent, StringBuilder sb)
            throws KeeperException, InterruptedException {
        String name = path.substring(path.lastIndexOf('/') + 1);
        byte[] data = zk.getData(path, false, null);
        sb.append(indent).append(name.isEmpty() ? "/" : name);
        if (data != null && data.length > 0) {
            sb.append("  = ").append(new String(data));
        }
        sb.append('\n');

        List<String> children = zk.getChildren(path, false);
        Collections.sort(children);
        for (String child : children) {
            appendTree(path + "/" + child, indent + "   ", sb);
        }
    }

    public void close() throws InterruptedException {
        stopExternalApp();
        zk.close();
    }

    static void main(String[] args) throws Exception {
        String connectString = args[0];
        String[] command = Arrays.copyOfRange(args, 1, args.length);

        WatcherApp app = new WatcherApp(connectString, command);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { app.close(); } catch (InterruptedException ignored) { }
        }));

        Thread console = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    switch (line) {
                        case "tree" -> System.out.println(app.buildTree());
                        case "quit", "exit" -> {
                            app.close();
                            System.exit(0);
                        }
                        default -> System.out.println("Unknown command: " + line);
                    }
                }
            } catch (IOException | InterruptedException ignored) {
            }
        });
        console.setDaemon(true);
        console.start();
    }

    static class Dashboard {
        private final Supplier<String> treeSupplier;
        private final JLabel connLabel = new JLabel("ZooKeeper: connecting...");
        private final JLabel existsLabel = new JLabel("Znode: ?");
        private final JLabel countLabel = new JLabel("Number of children: 0");

        Dashboard(Supplier<String> treeSupplier) {
            this.treeSupplier = treeSupplier;
            SwingUtilities.invokeLater(this::build);
        }

        private void build() {
            JFrame frame = new JFrame("Znode monitor");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD, 22f));

            JPanel labels = new JPanel(new GridLayout(3, 1, 4, 4));
            labels.add(connLabel);
            labels.add(existsLabel);
            labels.add(countLabel);

            JButton treeBtn = new JButton("Show tree");
            treeBtn.addActionListener(_ -> showTree());

            JPanel root = new JPanel(new BorderLayout(8, 8));
            root.add(labels, BorderLayout.CENTER);
            root.add(treeBtn, BorderLayout.SOUTH);
            root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            frame.setContentPane(root);
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        }

        void setConnected(boolean c) {
            SwingUtilities.invokeLater(() ->
                    connLabel.setText("ZooKeeper: " + (c ? "connected" : "disconnected")));
        }

        void setExists(boolean exists) {
            SwingUtilities.invokeLater(() ->
                    existsLabel.setText("Znode: " + (exists ? "exists" : "missing")));
        }

        void setCount(int n) {
            SwingUtilities.invokeLater(() ->
                    countLabel.setText("Number of children: " + n));
        }

        private void showTree() {
            String tree = treeSupplier.get();
            JTextArea area = new JTextArea(tree, 18, 40);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            JFrame f = new JFrame("Tree");
            f.add(new JScrollPane(area));
            f.pack();
            f.setLocationByPlatform(true);
            f.setVisible(true);
        }
    }
}
