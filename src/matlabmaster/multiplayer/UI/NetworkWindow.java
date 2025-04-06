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
        setSize(400, 300);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // IP Address field
        JLabel ipLabel = new JLabel("IP Address:Port");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(ipLabel, gbc);

        ipField = new JTextField("127.0.0.1:4444", 20); // 20 columns wide
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2; // Span across two columns
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // Allow it to expand horizontally
        mainPanel.add(ipField, gbc);

        // Buttons panel
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

        // Status field
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

        // Message field
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

        // Button listeners
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
                } else {
                    MultiplayerModPlugin.startClient(ip, port);
                    messageField.append("Connected to " + ipPort + "\n");
                }
                isConnected = true;
                connectButton.setText("Disconnect");
                statusField.setText("Connected - " + MultiplayerModPlugin.getMode() + " mode");
                ipField.setEnabled(false);
                modeButton.setEnabled(false);
            } catch (Exception e) {
                messageField.append("Connection failed: " + e.getMessage() + "\n");
            }
        } else {
            try {
                MultiplayerModPlugin.stopNetwork();
                isConnected = false;
                connectButton.setText("Connect");
                statusField.setText("Disconnected");
                messageField.append("Disconnected\n");
                ipField.setEnabled(true);
                modeButton.setEnabled(true);
            } catch (Exception e) {
                messageField.append("Disconnection failed: " + e.getMessage() + "\n");
            }
        }
    }

    private void switchMode() {
        if (isConnected) return; // Can't switch while connected
        String newMode = MultiplayerModPlugin.getMode().equals("server") ? "client" : "server";
        MultiplayerModPlugin.setMode(newMode);
        modeButton.setText("Mode: " + newMode.substring(0, 1).toUpperCase() + newMode.substring(1));
        messageField.append("Switched to " + newMode + " mode\n");
        statusField.setText("Disconnected - " + newMode + " mode");
    }
}