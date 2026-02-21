package matlabmaster.multiplayer.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import com.fs.starfarer.api.Global;
import matlabmaster.multiplayer.MultiplayerLog;
import matlabmaster.multiplayer.UserError;
import matlabmaster.multiplayer.server.Server;
import matlabmaster.multiplayer.client.Client;

public class UI extends JFrame {
    private JTextArea logArea;
    private JButton actionButton; // Utilisé pour Join
    private JButton hostDedicatedButton;
    private JButton hostCurrentButton;

    private JTextField ipField;
    private JTextField portField;
    private JComboBox<String> modeSelector;
    private JLabel serverTimeLabel;

    private final Server server;
    private final Client client;
    private boolean isRunning = false;


    public UI(Server serverInstance, Client clientInstance) {
        this.server = serverInstance;
        this.client = clientInstance;

        // Existing client listener
        client.addListener(new Client.ClientListener() {
            @Override
            public void onDisconnected() {
                MultiplayerLog.log().debug("UI onDisconnected() callback triggered!");
                try {
                    isRunning = false;
                    updateButtonStyle();
                    MultiplayerLog.log().debug("updateButtonStyle() completed");
                } catch (Exception e) {
                    MultiplayerLog.log().error("Exception in onDisconnected: " + e.getMessage(), e);
                }
            }
            @Override
            public void onMessageReceived(String msg) {}
        });

        // ADD THIS: Server listener
        server.setListener(new Server.ServerListener() {
            @Override
            public void onServerStopped() {
                isRunning = false;
                updateButtonStyle();
            }
        });

        setTitle("Starsector Multiplayer");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(750, 550);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(40, 42, 54));

        // --- BARRE DE CONFIGURATION ---
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modeSelector = new JComboBox<>(new String[]{"HOST MODE", "JOIN MODE"});
        ipField = new JTextField("127.0.0.1", 10);
        ipField.setEnabled(false);
        portField = new JTextField("20603", 6);

        modeSelector.addActionListener(e -> toggleMode());

        configPanel.add(new JLabel("Mode: ")); configPanel.add(modeSelector);
        configPanel.add(new JLabel(" IP: ")); configPanel.add(ipField);
        configPanel.add(new JLabel(" Port: ")); configPanel.add(portField);
        
        // --- SERVER TIME CLOCK ---
        serverTimeLabel = new JLabel("Server Time: c--- -- --");
        serverTimeLabel.setForeground(Color.BLACK);
        serverTimeLabel.setFont(new Font(serverTimeLabel.getFont().getName(), Font.BOLD, 12));
        configPanel.add(new JLabel(" | "));
        configPanel.add(serverTimeLabel);

        // --- CONSOLE ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(20, 20, 25));
        logArea.setForeground(new Color(139, 233, 253));
        JScrollPane scroll = new JScrollPane(logArea);

        // --- ZONE DES BOUTONS (SUD) ---
        JPanel buttonPanel = new JPanel(new GridLayout(1, 0, 10, 0));
        buttonPanel.setOpaque(false);

        hostDedicatedButton = new JButton("HOST AS DEDICATED");
        hostCurrentButton = new JButton("HOST CURRENT GAME");
        // Remplace tes couleurs par des teintes un peu moins saturées qui font ressortir le blanc
        actionButton = new JButton("CONNECT"); // Pour le mode Join
        actionButton.setVisible(false);

        // Actions
        hostDedicatedButton.addActionListener(e -> startHost(false));
        hostCurrentButton.addActionListener(e -> startHost(true));
        actionButton.addActionListener(e -> startJoin());

        buttonPanel.add(hostDedicatedButton);
        buttonPanel.add(hostCurrentButton);
        buttonPanel.add(actionButton);

