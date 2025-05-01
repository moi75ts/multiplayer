package matlabmaster.multiplayer.UI;

import matlabmaster.multiplayer.MultiplayerModPlugin;
import matlabmaster.multiplayer.User;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;

public class NetworkWindow extends JFrame {
    private JTextField ipField;
    private JButton connectButton;
    private JTextField statusField;
    private JTextArea messageField;
    private JButton modeButton;
    private JTextField serverSeedField;  // New field for server seed
    private JButton copySeedButton;      // New copy button
    private boolean isConnected = false;

    public NetworkWindow() {
        setTitle("Multiplayer Network");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        // Add a WindowListener to handle the close attempt
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Optionally show a message or do nothing
                messageField.append("Closing the window is disabled.\n");
                // You can add other logic here, e.g., prompt the user or log the attempt
            }
        });
        setSize(800, 600);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // IP Address Section
        JLabel ipLabel = new JLabel("IP Address:Port");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(ipLabel, gbc);

        ipField = new JTextField("127.0.0.1:4444", 20);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(ipField, gbc);

        // User ID Section
        JLabel userIdLabel = new JLabel("User ID:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(userIdLabel, gbc);

        JTextField userIdField = new JTextField(User.getUserId(), 20);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(userIdField, gbc);

        JButton updateIdButton = new JButton("Update");
        updateIdButton.addActionListener(e -> {
            String newId = userIdField.getText().trim();
            if (!newId.isEmpty()) {
                User.setUserId(newId);
                messageField.append("User ID updated to: " + newId + "\n");
            } else {
                messageField.append("User ID cannot be empty\n");
            }
        });
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(updateIdButton, gbc);

        // Server Seed Section
        JLabel seedLabel = new JLabel("Server Seed:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(seedLabel, gbc);

        serverSeedField = new JTextField("Not available", 20);
        serverSeedField.setEditable(false);
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(serverSeedField, gbc);

        copySeedButton = new JButton("Copy");
        copySeedButton.addActionListener(e -> copySeedToClipboard());
        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(copySeedButton, gbc);

        // Buttons Section
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        connectButton = new JButton("Connect");
        modeButton = new JButton("Mode: Server");

        buttonPanel.add(connectButton);
        buttonPanel.add(modeButton);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(buttonPanel, gbc);

        // Status Section
        JLabel statusLabel = new JLabel("Status");
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(statusLabel, gbc);

        statusField = new JTextField("Disconnected", 20);
        statusField.setEditable(false);
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(statusField, gbc);

        // Messages Section
        JLabel messageLabel = new JLabel("Messages");
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(messageLabel, gbc);

        messageField = new JTextArea(10, 30);
        messageField.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageField);
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        mainPanel.add(scrollPane, gbc);

        add(mainPanel, BorderLayout.CENTER);
        setLocationRelativeTo(null);

        connectButton.addActionListener(e -> toggleConnection());
        modeButton.addActionListener(e -> switchMode());
    }

    private void toggleConnection() {
        if (!isConnected) {
            String ipPort = ipField.getText().trim();
            if (ipPort.isEmpty() || !ipPort.contains(":")) {
                messageField.append("Please enter a valid IP:Port\n");
                return;
            }

            String[] parts = ipPort.split(":");
            String ip = parts[0];
            int port;
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                messageField.append("Invalid port number\n");
                return;
            }

            try {
                if (MultiplayerModPlugin.getMode().equals("server")) {
                    User.setUserIdWithoutUpdatingTheFile("server");
                    MultiplayerModPlugin.startServer(port);
                    messageField.append("Server started on port " + port + "\n");
                    updateStatus(true, "Connected - server mode");
                } else {
                    User.getUserIdFromFile();
                    MultiplayerModPlugin.startClient(ip, port);
                    if (MultiplayerModPlugin.getMessageSender() != null && MultiplayerModPlugin.getMessageSender().isActive()) {
                        messageField.append("Connected to " + ipPort + "\n");
                        updateStatus(true, "Connected - client mode");
                    } else {
                        throw new Exception("Failed to connect to server");
                    }
                }
            } catch (Exception e) {
                messageField.append("Connection failed: " + e.getMessage() + "\n");
                updateStatus(false, "Disconnected");
            }
        } else {
            try {
                MultiplayerModPlugin.stopNetwork();
                updateStatus(false, "Disconnected");
                messageField.append("Disconnected\n");
                setServerSeed("Not available");  // Reset seed field when disconnected
            } catch (Exception e) {
                messageField.append("Disconnection failed: " + e.getMessage() + "\n");
                updateStatus(false, "Disconnected with error: " + e.getMessage());
            }
        }
    }

    private void switchMode() {
        if (isConnected) return;
        String newMode = MultiplayerModPlugin.getMode().equals("server") ? "client" : "server";
        MultiplayerModPlugin.setMode(newMode);
        modeButton.setText("Mode: " + newMode.substring(0, 1).toUpperCase() + newMode.substring(1));
        messageField.append("Switched to " + newMode + " mode\n");
        statusField.setText("Disconnected - " + newMode + " mode");
    }

    private void copySeedToClipboard() {
        String seed = serverSeedField.getText();
        if (seed != null && !seed.isEmpty() && !"Not available".equals(seed)) {
            StringSelection stringSelection = new StringSelection(seed);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            messageField.append("Seed copied to clipboard: " + seed + "\n");
        } else {
            messageField.append("No valid seed to copy\n");
        }
    }

    public JTextArea getMessageField() {
        return messageField;
    }

    public void updateStatus(boolean connected, String statusMessage) {
        isConnected = connected;
        statusField.setText(statusMessage);
        connectButton.setText(connected ? "Disconnect" : "Connect");
        ipField.setEnabled(!connected);
        modeButton.setEnabled(!connected);
    }

    // New method to update server seed
    public void setServerSeed(String seed) {
        SwingUtilities.invokeLater(() -> {
            serverSeedField.setText(seed != null ? seed : "Not available");
        });
    }
}