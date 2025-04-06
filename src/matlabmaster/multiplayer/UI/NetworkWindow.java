package matlabmaster.multiplayer.UI;

import matlabmaster.multiplayer.MultiplayerModPlugin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class NetworkWindow extends JFrame {
    private JTextField ipField;
    private JButton connectButton;
    private JTextField statusField;
    private JTextArea messageField;
    private JButton modeButton;
    private boolean isConnected = false;

    public NetworkWindow() {
        setTitle("Multiplayer Network");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

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

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        connectButton = new JButton("Connect");
        modeButton = new JButton("Mode: Server");

        buttonPanel.add(connectButton);
        buttonPanel.add(modeButton);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(buttonPanel, gbc);

        JLabel statusLabel = new JLabel("Status");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(statusLabel, gbc);

        statusField = new JTextField("Disconnected", 20);
        statusField.setEditable(false);
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(statusField, gbc);

        JLabel messageLabel = new JLabel("Messages");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(messageLabel, gbc);

        messageField = new JTextArea(10, 30);
        messageField.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageField);
        gbc.gridx = 1;
        gbc.gridy = 3;
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
                    MultiplayerModPlugin.startServer(port);
                    messageField.append("Server started on port " + port + "\n");
                    updateStatus(true, "Connected - server mode");
                } else {
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

    public JTextArea getMessageField() {
        return messageField;
    }

    // New method to update connection status
    public void updateStatus(boolean connected, String statusMessage) {
        isConnected = connected;
        statusField.setText(statusMessage);
        connectButton.setText(connected ? "Disconnect" : "Connect");
        ipField.setEnabled(!connected);
        modeButton.setEnabled(!connected);
    }
}