        mainPanel.add(configPanel, BorderLayout.NORTH);
        mainPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        updateButtonStyle();
        MultiplayerLog.setUILogSink(this::updateLogArea);
    }

    private void toggleMode() {
        boolean isJoin = modeSelector.getSelectedItem().equals("JOIN MODE");
        ipField.setEnabled(isJoin);
        hostDedicatedButton.setVisible(!isJoin);
        hostCurrentButton.setVisible(!isJoin);
        actionButton.setVisible(isJoin);
    }

    private void startHost(boolean asCurrentGame) {
        try{
            if (!isRunning) {
                int port = parsePort();
                if (port <= 0) return;
                server.setPort(port);
                server.start();
                isRunning = true;
                final int connectPort = port;
                MultiplayerLog.log().info("SERVER STARTED AS " + (asCurrentGame ? "HOSTED GAME" : "DEDICATED"));
                if (asCurrentGame) {
                    // On lance la connexion client dans un thread séparé avec un petit délai
                    new Thread(() -> {
                        try {
                            Thread.sleep(200); // Pause de 200ms pour laisser le port s'ouvrir
                            client.isSelfHosted = true;
                            client.connect("127.0.0.1", connectPort);
                            updateButtonStyle();
                        } catch (Exception ex) {
                            MultiplayerLog.log().error("FAILURE TO AUTO CONNECT : " + ex.getMessage());
                            client.isSelfHosted = false;
                            // Si l'auto-connexion échoue, on peut choisir d'arrêter le serveur ou pas
                        }
                    }).start();
                }
                updateButtonStyle();
            } else {
                stopAll();
            }
        }catch (UserError e){
            MultiplayerLog.log().warn(e.getMessage());
        }catch (Exception e){
            MultiplayerLog.log().error("server crashed", e);
        }

    }

    private void startJoin() {
        if (!isRunning) {
            int port = parsePort();
            if (port <= 0) return;
            final int connectPort = port;
            new Thread(() -> {
                try {
                    client.connect(ipField.getText(), connectPort);
                    isRunning = true;
                    updateButtonStyle();
                }catch (UserError e){
                    MultiplayerLog.log().warn(e.getMessage());
                }
                catch (Exception ex) {
                    MultiplayerLog.log().error("UNABLE TO CONNECT. " + ex.toString(), ex);
                    client.disconnect();
                }
            }).start();
        } else {
            stopAll();
        }

    }

    private void stopAll() {
        if (server.isRunning) server.stop();
        if (client.isConnected()) client.disconnect();
        isRunning = false;
        updateButtonStyle();
    }

    private void updateButtonStyle() {
        SwingUtilities.invokeLater(() -> {
            boolean serverUp = server.isRunning;
            boolean clientUp = client.isConnected();

            if (!serverUp && !clientUp) {
                // Tout est arrêté
                hostDedicatedButton.setText("HOST AS DEDICATED");
                hostDedicatedButton.setBackground(new Color(80, 250, 123));
                hostCurrentButton.setText("HOST CURRENT GAME");
                hostCurrentButton.setBackground(new Color(80, 250, 123));
                actionButton.setText("CONNECT");
                actionButton.setBackground(new Color(80, 250, 123));
                modeSelector.setEnabled(true);
                portField.setEnabled(true);
                isRunning = false;
            } else {
                // Quelque chose tourne
                hostDedicatedButton.setText("STOP SERVER");
                hostDedicatedButton.setBackground(new Color(255, 85, 85));
                hostCurrentButton.setText("STOP HOST");
                hostCurrentButton.setBackground(new Color(255, 85, 85));
                actionButton.setText("DISCONNECT");
                actionButton.setBackground(new Color(255, 85, 85));
                modeSelector.setEnabled(false);
                portField.setEnabled(false);
                isRunning = true;
            }
        });
    }

    private void updateLogArea(String t) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(t);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /** @return the port (1-65535) or 0 if invalid */
    private int parsePort() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                MultiplayerLog.log().error("Port must be between 1 and 65535.");
                return 0;
            }
            return port;
        } catch (NumberFormatException e) {
            MultiplayerLog.log().error("Invalid port: " + portField.getText());
            return 0;
        }
    }

    public void showUI() { SwingUtilities.invokeLater(() -> setVisible(true)); }

    /**
     * Sets the server time clock display.
     * Format: cYYY MM DD (e.g., c207 12 18)
     * @param year The year (e.g., 207 for cycle 207)
     * @param month The month (1-12)
     * @param day The day (1-31)
     */
    public void setServerTime(int year, int month, int day) {
        SwingUtilities.invokeLater(() -> {
            String formattedTime = String.format("c%03d %02d %02d", year, month, day);
            serverTimeLabel.setText("Server Time: " + formattedTime);
        });
    }
}