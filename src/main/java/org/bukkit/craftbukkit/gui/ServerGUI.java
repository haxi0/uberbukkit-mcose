package org.bukkit.craftbukkit.gui;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.EntityPlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.util.config.Configuration;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server GUI for Uberbukkit/MCOSE server.
 * Provides a visual interface for server management.
 */
public class ServerGUI extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Pattern CONSOLE_FORMATTED_LINE_PATTERN =
        Pattern.compile("^\\d{2}:\\d{2}:\\d{2} \\[([A-Z]+)\\] (.*)$");
    
    // Colors - Dark theme
    private static final Color BG_DARK = new Color(30, 30, 30);
    private static final Color BG_PANEL = new Color(40, 40, 40);
    private static final Color BG_INPUT = new Color(50, 50, 50);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color ACCENT_COLOR = new Color(86, 156, 214);
    private static final Color ERROR_COLOR = new Color(244, 71, 71);
    private static final Color CHAT_COLOR = new Color(78, 201, 176);
    private static final Color COMMAND_COLOR = new Color(220, 220, 170);
    private static final Color SUCCESS_COLOR = new Color(96, 186, 96);
    private static final Color MUTE_COLOR = new Color(255, 165, 0); // Orange
    
    // Mojang API endpoints (same as game client SkinManager)
    private static final String[] UUID_ENDPOINTS = {
        "https://api.minecraftservices.com/minecraft/profile/lookup/name/",
        "https://api.mojang.com/users/profiles/minecraft/"
    };
    private static final String SESSION_SERVER_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final int FACE_SIZE = 24;
    
    // Cache for player face icons
    private final Map<String, ImageIcon> playerFaceCache = new ConcurrentHashMap<>();
    private final Set<String> faceFetchInProgress = Collections.synchronizedSet(new HashSet<>());
    
    // Components
    private JTabbedPane tabbedPane;
    private JTextPane consolePane;
    private StyledDocument consoleDoc;
    private JTextPane chatLogPane;
    private StyledDocument chatLogDoc;
    private JTextField commandInput;
    private JComboBox<String> filterCombo;
    private JList<String> playerList;
    private DefaultListModel<String> playerListModel;
    private JList<String> bannedList;
    private DefaultListModel<String> bannedListModel;
    private JList<String> opsList;
    private DefaultListModel<String> opsListModel;
    private JList<String> whitelistList;
    private DefaultListModel<String> whitelistListModel;
    private JLabel ramUsageLabel;
    private JProgressBar ramProgressBar;
    private JSlider ramSlider;
    private JLabel ramSliderLabel;
    private JLabel serverStatusLabel;
    private JLabel cpuUsageLabel;
    private JProgressBar cpuProgressBar;
    private javax.swing.Timer updateTimer;
    private JButton startButton;
    
    // Server.properties UI components
    private JPanel propertiesPanel;
    private JTextField serverIpField;
    private JTextField serverPortField;
    private JTextField levelNameField;
    private JTextField levelSeedField;
    private JTextField maxPlayersField;
    private JTextField viewDistanceField;
    private JTextField motdField;
    private JSlider spawnProtectionSlider;
    private JLabel spawnProtectionSliderLabel;
    private JComboBox<String> levelTypeCombo;
    private JComboBox<String> gamemodeCombo;
    private JComboBox<String> difficultyCombo;
    private JCheckBox onlineModeCheck;
    private JCheckBox spawnAnimalsCheck;
    private JCheckBox spawnMonstersCheck;
    private JCheckBox pvpCheck;
    private JCheckBox allowFlightCheck;
    private JCheckBox allowNetherCheck;
    private JCheckBox whiteListCheck;
    private JCheckBox voiceChatCheck;
    private JTextField voiceChatPortField;
    private JButton savePropertiesButton;
    
    // Threading configuration UI components
    private JCheckBox asyncChunkGenCheck;
    private JSpinner chunkGenThreadsSpinner;
    private JCheckBox asyncLightingCheck;
    private JSpinner lightingThreadsSpinner;
    private JCheckBox asyncEntityCheck;
    private JSpinner entityThreadsSpinner;
    private JSpinner chunkCompressionThreadsSpinner;
    private JSpinner chunkCompressionQueueCapacitySpinner;
    private JSpinner chunkCompressionHighWatermarkSpinner;
    private JSpinner chunkCompressionLowWatermarkSpinner;
    private JButton saveThreadingButton;

    // Startup readiness / tick catchup UI
    private JCheckBox startupReadinessEnabledCheck;
    private JCheckBox startupHoldLoginCheck;
    private JCheckBox tickCatchupEnabledCheck;
    private JSpinner startupOverworldRadiusSpinner;
    private JSpinner startupNetherRadiusSpinner;
    private JSpinner startupJoinerExtraRadiusSpinner;
    private JSpinner startupMaxHoldSecondsSpinner;
    private JSpinner tickCatchupMaxBacklogSpinner;
    private JButton saveStartupReadinessButton;
    
    // Log storage for filtering
    private final List<LogEntry> allLogs = Collections.synchronizedList(new ArrayList<LogEntry>());
    private final List<LogEntry> chatOnlyLogs = Collections.synchronizedList(new ArrayList<LogEntry>());
    private String currentFilter = "All";
    
    // Server reference
    private MinecraftServer server;
    private Thread serverThread;
    private String[] serverArgs;
    private boolean serverStarted = false;
    private Handler guiLogHandler; // Keep reference to our log handler
    
    public ServerGUI(String[] args) {
        this.serverArgs = args;
        // Set GUI mode so server doesn't call System.exit() when stopped
        net.minecraft.server.MinecraftServer.guiMode = true;
        initializeUI();
        setupLogging();
        startUpdateTimer();
    }
    
    private void initializeUI() {
        setTitle("Minecraft Oldschool Edition Server");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 700);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        
        // Use cross-platform look and feel for consistent dark theme
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore
        }
        
        // Override UI colors for complete dark theme
        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("Panel.foreground", TEXT_COLOR);
        UIManager.put("Label.foreground", TEXT_COLOR);
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Button.background", BG_INPUT);
        UIManager.put("Button.select", ACCENT_COLOR);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("TextField.background", BG_INPUT);
        UIManager.put("TextField.foreground", TEXT_COLOR);
        UIManager.put("TextField.caretForeground", TEXT_COLOR);
        UIManager.put("TextPane.background", BG_DARK);
        UIManager.put("TextPane.foreground", TEXT_COLOR);
        UIManager.put("TextPane.caretForeground", TEXT_COLOR);
        UIManager.put("ComboBox.background", BG_INPUT);
        UIManager.put("ComboBox.foreground", TEXT_COLOR);
        UIManager.put("ComboBox.selectionBackground", ACCENT_COLOR);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("List.background", BG_DARK);
        UIManager.put("List.foreground", TEXT_COLOR);
        UIManager.put("List.selectionBackground", ACCENT_COLOR);
        UIManager.put("List.selectionForeground", Color.WHITE);
        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("ScrollBar.background", BG_DARK);
        UIManager.put("ScrollBar.thumb", BG_INPUT);
        UIManager.put("ScrollBar.track", BG_DARK);
        UIManager.put("TabbedPane.background", BG_PANEL);
        UIManager.put("TabbedPane.foreground", TEXT_COLOR);
        UIManager.put("TabbedPane.selected", BG_PANEL);
        UIManager.put("TabbedPane.contentAreaColor", BG_PANEL);
        UIManager.put("TabbedPane.light", BG_INPUT);
        UIManager.put("TabbedPane.shadow", BG_DARK);
        UIManager.put("TabbedPane.darkShadow", BG_DARK);
        UIManager.put("TabbedPane.highlight", BG_INPUT);
        UIManager.put("TabbedPane.tabAreaBackground", BG_DARK);
        UIManager.put("OptionPane.background", BG_DARK);
        UIManager.put("OptionPane.foreground", TEXT_COLOR);
        UIManager.put("OptionPane.messageForeground", TEXT_COLOR);
        UIManager.put("Spinner.background", BG_INPUT);
        UIManager.put("Spinner.foreground", TEXT_COLOR);
        UIManager.put("TitledBorder.titleColor", TEXT_COLOR);
        UIManager.put("ProgressBar.foreground", ACCENT_COLOR);
        UIManager.put("ProgressBar.background", BG_INPUT);
        UIManager.put("ProgressBar.selectionForeground", TEXT_COLOR);
        UIManager.put("ProgressBar.selectionBackground", TEXT_COLOR);
        
        // Add window listener for proper shutdown
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmAndShutdown();
            }
        });
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBackground(BG_DARK);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header with server status
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(BG_PANEL);
        tabbedPane.setForeground(TEXT_COLOR);
        
        // Console tab
        tabbedPane.addTab("Console", createConsolePanel());

        // Chat-only tab
        tabbedPane.addTab("Chat Logs", createChatLogsPanel());
        
        // Players tab
        tabbedPane.addTab("Players", createPlayersPanel());
        
        // Banned Players tab
        tabbedPane.addTab("Banned", createBannedPanel());
        
        // OP Players tab
        tabbedPane.addTab("Operators", createOpsPanel());
        
        // Whitelist tab
        tabbedPane.addTab("Whitelist", createWhitelistPanel());
        
        // Options tab
        tabbedPane.addTab("Options", createOptionsPanel());
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        setContentPane(mainPanel);
        
        // Set icon - try multiple icon file names
        try {
            File iconFile = new File("minecraft.ico");
            if (!iconFile.exists()) {
                iconFile = new File("icon.png");
            }
            if (!iconFile.exists()) {
                iconFile = new File("server-icon.png");
            }
            if (iconFile.exists()) {
                // Use Toolkit for broader format support (including .ico on Windows)
                Image iconImage = Toolkit.getDefaultToolkit().getImage(iconFile.getAbsolutePath());
                if (iconImage != null) {
                    setIconImage(iconImage);
                }
            }
        } catch (Exception e) {
            // Ignore icon loading errors
        }
    }
    
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(BG_DARK);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));
        
        // Left side - Server status
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        statusPanel.setBackground(BG_DARK);
        
        serverStatusLabel = new JLabel("● Server Stopped");
        serverStatusLabel.setForeground(ERROR_COLOR);
        serverStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusPanel.add(serverStatusLabel);
        
        header.add(statusPanel, BorderLayout.WEST);
        
        // Right side - RAM and CPU usage
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        statsPanel.setBackground(BG_DARK);
        
        // CPU usage
        cpuUsageLabel = new JLabel("CPU: 0%");
        cpuUsageLabel.setForeground(TEXT_COLOR);
        cpuUsageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statsPanel.add(cpuUsageLabel);
        
        cpuProgressBar = new JProgressBar(0, 100);
        cpuProgressBar.setPreferredSize(new Dimension(100, 18));
        cpuProgressBar.setStringPainted(true);
        cpuProgressBar.setForeground(new Color(156, 86, 214));
        cpuProgressBar.setBackground(BG_INPUT);
        statsPanel.add(cpuProgressBar);
        
        // Separator
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 20));
        sep.setForeground(BG_INPUT);
        statsPanel.add(sep);
        
        // RAM usage
        ramUsageLabel = new JLabel("RAM: 0 MB / 0 MB");
        ramUsageLabel.setForeground(TEXT_COLOR);
        ramUsageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statsPanel.add(ramUsageLabel);
        
        ramProgressBar = new JProgressBar(0, 100);
        ramProgressBar.setPreferredSize(new Dimension(100, 18));
        ramProgressBar.setStringPainted(true);
        ramProgressBar.setForeground(ACCENT_COLOR);
        ramProgressBar.setBackground(BG_INPUT);
        statsPanel.add(ramProgressBar);
        
        header.add(statsPanel, BorderLayout.EAST);
        
        // Center - Start/Stop/Restart buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBackground(BG_DARK);
        
        startButton = new JButton("Start Server");
        styleButton(startButton, SUCCESS_COLOR, Color.WHITE);
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        startButton.addActionListener(e -> {
            if (!serverStarted) {
                startServer();
                startButton.setText("Stop Server");
                startButton.setBackground(ERROR_COLOR);
                styleButton(startButton, ERROR_COLOR, Color.WHITE);
            } else {
                stopServer();
                // Don't change button text here - let the server thread's finally block do it
                // This prevents the button from showing "Start" while server is still stopping
                startButton.setText("Stopping...");
                startButton.setEnabled(false);
                // Re-enable after a delay to prevent double-clicking
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {}
                    SwingUtilities.invokeLater(() -> startButton.setEnabled(true));
                }).start();
            }
        });
        buttonPanel.add(startButton);
        
        JButton restartButton = new JButton("Restart");
        styleButton(restartButton, new Color(230, 150, 50), Color.WHITE);
        restartButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        restartButton.addActionListener(e -> {
            if (serverStarted) {
                appendLog("[GUI] Restarting server...", LogType.INFO);
                restartButton.setEnabled(false);
                startButton.setEnabled(false);
                stopServer();
                // Wait for server to actually stop, then restart
                new Thread(() -> {
                    try {
                        // Wait for serverStarted to become false (max 30 seconds)
                        int waitCount = 0;
                        while (serverStarted && waitCount < 60) {
                            Thread.sleep(500);
                            waitCount++;
                        }
                        
                        if (serverStarted) {
                            // Server didn't stop in time
                            SwingUtilities.invokeLater(() -> {
                                appendLog("[GUI] Server did not stop in time for restart", LogType.ERROR);
                                restartButton.setEnabled(true);
                                startButton.setEnabled(true);
                            });
                            return;
                        }
                        
                        // Wait a bit more for cleanup
                        Thread.sleep(1000);
                        
                        SwingUtilities.invokeLater(() -> {
                            startServer();
                            startButton.setText("Stop Server");
                            styleButton(startButton, ERROR_COLOR, Color.WHITE);
                            restartButton.setEnabled(true);
                            startButton.setEnabled(true);
                        });
                    } catch (InterruptedException ex) {
                        SwingUtilities.invokeLater(() -> {
                            restartButton.setEnabled(true);
                            startButton.setEnabled(true);
                        });
                    }
                }).start();
            } else {
                appendLog("[GUI] Server is not running", LogType.INFO);
            }
        });
        buttonPanel.add(restartButton);
        
        header.add(buttonPanel, BorderLayout.CENTER);
        
        return header;
    }
    
    private JPanel createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Filter bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        filterBar.setBackground(BG_PANEL);
        
        JLabel filterLabel = new JLabel("Filter:");
        filterLabel.setForeground(TEXT_COLOR);
        filterBar.add(filterLabel);
        
        filterCombo = new JComboBox<>(new String[]{"All", "Chat", "Errors", "Commands"});
        filterCombo.setBackground(BG_INPUT);
        filterCombo.setForeground(TEXT_COLOR);
        filterCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? ACCENT_COLOR : BG_INPUT);
                setForeground(TEXT_COLOR);
                return this;
            }
        });
        ((JComponent) filterCombo.getEditor().getEditorComponent()).setForeground(TEXT_COLOR);
        filterCombo.addActionListener(e -> {
            currentFilter = (String) filterCombo.getSelectedItem();
            refreshConsole();
        });
        filterBar.add(filterCombo);
        
        JButton clearButton = new JButton("Clear");
        styleButton(clearButton, BG_INPUT, TEXT_COLOR);
        clearButton.addActionListener(e -> {
            allLogs.clear();
            refreshConsole();
        });
        filterBar.add(clearButton);
        
        panel.add(filterBar, BorderLayout.NORTH);
        
        // Console text area
        consolePane = new JTextPane();
        consolePane.setEditable(false);
        consolePane.setBackground(BG_DARK);
        consolePane.setForeground(TEXT_COLOR);
        consolePane.setFont(new Font("Consolas", Font.PLAIN, 13));
        consolePane.setCaretColor(TEXT_COLOR);
        consoleDoc = consolePane.getStyledDocument();
        
        JScrollPane scrollPane = new JScrollPane(consolePane);
        scrollPane.setBackground(BG_DARK);
        scrollPane.setBorder(BorderFactory.createLineBorder(BG_INPUT));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Command input
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBackground(BG_PANEL);
        inputPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        JLabel promptLabel = new JLabel("> ");
        promptLabel.setForeground(ACCENT_COLOR);
        promptLabel.setFont(new Font("Consolas", Font.BOLD, 14));
        inputPanel.add(promptLabel, BorderLayout.WEST);
        
        commandInput = new JTextField();
        commandInput.setBackground(BG_INPUT);
        commandInput.setForeground(TEXT_COLOR);
        commandInput.setCaretColor(TEXT_COLOR);
        commandInput.setFont(new Font("Consolas", Font.PLAIN, 13));
        commandInput.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BG_INPUT),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        commandInput.addActionListener(e -> {
            String cmd = commandInput.getText().trim();
            if (!cmd.isEmpty() && server != null) {
                server.issueCommand(cmd, server);
                appendLog("[Command] " + cmd, LogType.COMMAND);
                commandInput.setText("");
            }
        });
        inputPanel.add(commandInput, BorderLayout.CENTER);
        
        JButton sendButton = new JButton("Send");
        styleButton(sendButton, ACCENT_COLOR, Color.WHITE);
        sendButton.addActionListener(e -> {
            commandInput.postActionEvent();
        });
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        panel.add(inputPanel, BorderLayout.SOUTH);
        
        return panel;
    }

    private JPanel createChatLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topBar.setBackground(BG_PANEL);

        JLabel title = new JLabel("Chat stream only");
        title.setForeground(CHAT_COLOR);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        topBar.add(title);

        JButton clearButton = new JButton("Clear Chat Logs");
        styleButton(clearButton, BG_INPUT, TEXT_COLOR);
        clearButton.addActionListener(e -> {
            chatOnlyLogs.clear();
            refreshChatLogConsole();
        });
        topBar.add(clearButton);

        panel.add(topBar, BorderLayout.NORTH);

        chatLogPane = new JTextPane();
        chatLogPane.setEditable(false);
        chatLogPane.setBackground(BG_DARK);
        chatLogPane.setForeground(CHAT_COLOR);
        chatLogPane.setFont(new Font("Consolas", Font.PLAIN, 13));
        chatLogPane.setCaretColor(TEXT_COLOR);
        chatLogDoc = chatLogPane.getStyledDocument();

        JScrollPane scrollPane = new JScrollPane(chatLogPane);
        scrollPane.setBackground(BG_DARK);
        scrollPane.setBorder(BorderFactory.createLineBorder(BG_INPUT));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
    
    private JPanel createPlayersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Player count header
        JLabel headerLabel = new JLabel("Online Players");
        headerLabel.setForeground(TEXT_COLOR);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        panel.add(headerLabel, BorderLayout.NORTH);
        
        // Player list
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        playerList.setBackground(BG_DARK);
        playerList.setForeground(TEXT_COLOR);
        playerList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        playerList.setSelectionBackground(ACCENT_COLOR);
        playerList.setSelectionForeground(Color.WHITE);
        playerList.setCellRenderer(new PlayerListCellRenderer());
        
        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setBackground(BG_DARK);
        scrollPane.setBorder(BorderFactory.createLineBorder(BG_INPUT));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Player actions
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        actionsPanel.setBackground(BG_PANEL);
        
        JButton kickButton = new JButton("Kick");
        styleButton(kickButton, ERROR_COLOR, Color.WHITE);
        kickButton.addActionListener(e -> {
            String selected = playerList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select a player to kick.", "No Player Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server == null) {
                JOptionPane.showMessageDialog(this, "Server is not running.", "Server Offline", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String reason = JOptionPane.showInputDialog(this, "Kick reason (leave empty for default):", "Kick " + selected, JOptionPane.QUESTION_MESSAGE);
            if (reason != null) {
                if (reason.isEmpty()) {
                    server.issueCommand("kick " + selected, server);
                } else {
                    server.issueCommand("kick " + selected + " " + reason, server);
                }
                appendLog("[GUI] Kicked player: " + selected, LogType.INFO);
            }
        });
        actionsPanel.add(kickButton);
        
        JButton banButton = new JButton("Ban");
        styleButton(banButton, new Color(180, 60, 60), Color.WHITE);
        banButton.addActionListener(e -> {
            String selected = playerList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select a player to ban.", "No Player Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server == null) {
                JOptionPane.showMessageDialog(this, "Server is not running.", "Server Offline", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String reason = JOptionPane.showInputDialog(this, "Ban reason (leave empty for default):", "Ban " + selected, JOptionPane.QUESTION_MESSAGE);
            if (reason != null) {
                // Ban and kick the player
                if (reason.isEmpty()) {
                    server.issueCommand("ban " + selected, server);
                    server.issueCommand("kick " + selected + " You have been banned from this server.", server);
                } else {
                    server.issueCommand("ban " + selected + " " + reason, server);
                    server.issueCommand("kick " + selected + " Banned: " + reason, server);
                }
                appendLog("[GUI] Banned and kicked player: " + selected, LogType.INFO);
                refreshBannedList();
            }
        });
        actionsPanel.add(banButton);
        
        JButton opButton = new JButton("OP");
        styleButton(opButton, ACCENT_COLOR, Color.WHITE);
        opButton.addActionListener(e -> {
            String selected = playerList.getSelectedValue();
            if (selected != null && server != null) {
                server.issueCommand("op " + selected, server);
            }
        });
        actionsPanel.add(opButton);
        
        JButton muteButton = new JButton("Mute");
        styleButton(muteButton, MUTE_COLOR, Color.WHITE);
        muteButton.addActionListener(e -> {
            String selected = playerList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select a player to mute.", "No Player Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server == null) {
                JOptionPane.showMessageDialog(this, "Server is not running.", "Server Offline", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String duration = JOptionPane.showInputDialog(this, "Mute duration in minutes (leave empty for permanent):", "Mute " + selected, JOptionPane.QUESTION_MESSAGE);
            if (duration != null) {
                if (duration.isEmpty()) {
                    server.issueCommand("mute " + selected, server);
                    appendLog("[GUI] Muted player: " + selected, LogType.INFO);
                } else {
                    server.issueCommand("mute " + selected + " " + duration + "m", server);
                    appendLog("[GUI] Muted player: " + selected + " for " + duration + " minutes", LogType.INFO);
                }
            }
        });
        actionsPanel.add(muteButton);
        
        panel.add(actionsPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createBannedPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header
        JLabel headerLabel = new JLabel("Banned Players");
        headerLabel.setForeground(TEXT_COLOR);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        panel.add(headerLabel, BorderLayout.NORTH);
        
        // Banned list
        bannedListModel = new DefaultListModel<>();
        bannedList = new JList<>(bannedListModel);
        bannedList.setBackground(BG_DARK);
        bannedList.setForeground(TEXT_COLOR);
        bannedList.setSelectionBackground(ACCENT_COLOR);
        bannedList.setSelectionForeground(Color.WHITE);
        bannedList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(bannedList);
        scrollPane.setBackground(BG_DARK);
        scrollPane.setBorder(BorderFactory.createLineBorder(BG_INPUT));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Actions panel
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        actionsPanel.setBackground(BG_PANEL);
        
        JButton unbanButton = new JButton("Unban");
        styleButton(unbanButton, SUCCESS_COLOR, Color.WHITE);
        unbanButton.addActionListener(e -> {
            String selected = bannedList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select a player to unban.", "No Player Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server != null) {
                server.issueCommand("pardon " + selected, server);
                appendLog("[GUI] Unbanned player: " + selected, LogType.INFO);
            }
            // Immediately remove from visual list
            bannedListModel.removeElement(selected);
        });
        actionsPanel.add(unbanButton);
        
        JButton refreshBannedButton = new JButton("Refresh");
        styleButton(refreshBannedButton, ACCENT_COLOR, Color.WHITE);
        refreshBannedButton.addActionListener(e -> refreshBannedList());
        actionsPanel.add(refreshBannedButton);
        
        // Add player to ban
        JTextField banNameField = new JTextField(15);
        banNameField.setBackground(BG_INPUT);
        banNameField.setForeground(TEXT_COLOR);
        banNameField.setCaretColor(TEXT_COLOR);
        banNameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BG_INPUT.brighter()),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        actionsPanel.add(banNameField);
        
        JButton banButton = new JButton("Ban Player");
        styleButton(banButton, ERROR_COLOR, Color.WHITE);
        banButton.addActionListener(e -> {
            String name = banNameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a player name to ban.", "No Name Entered", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server == null) {
                JOptionPane.showMessageDialog(this, "Server is not running.", "Server Offline", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Ban and kick the player
            server.issueCommand("ban " + name, server);
            server.issueCommand("kick " + name + " You have been banned from this server.", server);
            appendLog("[GUI] Banned and kicked player: " + name, LogType.INFO);
            banNameField.setText("");
            // Immediately add to visual list
            if (!bannedListModel.contains(name)) {
                bannedListModel.addElement(name);
            }
        });
        actionsPanel.add(banButton);
        
        panel.add(actionsPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createOpsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header
        JLabel headerLabel = new JLabel("Server Operators");
        headerLabel.setForeground(TEXT_COLOR);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        panel.add(headerLabel, BorderLayout.NORTH);
        
        // Ops list
        opsListModel = new DefaultListModel<>();
        opsList = new JList<>(opsListModel);
        opsList.setBackground(BG_DARK);
        opsList.setForeground(TEXT_COLOR);
        opsList.setSelectionBackground(ACCENT_COLOR);
        opsList.setSelectionForeground(Color.WHITE);
        opsList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(opsList);
        scrollPane.setBackground(BG_DARK);
        scrollPane.setBorder(BorderFactory.createLineBorder(BG_INPUT));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Actions panel
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        actionsPanel.setBackground(BG_PANEL);
        
        JButton deopButton = new JButton("Remove OP");
        styleButton(deopButton, ERROR_COLOR, Color.WHITE);
        deopButton.addActionListener(e -> {
            String selected = opsList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select an operator to remove.", "No Operator Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server != null) {
                server.issueCommand("deop " + selected, server);
                appendLog("[GUI] Removed OP from: " + selected, LogType.INFO);
            }
            // Immediately remove from visual list
            opsListModel.removeElement(selected);
        });
        actionsPanel.add(deopButton);
        
        JButton refreshOpsButton = new JButton("Refresh");
        styleButton(refreshOpsButton, ACCENT_COLOR, Color.WHITE);
        refreshOpsButton.addActionListener(e -> refreshOpsList());
        actionsPanel.add(refreshOpsButton);
        
        // Add player to op
        JTextField opNameField = new JTextField(15);
        opNameField.setBackground(BG_INPUT);
        opNameField.setForeground(TEXT_COLOR);
        opNameField.setCaretColor(TEXT_COLOR);
        opNameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BG_INPUT.brighter()),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        actionsPanel.add(opNameField);
        
        JButton opButton = new JButton("Add OP");
        styleButton(opButton, SUCCESS_COLOR, Color.WHITE);
        opButton.addActionListener(e -> {
            String name = opNameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a player name to op.", "No Name Entered", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server != null) {
                server.issueCommand("op " + name, server);
                appendLog("[GUI] Gave OP to: " + name, LogType.INFO);
            }
            opNameField.setText("");
            // Immediately add to visual list
            if (!opsListModel.contains(name)) {
                opsListModel.addElement(name);
            }
        });
        actionsPanel.add(opButton);
        
        panel.add(actionsPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createWhitelistPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header with whitelist status
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerPanel.setBackground(BG_PANEL);
        
        JLabel headerLabel = new JLabel("Whitelisted Players");
        headerLabel.setForeground(TEXT_COLOR);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        headerPanel.add(headerLabel);
        
        JButton whitelistOnBtn = new JButton("Enable");
        styleButton(whitelistOnBtn, SUCCESS_COLOR, Color.WHITE);
        whitelistOnBtn.addActionListener(e -> {
            if (server != null) {
                server.issueCommand("whitelist on", server);
                appendLog("[GUI] Whitelist enabled", LogType.INFO);
            }
        });
        headerPanel.add(whitelistOnBtn);
        
        JButton whitelistOffBtn = new JButton("Disable");
        styleButton(whitelistOffBtn, ERROR_COLOR, Color.WHITE);
        whitelistOffBtn.addActionListener(e -> {
            if (server != null) {
                server.issueCommand("whitelist off", server);
                appendLog("[GUI] Whitelist disabled", LogType.INFO);
            }
        });
        headerPanel.add(whitelistOffBtn);
        
        JButton reloadWhitelistBtn = new JButton("Reload");
        styleButton(reloadWhitelistBtn, BG_INPUT, TEXT_COLOR);
        reloadWhitelistBtn.addActionListener(e -> {
            if (server != null) {
                server.issueCommand("whitelist reload", server);
                appendLog("[GUI] Whitelist reloaded", LogType.INFO);
            }
            refreshWhitelistList();
        });
        headerPanel.add(reloadWhitelistBtn);
        
        panel.add(headerPanel, BorderLayout.NORTH);
        
        // Whitelist
        whitelistListModel = new DefaultListModel<>();
        whitelistList = new JList<>(whitelistListModel);
        whitelistList.setBackground(BG_DARK);
        whitelistList.setForeground(TEXT_COLOR);
        whitelistList.setSelectionBackground(ACCENT_COLOR);
        whitelistList.setSelectionForeground(Color.WHITE);
        whitelistList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(whitelistList);
        scrollPane.setBackground(BG_DARK);
        scrollPane.setBorder(BorderFactory.createLineBorder(BG_INPUT));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Actions panel
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        actionsPanel.setBackground(BG_PANEL);
        
        JButton removeButton = new JButton("Remove");
        styleButton(removeButton, ERROR_COLOR, Color.WHITE);
        removeButton.addActionListener(e -> {
            String selected = whitelistList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select a player to remove from whitelist.", "No Player Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server != null) {
                server.issueCommand("whitelist remove " + selected, server);
                appendLog("[GUI] Removed from whitelist: " + selected, LogType.INFO);
            }
            // Immediately remove from visual list
            whitelistListModel.removeElement(selected);
        });
        actionsPanel.add(removeButton);
        
        JButton refreshButton = new JButton("Refresh");
        styleButton(refreshButton, ACCENT_COLOR, Color.WHITE);
        refreshButton.addActionListener(e -> refreshWhitelistList());
        actionsPanel.add(refreshButton);
        
        // Add player to whitelist
        JTextField whitelistNameField = new JTextField(15);
        whitelistNameField.setBackground(BG_INPUT);
        whitelistNameField.setForeground(TEXT_COLOR);
        whitelistNameField.setCaretColor(TEXT_COLOR);
        whitelistNameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BG_INPUT.brighter()),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        actionsPanel.add(whitelistNameField);
        
        JButton addButton = new JButton("Add Player");
        styleButton(addButton, SUCCESS_COLOR, Color.WHITE);
        addButton.addActionListener(e -> {
            String name = whitelistNameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a player name to add.", "No Name Entered", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (server != null) {
                server.issueCommand("whitelist add " + name, server);
                appendLog("[GUI] Added to whitelist: " + name, LogType.INFO);
            }
            whitelistNameField.setText("");
            // Immediately add to visual list
            if (!whitelistListModel.contains(name)) {
                whitelistListModel.addElement(name);
            }
        });
        actionsPanel.add(addButton);
        
        panel.add(actionsPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void refreshBannedList() {
        SwingUtilities.invokeLater(() -> {
            bannedListModel.clear();
            File bannedFile = new File("banned-players.txt");
            if (bannedFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(bannedFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            bannedListModel.addElement(line);
                        }
                    }
                } catch (IOException e) {
                    appendLog("[GUI] Error reading banned players: " + e.getMessage(), LogType.ERROR);
                }
            }
        });
    }
    
    private void refreshOpsList() {
        SwingUtilities.invokeLater(() -> {
            opsListModel.clear();
            File opsFile = new File("ops.txt");
            if (opsFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(opsFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            opsListModel.addElement(line);
                        }
                    }
                } catch (IOException e) {
                    appendLog("[GUI] Error reading operators: " + e.getMessage(), LogType.ERROR);
                }
            }
        });
    }
    
    private void refreshWhitelistList() {
        SwingUtilities.invokeLater(() -> {
            whitelistListModel.clear();
            File whitelistFile = new File("white-list.txt");
            if (whitelistFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(whitelistFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            whitelistListModel.addElement(line);
                        }
                    }
                } catch (IOException e) {
                    appendLog("[GUI] Error reading whitelist: " + e.getMessage(), LogType.ERROR);
                }
            }
        });
    }
    
    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JPanel optionsContent = new JPanel();
        optionsContent.setLayout(new BoxLayout(optionsContent, BoxLayout.Y_AXIS));
        optionsContent.setBackground(BG_PANEL);
        
        // Memory section
        JPanel memorySection = createSection("Memory Allocation");
        memorySection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        // Get current max memory
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        
        // RAM slider row
        JPanel sliderRow = new JPanel(new BorderLayout(10, 0));
        sliderRow.setBackground(BG_PANEL);
        sliderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        
        JLabel ramLabel = new JLabel("Max RAM:");
        ramLabel.setForeground(TEXT_COLOR);
        ramLabel.setPreferredSize(new Dimension(70, 25));
        sliderRow.add(ramLabel, BorderLayout.WEST);
        
        ramSlider = new JSlider(512, 16384, (int)maxMem);
        ramSlider.setBackground(BG_PANEL);
        ramSlider.setForeground(TEXT_COLOR);
        ramSlider.setMajorTickSpacing(4096);
        ramSlider.setMinorTickSpacing(1024);
        ramSlider.setPaintTicks(true);
        ramSlider.setSnapToTicks(false);
        
        ramSliderLabel = new JLabel(maxMem + " MB");
        ramSliderLabel.setForeground(ACCENT_COLOR);
        ramSliderLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        ramSliderLabel.setPreferredSize(new Dimension(80, 25));
        
        ramSlider.addChangeListener(e -> {
            int value = ramSlider.getValue();
            // Round to nearest 256 MB
            value = (value / 256) * 256;
            ramSliderLabel.setText(value + " MB");
        });
        
        sliderRow.add(ramSlider, BorderLayout.CENTER);
        sliderRow.add(ramSliderLabel, BorderLayout.EAST);
        
        memorySection.add(sliderRow);
        
        // Note
        JPanel noteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        noteRow.setBackground(BG_PANEL);
        JLabel noteLabel = new JLabel("Note: Memory changes require restart with -Xmx flag");
        noteLabel.setForeground(new Color(150, 150, 150));
        noteLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        noteRow.add(noteLabel);
        memorySection.add(noteRow);
        
        optionsContent.add(memorySection);
        optionsContent.add(Box.createVerticalStrut(10));
        
        // Server Properties section
        propertiesPanel = createSection("Server Properties");
        propertiesPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));
        
        // Create grid for properties
        JPanel propsGrid = new JPanel(new GridBagLayout());
        propsGrid.setBackground(BG_PANEL);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // Server IP
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        propsGrid.add(createLabel("Server IP:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        serverIpField = createTextField("");
        propsGrid.add(serverIpField, gbc);
        
        // Server Port
        gbc.gridx = 2; gbc.weightx = 0;
        propsGrid.add(createLabel("Port:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        serverPortField = createTextField("25565");
        serverPortField.setPreferredSize(new Dimension(80, 25));
        propsGrid.add(serverPortField, gbc);
        
        row++;
        
        // Level Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        propsGrid.add(createLabel("World Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        levelNameField = createTextField("world");
        propsGrid.add(levelNameField, gbc);
        
        // Level Seed
        gbc.gridx = 2; gbc.weightx = 0;
        propsGrid.add(createLabel("Seed:"), gbc);
        gbc.gridx = 3; gbc.weightx = 1;
        levelSeedField = createTextField("");
        propsGrid.add(levelSeedField, gbc);
        
        row++;
        
        // Level Type
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        propsGrid.add(createLabel("World Type:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        levelTypeCombo = createComboBox(new String[]{"DEFAULT", "FLAT", "ALPHA", "ALPHA_SNOW", "SKY", "CLASSIC", "INFDEV"});
        propsGrid.add(levelTypeCombo, gbc);
        
        // Max Players
        gbc.gridx = 2; gbc.weightx = 0;
        propsGrid.add(createLabel("Max Players:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        maxPlayersField = createTextField("20");
        maxPlayersField.setPreferredSize(new Dimension(80, 25));
        propsGrid.add(maxPlayersField, gbc);
        
        row++;
        
        // Gamemode
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        propsGrid.add(createLabel("Gamemode:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        gamemodeCombo = createComboBox(new String[]{"Survival", "Creative", "Hardcore"});
        propsGrid.add(gamemodeCombo, gbc);
        
        // Difficulty
        gbc.gridx = 2; gbc.weightx = 0;
        propsGrid.add(createLabel("Difficulty:"), gbc);
        gbc.gridx = 3; gbc.weightx = 1;
        difficultyCombo = createComboBox(new String[]{"Peaceful", "Easy", "Normal", "Hard"});
        difficultyCombo.setSelectedIndex(1); // Default to Easy
        propsGrid.add(difficultyCombo, gbc);
        
        row++;

        // View Distance
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        propsGrid.add(createLabel("View Distance:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        viewDistanceField = createTextField("10");
        viewDistanceField.setPreferredSize(new Dimension(80, 25));
        propsGrid.add(viewDistanceField, gbc);

        // Spawn protection radius (global server setting)
        gbc.gridx = 2; gbc.weightx = 0;
        propsGrid.add(createLabel("Spawn Protection:"), gbc);
        gbc.gridx = 3; gbc.weightx = 1;
        JPanel spawnProtectionRow = new JPanel(new BorderLayout(5, 0));
        spawnProtectionRow.setBackground(BG_PANEL);

        spawnProtectionSlider = new JSlider(0, 64, 16);
        spawnProtectionSlider.setBackground(BG_PANEL);
        spawnProtectionSlider.setForeground(TEXT_COLOR);
        spawnProtectionSlider.setMajorTickSpacing(16);
        spawnProtectionSlider.setMinorTickSpacing(1);
        spawnProtectionSlider.setPaintTicks(true);

        spawnProtectionSliderLabel = new JLabel("16 blocks");
        spawnProtectionSliderLabel.setForeground(ACCENT_COLOR);
        spawnProtectionSliderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        spawnProtectionSliderLabel.setPreferredSize(new Dimension(70, 25));

        spawnProtectionSlider.addChangeListener(e -> {
            if (spawnProtectionSliderLabel != null) {
                spawnProtectionSliderLabel.setText(spawnProtectionSlider.getValue() + " blocks");
            }
        });

        spawnProtectionRow.add(spawnProtectionSlider, BorderLayout.CENTER);
        spawnProtectionRow.add(spawnProtectionSliderLabel, BorderLayout.EAST);
        propsGrid.add(spawnProtectionRow, gbc);

        // MOTD (full width)
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        propsGrid.add(createLabel("MOTD:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1;
        motdField = createTextField("A Minecraft Server");
        propsGrid.add(motdField, gbc);
        gbc.gridwidth = 1;
        
        row++;
        
        // Checkboxes row 1
        JPanel checkRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        checkRow1.setBackground(BG_PANEL);
        
        onlineModeCheck = createCheckBox("Online Mode (Authentication)", true);
        checkRow1.add(onlineModeCheck);
        
        pvpCheck = createCheckBox("PvP", true);
        checkRow1.add(pvpCheck);
        
        allowFlightCheck = createCheckBox("Allow Flight", false);
        checkRow1.add(allowFlightCheck);
        
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4; gbc.weightx = 1;
        propsGrid.add(checkRow1, gbc);
        
        row++;
        
        // Checkboxes row 2
        JPanel checkRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        checkRow2.setBackground(BG_PANEL);
        
        spawnAnimalsCheck = createCheckBox("Spawn Animals", true);
        checkRow2.add(spawnAnimalsCheck);
        
        spawnMonstersCheck = createCheckBox("Spawn Monsters", true);
        checkRow2.add(spawnMonstersCheck);
        
        allowNetherCheck = createCheckBox("Allow Nether", true);
        checkRow2.add(allowNetherCheck);
        
        whiteListCheck = createCheckBox("Whitelist", false);
        checkRow2.add(whiteListCheck);
        
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        propsGrid.add(checkRow2, gbc);
        
        row++;
        
        // Voice chat row
        JPanel voiceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        voiceRow.setBackground(BG_PANEL);
        
        voiceChatCheck = createCheckBox("Voice Chat Enabled", true);
        voiceRow.add(voiceChatCheck);
        
        JLabel voicePortLabel = new JLabel("Voice Port:");
        voicePortLabel.setForeground(TEXT_COLOR);
        voiceRow.add(voicePortLabel);
        
        voiceChatPortField = new JTextField("24454", 6);
        voiceChatPortField.setBackground(BG_INPUT);
        voiceChatPortField.setForeground(TEXT_COLOR);
        voiceChatPortField.setCaretColor(TEXT_COLOR);
        voiceChatPortField.setBorder(BorderFactory.createLineBorder(BG_DARK));
        voiceRow.add(voiceChatPortField);
        
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        propsGrid.add(voiceRow, gbc);
        
        row++;
        
        // Save button
        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        saveRow.setBackground(BG_PANEL);
        
        savePropertiesButton = new JButton("Save Properties");
        styleButton(savePropertiesButton, SUCCESS_COLOR, Color.WHITE);
        savePropertiesButton.addActionListener(e -> saveServerProperties());
        saveRow.add(savePropertiesButton);
        
        JButton reloadPropsButton = new JButton("Reload");
        styleButton(reloadPropsButton, ACCENT_COLOR, Color.WHITE);
        reloadPropsButton.addActionListener(e -> loadServerProperties());
        saveRow.add(reloadPropsButton);
        
        JLabel propsNote = new JLabel("  (Server restart required for most changes)");
        propsNote.setForeground(new Color(150, 150, 150));
        propsNote.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        saveRow.add(propsNote);
        
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 4;
        propsGrid.add(saveRow, gbc);
        
        propertiesPanel.add(propsGrid);
        optionsContent.add(propertiesPanel);
        optionsContent.add(Box.createVerticalStrut(10));
        
        // Load server properties
        loadServerProperties();
        
        // Threading Configuration section
        JPanel threadingSection = createSection("Threading Configuration");
        threadingSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        
        JPanel threadingGrid = new JPanel(new GridBagLayout());
        threadingGrid.setBackground(BG_PANEL);
        GridBagConstraints tgbc = new GridBagConstraints();
        tgbc.insets = new Insets(3, 5, 3, 10);
        tgbc.anchor = GridBagConstraints.WEST;
        tgbc.fill = GridBagConstraints.HORIZONTAL;
        
        int trow = 0;
        
        // Async Chunk Generation
        tgbc.gridx = 0; tgbc.gridy = trow; tgbc.weightx = 0;
        asyncChunkGenCheck = createCheckBox("Async Chunk Generation", true);
        asyncChunkGenCheck.setToolTipText("Generate terrain on background threads (safe, improves performance)");
        threadingGrid.add(asyncChunkGenCheck, tgbc);
        
        tgbc.gridx = 1; tgbc.weightx = 0;
        threadingGrid.add(createLabel("Threads:"), tgbc);
        
        tgbc.gridx = 2; tgbc.weightx = 0;
        int defaultThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        chunkGenThreadsSpinner = new JSpinner(new SpinnerNumberModel(defaultThreads, 1, 16, 1));
        chunkGenThreadsSpinner.setPreferredSize(new Dimension(60, 25));
        styleSpinner(chunkGenThreadsSpinner);
        threadingGrid.add(chunkGenThreadsSpinner, tgbc);
        
        trow++;
        
        // Async Lighting (experimental)
        tgbc.gridx = 0; tgbc.gridy = trow; tgbc.weightx = 0;
        asyncLightingCheck = createCheckBox("Async Lighting (Experimental)", false);
        asyncLightingCheck.setToolTipText("Process lighting updates on background threads (may cause visual glitches)");
        threadingGrid.add(asyncLightingCheck, tgbc);
        
        tgbc.gridx = 1; tgbc.weightx = 0;
        threadingGrid.add(createLabel("Threads:"), tgbc);
        
        tgbc.gridx = 2; tgbc.weightx = 0;
        lightingThreadsSpinner = new JSpinner(new SpinnerNumberModel(defaultThreads, 1, 16, 1));
        lightingThreadsSpinner.setPreferredSize(new Dimension(60, 25));
        styleSpinner(lightingThreadsSpinner);
        threadingGrid.add(lightingThreadsSpinner, tgbc);
        
        trow++;
        
        // Async Entity Processing (experimental)
        tgbc.gridx = 0; tgbc.gridy = trow; tgbc.weightx = 0;
        asyncEntityCheck = createCheckBox("Async Entity Processing (Experimental)", false);
        asyncEntityCheck.setToolTipText("Process entity AI on background threads (may cause AI issues)");
        threadingGrid.add(asyncEntityCheck, tgbc);
        
        tgbc.gridx = 1; tgbc.weightx = 0;
        threadingGrid.add(createLabel("Threads:"), tgbc);
        
        tgbc.gridx = 2; tgbc.weightx = 0;
        entityThreadsSpinner = new JSpinner(new SpinnerNumberModel(defaultThreads, 1, 16, 1));
        entityThreadsSpinner.setPreferredSize(new Dimension(60, 25));
        styleSpinner(entityThreadsSpinner);
        threadingGrid.add(entityThreadsSpinner, tgbc);
        
        trow++;

        // Chunk compression workers
        tgbc.gridx = 0; tgbc.gridy = trow; tgbc.weightx = 0;
        threadingGrid.add(createLabel("Chunk Compression Workers:"), tgbc);

        tgbc.gridx = 1; tgbc.weightx = 0;
        chunkCompressionThreadsSpinner = new JSpinner(new SpinnerNumberModel(Math.max(1, Runtime.getRuntime().availableProcessors() / 3), 1, 16, 1));
        chunkCompressionThreadsSpinner.setPreferredSize(new Dimension(60, 25));
        styleSpinner(chunkCompressionThreadsSpinner);
        threadingGrid.add(chunkCompressionThreadsSpinner, tgbc);

        tgbc.gridx = 2; tgbc.weightx = 0;
        threadingGrid.add(createLabel("(striped by player id)"), tgbc);

        trow++;

        // Chunk compression queue capacity
        tgbc.gridx = 0; tgbc.gridy = trow; tgbc.weightx = 0;
        threadingGrid.add(createLabel("Compression Queue Capacity:"), tgbc);

        tgbc.gridx = 1; tgbc.weightx = 0;
        chunkCompressionQueueCapacitySpinner = new JSpinner(new SpinnerNumberModel(10240, 128, 200000, 128));
        chunkCompressionQueueCapacitySpinner.setPreferredSize(new Dimension(90, 25));
        styleSpinner(chunkCompressionQueueCapacitySpinner);
        threadingGrid.add(chunkCompressionQueueCapacitySpinner, tgbc);

        tgbc.gridx = 2; tgbc.weightx = 0;
        threadingGrid.add(createLabel("(total packets)"), tgbc);

        trow++;

        // Backpressure watermarks
        tgbc.gridx = 0; tgbc.gridy = trow; tgbc.weightx = 0;
        threadingGrid.add(createLabel("Backpressure High %:"), tgbc);

        tgbc.gridx = 1; tgbc.weightx = 0;
        chunkCompressionHighWatermarkSpinner = new JSpinner(new SpinnerNumberModel(85, 1, 100, 1));
        chunkCompressionHighWatermarkSpinner.setPreferredSize(new Dimension(60, 25));
        styleSpinner(chunkCompressionHighWatermarkSpinner);
        threadingGrid.add(chunkCompressionHighWatermarkSpinner, tgbc);

        tgbc.gridx = 2; tgbc.weightx = 0;
        threadingGrid.add(createLabel("Low %:"), tgbc);

        tgbc.gridx = 3; tgbc.weightx = 0;
        chunkCompressionLowWatermarkSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 100, 1));
        chunkCompressionLowWatermarkSpinner.setPreferredSize(new Dimension(60, 25));
        styleSpinner(chunkCompressionLowWatermarkSpinner);
        threadingGrid.add(chunkCompressionLowWatermarkSpinner, tgbc);

        trow++;
        
        // Save button row
        JPanel threadingSaveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        threadingSaveRow.setBackground(BG_PANEL);
        
        saveThreadingButton = new JButton("Save Threading Config");
        styleButton(saveThreadingButton, SUCCESS_COLOR, Color.WHITE);
        saveThreadingButton.addActionListener(e -> saveThreadingConfig());
        threadingSaveRow.add(saveThreadingButton);
        
        JButton reloadThreadingButton = new JButton("Reload");
        styleButton(reloadThreadingButton, ACCENT_COLOR, Color.WHITE);
        reloadThreadingButton.addActionListener(e -> loadThreadingConfig());
        threadingSaveRow.add(reloadThreadingButton);
        
        JLabel threadingNote = new JLabel("  (Server restart required for changes)");
        threadingNote.setForeground(new Color(150, 150, 150));
        threadingNote.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        threadingSaveRow.add(threadingNote);
        
        tgbc.gridx = 0; tgbc.gridy = trow; tgbc.gridwidth = 3;
        threadingGrid.add(threadingSaveRow, tgbc);
        
        threadingSection.add(threadingGrid);
        optionsContent.add(threadingSection);
        optionsContent.add(Box.createVerticalStrut(10));
        
        // Load threading config
        loadThreadingConfig();

        // Startup readiness / tick catch-up section
        JPanel startupSection = createSection("Startup Readiness");
        startupSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JPanel startupGrid = new JPanel(new GridBagLayout());
        startupGrid.setBackground(BG_PANEL);
        GridBagConstraints sgbc = new GridBagConstraints();
        sgbc.insets = new Insets(3, 5, 3, 10);
        sgbc.anchor = GridBagConstraints.WEST;
        sgbc.fill = GridBagConstraints.HORIZONTAL;

        int srow = 0;

        sgbc.gridx = 0; sgbc.gridy = srow; sgbc.gridwidth = 2; sgbc.weightx = 1;
        startupReadinessEnabledCheck = createCheckBox("Enable Startup Readiness Gate", true);
        startupGrid.add(startupReadinessEnabledCheck, sgbc);

        srow++;
        sgbc.gridx = 0; sgbc.gridy = srow; sgbc.gridwidth = 2;
        startupHoldLoginCheck = createCheckBox("Hold pre-ready logins and auto-join when warmup completes", true);
        startupGrid.add(startupHoldLoginCheck, sgbc);

        srow++;
        sgbc.gridx = 0; sgbc.gridy = srow; sgbc.gridwidth = 2;
        tickCatchupEnabledCheck = createCheckBox("Enable Tick Catch-up Clamp", true);
        startupGrid.add(tickCatchupEnabledCheck, sgbc);

        srow++;
        sgbc.gridwidth = 1;
        sgbc.gridx = 0; sgbc.gridy = srow; sgbc.weightx = 0;
        startupGrid.add(createLabel("Overworld Radius (chunks):"), sgbc);
        sgbc.gridx = 1;
        startupOverworldRadiusSpinner = new JSpinner(new SpinnerNumberModel(14, 0, 64, 1));
        startupOverworldRadiusSpinner.setPreferredSize(new Dimension(70, 25));
        styleSpinner(startupOverworldRadiusSpinner);
        startupGrid.add(startupOverworldRadiusSpinner, sgbc);

        srow++;
        sgbc.gridx = 0; sgbc.gridy = srow;
        startupGrid.add(createLabel("Nether Radius (chunks):"), sgbc);
        sgbc.gridx = 1;
        startupNetherRadiusSpinner = new JSpinner(new SpinnerNumberModel(8, 0, 64, 1));
        startupNetherRadiusSpinner.setPreferredSize(new Dimension(70, 25));
        styleSpinner(startupNetherRadiusSpinner);
        startupGrid.add(startupNetherRadiusSpinner, sgbc);

        srow++;
        sgbc.gridx = 0; sgbc.gridy = srow;
        startupGrid.add(createLabel("Joiner Extra Radius:"), sgbc);
        sgbc.gridx = 1;
        startupJoinerExtraRadiusSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 32, 1));
        startupJoinerExtraRadiusSpinner.setPreferredSize(new Dimension(70, 25));
        styleSpinner(startupJoinerExtraRadiusSpinner);
        startupGrid.add(startupJoinerExtraRadiusSpinner, sgbc);

        srow++;
        sgbc.gridx = 0; sgbc.gridy = srow;
        startupGrid.add(createLabel("Max Hold Seconds:"), sgbc);
        sgbc.gridx = 1;
        startupMaxHoldSecondsSpinner = new JSpinner(new SpinnerNumberModel(60, 5, 600, 1));
        startupMaxHoldSecondsSpinner.setPreferredSize(new Dimension(70, 25));
        styleSpinner(startupMaxHoldSecondsSpinner);
        startupGrid.add(startupMaxHoldSecondsSpinner, sgbc);

        srow++;
        sgbc.gridx = 0; sgbc.gridy = srow;
        startupGrid.add(createLabel("Tick Catch-up Backlog (ms):"), sgbc);
        sgbc.gridx = 1;
        tickCatchupMaxBacklogSpinner = new JSpinner(new SpinnerNumberModel(200, 50, 2000, 10));
        tickCatchupMaxBacklogSpinner.setPreferredSize(new Dimension(90, 25));
        styleSpinner(tickCatchupMaxBacklogSpinner);
        startupGrid.add(tickCatchupMaxBacklogSpinner, sgbc);

        srow++;
        JPanel startupSaveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        startupSaveRow.setBackground(BG_PANEL);
        saveStartupReadinessButton = new JButton("Save Startup Readiness");
        styleButton(saveStartupReadinessButton, SUCCESS_COLOR, Color.WHITE);
        saveStartupReadinessButton.addActionListener(e -> saveStartupReadinessConfig());
        startupSaveRow.add(saveStartupReadinessButton);

        JButton reloadStartupButton = new JButton("Reload");
        styleButton(reloadStartupButton, ACCENT_COLOR, Color.WHITE);
        reloadStartupButton.addActionListener(e -> loadStartupReadinessConfig());
        startupSaveRow.add(reloadStartupButton);

        JLabel startupNote = new JLabel("  (Server restart required for startup policy changes)");
        startupNote.setForeground(new Color(150, 150, 150));
        startupNote.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        startupSaveRow.add(startupNote);

        sgbc.gridx = 0; sgbc.gridy = srow; sgbc.gridwidth = 2;
        startupGrid.add(startupSaveRow, sgbc);

        startupSection.add(startupGrid);
        optionsContent.add(startupSection);
        optionsContent.add(Box.createVerticalStrut(10));

        loadStartupReadinessConfig();
        
        // Server info section
        JPanel infoSection = createSection("Server Information");
        infoSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        addInfoRow(infoSection, "Java Version:", System.getProperty("java.version"));
        addInfoRow(infoSection, "OS:", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        addInfoRow(infoSection, "Available Processors:", String.valueOf(Runtime.getRuntime().availableProcessors()));
        
        optionsContent.add(infoSection);
        optionsContent.add(Box.createVerticalStrut(10));
        
        // Quick commands section
        JPanel commandsSection = createSection("Quick Commands");
        commandsSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        
        JPanel commandsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        commandsRow.setBackground(BG_PANEL);
        
        JButton saveButton = new JButton("Save All");
        styleButton(saveButton, SUCCESS_COLOR, Color.WHITE);
        saveButton.addActionListener(e -> {
            if (server != null) server.issueCommand("save-all", server);
        });
        commandsRow.add(saveButton);
        
        JButton reloadButton = new JButton("Reload Plugins");
        styleButton(reloadButton, ACCENT_COLOR, Color.WHITE);
        reloadButton.addActionListener(e -> {
            if (server != null) server.issueCommand("reload", server);
        });
        commandsRow.add(reloadButton);
        
        commandsSection.add(commandsRow);
        optionsContent.add(commandsSection);
        optionsContent.add(Box.createVerticalStrut(10));
        
        // Debug Commands section (Profiler)
        JPanel debugSection = createSection("Debug Commands");
        debugSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        
        JPanel debugRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        debugRow1.setBackground(BG_PANEL);
        
        JButton profileStartBtn = new JButton("Start Profiler");
        styleButton(profileStartBtn, SUCCESS_COLOR, Color.WHITE);
        profileStartBtn.addActionListener(e -> {
            if (server != null) server.issueCommand("profile start", server);
        });
        debugRow1.add(profileStartBtn);
        
        JButton profileStopBtn = new JButton("Stop Profiler");
        styleButton(profileStopBtn, new Color(200, 120, 50), Color.WHITE);
        profileStopBtn.addActionListener(e -> {
            if (server != null) server.issueCommand("profile stop", server);
        });
        debugRow1.add(profileStopBtn);
        
        JButton profileReportBtn = new JButton("View Report");
        styleButton(profileReportBtn, ACCENT_COLOR, Color.WHITE);
        profileReportBtn.addActionListener(e -> {
            if (server != null) server.issueCommand("profile report", server);
        });
        debugRow1.add(profileReportBtn);
        
        JButton profileSaveBtn = new JButton("Save Report");
        styleButton(profileSaveBtn, new Color(80, 140, 200), Color.WHITE);
        profileSaveBtn.addActionListener(e -> {
            if (server != null) server.issueCommand("profile save", server);
        });
        debugRow1.add(profileSaveBtn);
        
        JPanel debugRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        debugRow2.setBackground(BG_PANEL);
        
        JButton profileStatusBtn = new JButton("Profiler Status");
        styleButton(profileStatusBtn, new Color(100, 100, 120), Color.WHITE);
        profileStatusBtn.addActionListener(e -> {
            if (server != null) server.issueCommand("profile status", server);
        });
        debugRow2.add(profileStatusBtn);
        
        JButton profileClearBtn = new JButton("Clear Data");
        styleButton(profileClearBtn, new Color(180, 80, 80), Color.WHITE);
        profileClearBtn.addActionListener(e -> {
            if (server != null) server.issueCommand("profile clear", server);
        });
        debugRow2.add(profileClearBtn);
        
        debugSection.add(debugRow1);
        debugSection.add(debugRow2);
        optionsContent.add(debugSection);
        
        // Add filler
        optionsContent.add(Box.createVerticalGlue());
        
        JScrollPane scrollPane = new JScrollPane(optionsContent);
        scrollPane.setBackground(BG_PANEL);
        scrollPane.getViewport().setBackground(BG_PANEL);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_COLOR);
        return label;
    }
    
    private JTextField createTextField(String defaultValue) {
        JTextField field = new JTextField(defaultValue);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_COLOR);
        field.setCaretColor(TEXT_COLOR);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60)),
            new EmptyBorder(3, 5, 3, 5)
        ));
        return field;
    }
    
    private JComboBox<String> createComboBox(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setBackground(BG_INPUT);
        combo.setForeground(TEXT_COLOR);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? ACCENT_COLOR : BG_INPUT);
                setForeground(TEXT_COLOR);
                return this;
            }
        });
        return combo;
    }
    
    private JCheckBox createCheckBox(String text, boolean selected) {
        JCheckBox check = new JCheckBox(text, selected);
        check.setBackground(BG_PANEL);
        check.setForeground(TEXT_COLOR);
        check.setFocusPainted(false);
        return check;
    }
    
    private void loadServerProperties() {
        loadSpawnProtectionRadius();

        File propsFile = new File("server.properties");
        boolean exists = propsFile.exists();
        
        // Enable/disable controls based on file existence
        setPropertiesEnabled(exists);
        
        if (!exists) {
            return;
        }
        
        try {
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(propsFile);
            props.load(fis);
            fis.close();
            
            // Load values into UI
            serverIpField.setText(props.getProperty("server-ip", ""));
            serverPortField.setText(props.getProperty("server-port", "25565"));
            levelNameField.setText(props.getProperty("level-name", "world"));
            levelSeedField.setText(props.getProperty("level-seed", ""));
            maxPlayersField.setText(props.getProperty("max-players", "20"));
            viewDistanceField.setText(props.getProperty("view-distance", "10"));
            motdField.setText(props.getProperty("motd", "A Minecraft Server"));
            
            // Level type
            String levelType = props.getProperty("level-type", "DEFAULT").toUpperCase();
            for (int i = 0; i < levelTypeCombo.getItemCount(); i++) {
                if (levelTypeCombo.getItemAt(i).equalsIgnoreCase(levelType)) {
                    levelTypeCombo.setSelectedIndex(i);
                    break;
                }
            }
            
            // Gamemode (0/survival, 1/creative, 2/hardcore)
            String gamemode = props.getProperty("gamemode", "survival").toLowerCase();
            if (gamemode.equals("1") || gamemode.equals("creative") || gamemode.equals("c")) {
                gamemodeCombo.setSelectedIndex(1); // Creative
            } else if (gamemode.equals("2") || gamemode.equals("hardcore") || gamemode.equals("h")) {
                gamemodeCombo.setSelectedIndex(2); // Hardcore
            } else {
                gamemodeCombo.setSelectedIndex(0); // Survival (default)
            }
            
            // Difficulty
            try {
                int diff = Integer.parseInt(props.getProperty("difficulty", "1"));
                if (diff >= 0 && diff <= 3) difficultyCombo.setSelectedIndex(diff);
            } catch (NumberFormatException ignored) {}
            
            // Booleans
            onlineModeCheck.setSelected(Boolean.parseBoolean(props.getProperty("online-mode", "true")));
            spawnAnimalsCheck.setSelected(Boolean.parseBoolean(props.getProperty("spawn-animals", "true")));
            spawnMonstersCheck.setSelected(Boolean.parseBoolean(props.getProperty("spawn-monsters", "true")));
            pvpCheck.setSelected(Boolean.parseBoolean(props.getProperty("pvp", "true")));
            allowFlightCheck.setSelected(Boolean.parseBoolean(props.getProperty("allow-flight", "false")));
            allowNetherCheck.setSelected(Boolean.parseBoolean(props.getProperty("allow-nether", "true")));
            whiteListCheck.setSelected(Boolean.parseBoolean(props.getProperty("white-list", "false")));
            voiceChatCheck.setSelected(Boolean.parseBoolean(props.getProperty("voice-chat", "true")));
            voiceChatPortField.setText(props.getProperty("voice-chat-port", "24454"));
            
        } catch (IOException e) {
            appendLog("[GUI] Error loading server.properties: " + e.getMessage(), LogType.ERROR);
        }
    }
    
    private void saveServerProperties() {
        File propsFile = new File("server.properties");
        
        try {
            Properties props = new Properties();
            
            // Load existing properties first to preserve any we don't manage
            if (propsFile.exists()) {
                FileInputStream fis = new FileInputStream(propsFile);
                props.load(fis);
                fis.close();
            }
            
            // Update with UI values
            props.setProperty("server-ip", serverIpField.getText().trim());
            props.setProperty("server-port", serverPortField.getText().trim());
            props.setProperty("level-name", levelNameField.getText().trim());
            props.setProperty("level-seed", levelSeedField.getText().trim());
            props.setProperty("max-players", maxPlayersField.getText().trim());
            props.setProperty("view-distance", viewDistanceField.getText().trim());
            props.setProperty("motd", motdField.getText());
            props.setProperty("level-type", (String) levelTypeCombo.getSelectedItem());
            // Save gamemode as string (survival, creative, hardcore)
            String[] gamemodes = {"survival", "creative", "hardcore"};
            props.setProperty("gamemode", gamemodes[gamemodeCombo.getSelectedIndex()]);
            props.setProperty("difficulty", String.valueOf(difficultyCombo.getSelectedIndex()));
            
            // Online mode controls authentication (modern Mojang auth when true, no auth when false)
            props.setProperty("online-mode", String.valueOf(onlineModeCheck.isSelected()));
            // Remove legacy modern-authentication property if it exists
            props.remove("modern-authentication");
            
            props.setProperty("spawn-animals", String.valueOf(spawnAnimalsCheck.isSelected()));
            props.setProperty("spawn-monsters", String.valueOf(spawnMonstersCheck.isSelected()));
            props.setProperty("pvp", String.valueOf(pvpCheck.isSelected()));
            props.setProperty("allow-flight", String.valueOf(allowFlightCheck.isSelected()));
            props.setProperty("allow-nether", String.valueOf(allowNetherCheck.isSelected()));
            props.setProperty("white-list", String.valueOf(whiteListCheck.isSelected()));
            props.setProperty("voice-chat", String.valueOf(voiceChatCheck.isSelected()));
            props.setProperty("voice-chat-port", voiceChatPortField.getText().trim());
            
            // Save
            FileOutputStream fos = new FileOutputStream(propsFile);
            props.store(fos, "Minecraft server properties");
            fos.close();

            saveSpawnProtectionRadius();
            
            appendLog("[GUI] Server properties saved successfully.", LogType.INFO);
            setPropertiesEnabled(true);
            
        } catch (IOException e) {
            appendLog("[GUI] Error saving server.properties: " + e.getMessage(), LogType.ERROR);
        }
    }
    
    private void setPropertiesEnabled(boolean enabled) {
        serverIpField.setEnabled(enabled);
        serverPortField.setEnabled(enabled);
        levelNameField.setEnabled(enabled);
        levelSeedField.setEnabled(enabled);
        maxPlayersField.setEnabled(enabled);
        viewDistanceField.setEnabled(enabled);
        motdField.setEnabled(enabled);
        levelTypeCombo.setEnabled(enabled);
        gamemodeCombo.setEnabled(enabled);
        difficultyCombo.setEnabled(enabled);
        onlineModeCheck.setEnabled(enabled);
        spawnAnimalsCheck.setEnabled(enabled);
        spawnMonstersCheck.setEnabled(enabled);
        pvpCheck.setEnabled(enabled);
        allowFlightCheck.setEnabled(enabled);
        allowNetherCheck.setEnabled(enabled);
        whiteListCheck.setEnabled(enabled);
        voiceChatCheck.setEnabled(enabled);
        voiceChatPortField.setEnabled(enabled);
        if (spawnProtectionSlider != null) {
            spawnProtectionSlider.setEnabled(true);
        }
        
        // Always enable save button - allows creating new server.properties
        savePropertiesButton.setEnabled(true);
        
        if (!enabled) {
            // Show placeholder text
            serverIpField.setText("");
            serverPortField.setText("25565");
            levelNameField.setText("world");
            levelSeedField.setText("");
            maxPlayersField.setText("20");
            viewDistanceField.setText("10");
            motdField.setText("A Minecraft Server");
        }
    }

    private void loadSpawnProtectionRadius() {
        if (spawnProtectionSlider == null) {
            return;
        }

        int radius = 16;
        if (server != null && server.server != null) {
            radius = Math.max(0, server.server.getSpawnRadius());
        } else {
            try {
                Configuration config = new Configuration(new File("bukkit.yml"));
                config.load();
                radius = Math.max(0, config.getInt("settings.spawn-radius", 16));
            } catch (Exception e) {
                appendLog("[GUI] Error loading spawn protection from bukkit.yml: " + e.getMessage(), LogType.ERROR);
            }
        }

        spawnProtectionSlider.setValue(radius);
        if (spawnProtectionSliderLabel != null) {
            spawnProtectionSliderLabel.setText(radius + " blocks");
        }
    }

    private void saveSpawnProtectionRadius() {
        if (spawnProtectionSlider == null) {
            return;
        }

        int radius = Math.max(0, spawnProtectionSlider.getValue());

        if (server != null && server.server != null) {
            server.server.setSpawnRadius(radius);
        } else {
            try {
                Configuration config = new Configuration(new File("bukkit.yml"));
                config.load();
                config.setProperty("settings.spawn-radius", Integer.valueOf(radius));
                config.save();
            } catch (Exception e) {
                appendLog("[GUI] Error saving spawn protection to bukkit.yml: " + e.getMessage(), LogType.ERROR);
                return;
            }
        }

    }
    
    private void styleSpinner(JSpinner spinner) {
        spinner.getEditor().getComponent(0).setBackground(BG_INPUT);
        spinner.getEditor().getComponent(0).setForeground(TEXT_COLOR);
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setCaretColor(TEXT_COLOR);
    }
    
    private void loadThreadingConfig() {
        File configFile = new File("threading.properties");
        
        if (!configFile.exists()) {
            // Set defaults
            int defaultThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
            asyncChunkGenCheck.setSelected(true);
            chunkGenThreadsSpinner.setValue(defaultThreads);
            asyncLightingCheck.setSelected(false);
            lightingThreadsSpinner.setValue(defaultThreads);
            asyncEntityCheck.setSelected(false);
            entityThreadsSpinner.setValue(defaultThreads);
            chunkCompressionThreadsSpinner.setValue(Math.max(1, Runtime.getRuntime().availableProcessors() / 3));
            chunkCompressionQueueCapacitySpinner.setValue(10240);
            chunkCompressionHighWatermarkSpinner.setValue(85);
            chunkCompressionLowWatermarkSpinner.setValue(50);
            return;
        }
        
        try {
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(configFile);
            props.load(fis);
            fis.close();
            
            int defaultThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
            
            asyncChunkGenCheck.setSelected(Boolean.parseBoolean(props.getProperty("async.chunk-generation.enabled", "true")));
            chunkGenThreadsSpinner.setValue(Integer.parseInt(props.getProperty("async.chunk-generation.threads", String.valueOf(defaultThreads))));
            
            asyncLightingCheck.setSelected(Boolean.parseBoolean(props.getProperty("async.lighting.enabled", "false")));
            lightingThreadsSpinner.setValue(Integer.parseInt(props.getProperty("async.lighting.threads", String.valueOf(defaultThreads))));
            
            asyncEntityCheck.setSelected(Boolean.parseBoolean(props.getProperty("async.entity-processing.enabled", "false")));
            entityThreadsSpinner.setValue(Integer.parseInt(props.getProperty("async.entity-processing.threads", String.valueOf(defaultThreads))));
            chunkCompressionThreadsSpinner.setValue(Integer.parseInt(props.getProperty("chunk-compression.threads", String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() / 3)))));
            chunkCompressionQueueCapacitySpinner.setValue(Integer.parseInt(props.getProperty("chunk-compression.queue-capacity", "10240")));
            chunkCompressionHighWatermarkSpinner.setValue(Integer.parseInt(props.getProperty("chunk-compression.high-watermark-percent", "85")));
            chunkCompressionLowWatermarkSpinner.setValue(Integer.parseInt(props.getProperty("chunk-compression.low-watermark-percent", "50")));
            
        } catch (Exception e) {
            appendLog("[GUI] Error loading threading.properties: " + e.getMessage(), LogType.ERROR);
        }
    }
    
    private void saveThreadingConfig() {
        File configFile = new File("threading.properties");
        
        try {
            Properties props = new Properties();
            
            // Load existing properties first to preserve any we don't manage
            if (configFile.exists()) {
                FileInputStream fis = new FileInputStream(configFile);
                props.load(fis);
                fis.close();
            }
            
            props.setProperty("async.chunk-generation.enabled", String.valueOf(asyncChunkGenCheck.isSelected()));
            props.setProperty("async.chunk-generation.threads", String.valueOf(chunkGenThreadsSpinner.getValue()));
            
            props.setProperty("async.lighting.enabled", String.valueOf(asyncLightingCheck.isSelected()));
            props.setProperty("async.lighting.threads", String.valueOf(lightingThreadsSpinner.getValue()));
            
            props.setProperty("async.entity-processing.enabled", String.valueOf(asyncEntityCheck.isSelected()));
            props.setProperty("async.entity-processing.threads", String.valueOf(entityThreadsSpinner.getValue()));
            props.setProperty("chunk-compression.threads", String.valueOf(chunkCompressionThreadsSpinner.getValue()));
            props.setProperty("chunk-compression.queue-capacity", String.valueOf(chunkCompressionQueueCapacitySpinner.getValue()));

            int high = ((Number) chunkCompressionHighWatermarkSpinner.getValue()).intValue();
            int low = ((Number) chunkCompressionLowWatermarkSpinner.getValue()).intValue();
            if (high < low) {
                high = low;
                chunkCompressionHighWatermarkSpinner.setValue(Integer.valueOf(high));
            }
            props.setProperty("chunk-compression.high-watermark-percent", String.valueOf(high));
            props.setProperty("chunk-compression.low-watermark-percent", String.valueOf(low));
            
            // Preserve max-tasks-per-tick if it exists, otherwise set default
            if (!props.containsKey("threading.max-tasks-per-tick")) {
                props.setProperty("threading.max-tasks-per-tick", "100");
            }
            
            FileOutputStream fos = new FileOutputStream(configFile);
            props.store(fos, "Server Threading Configuration\n" +
                "WARNING: Async features are experimental!\n" +
                "Async chunk generation is generally safe.\n" +
                "Async lighting and entity processing may cause issues.");
            fos.close();
            
            appendLog("[GUI] Threading configuration saved successfully.", LogType.INFO);
            
        } catch (IOException e) {
            appendLog("[GUI] Error saving threading.properties: " + e.getMessage(), LogType.ERROR);
        }
    }

    private void loadStartupReadinessConfig() {
        File configFile = new File("poseidon.yml");
        if (!configFile.exists()) {
            startupReadinessEnabledCheck.setSelected(true);
            startupHoldLoginCheck.setSelected(true);
            tickCatchupEnabledCheck.setSelected(true);
            startupOverworldRadiusSpinner.setValue(14);
            startupNetherRadiusSpinner.setValue(8);
            startupJoinerExtraRadiusSpinner.setValue(4);
            startupMaxHoldSecondsSpinner.setValue(60);
            tickCatchupMaxBacklogSpinner.setValue(200);
            return;
        }

        try {
            Configuration config = new Configuration(configFile);
            config.load();

            startupReadinessEnabledCheck.setSelected(config.getBoolean("settings.startup-readiness.enabled", true));
            startupHoldLoginCheck.setSelected(config.getBoolean("settings.startup-readiness.hold-login-enabled", true));
            tickCatchupEnabledCheck.setSelected(config.getBoolean("settings.tick-catchup.enabled", true));
            startupOverworldRadiusSpinner.setValue(Integer.valueOf(config.getInt("settings.startup-readiness.overworld-radius-chunks", 14)));
            startupNetherRadiusSpinner.setValue(Integer.valueOf(config.getInt("settings.startup-readiness.nether-radius-chunks", 8)));
            startupJoinerExtraRadiusSpinner.setValue(Integer.valueOf(config.getInt("settings.startup-readiness.joiner-extra-radius-chunks", 4)));
            startupMaxHoldSecondsSpinner.setValue(Integer.valueOf(config.getInt("settings.startup-readiness.max-hold-seconds", 60)));
            tickCatchupMaxBacklogSpinner.setValue(Integer.valueOf(config.getInt("settings.tick-catchup.max-backlog-ms", 200)));
        } catch (Exception e) {
            appendLog("[GUI] Error loading poseidon.yml startup settings: " + e.getMessage(), LogType.ERROR);
        }
    }

    private void saveStartupReadinessConfig() {
        File configFile = new File("poseidon.yml");
        try {
            Configuration config = new Configuration(configFile);
            config.load();

            config.setProperty("settings.startup-readiness.enabled", Boolean.valueOf(startupReadinessEnabledCheck.isSelected()));
            config.setProperty("settings.startup-readiness.hold-login-enabled", Boolean.valueOf(startupHoldLoginCheck.isSelected()));
            config.setProperty("settings.startup-readiness.overworld-radius-chunks", Integer.valueOf(((Number) startupOverworldRadiusSpinner.getValue()).intValue()));
            config.setProperty("settings.startup-readiness.nether-radius-chunks", Integer.valueOf(((Number) startupNetherRadiusSpinner.getValue()).intValue()));
            config.setProperty("settings.startup-readiness.joiner-extra-radius-chunks", Integer.valueOf(((Number) startupJoinerExtraRadiusSpinner.getValue()).intValue()));
            config.setProperty("settings.startup-readiness.max-hold-seconds", Integer.valueOf(((Number) startupMaxHoldSecondsSpinner.getValue()).intValue()));
            config.setProperty("settings.tick-catchup.enabled", Boolean.valueOf(tickCatchupEnabledCheck.isSelected()));
            config.setProperty("settings.tick-catchup.max-backlog-ms", Integer.valueOf(((Number) tickCatchupMaxBacklogSpinner.getValue()).intValue()));

            if (config.getProperty("settings.startup-readiness.progress-log-interval-seconds") == null) {
                config.setProperty("settings.startup-readiness.progress-log-interval-seconds", Integer.valueOf(2));
            }
            if (config.getProperty("settings.tick-catchup.warn-interval-seconds") == null) {
                config.setProperty("settings.tick-catchup.warn-interval-seconds", Integer.valueOf(30));
            }

            config.save();
            appendLog("[GUI] Startup readiness settings saved successfully.", LogType.INFO);
        } catch (Exception e) {
            appendLog("[GUI] Error saving poseidon.yml startup settings: " + e.getMessage(), LogType.ERROR);
        }
    }
    
    private JPanel createSection(String title) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(BG_PANEL);
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BG_INPUT),
                title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                TEXT_COLOR
            ),
            new EmptyBorder(5, 10, 10, 10)
        ));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        return section;
    }
    
    private void addInfoRow(JPanel panel, String label, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        row.setBackground(BG_PANEL);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        
        JLabel labelComp = new JLabel(label);
        labelComp.setForeground(new Color(150, 150, 150));
        row.add(labelComp);
        
        JLabel valueComp = new JLabel(value);
        valueComp.setForeground(TEXT_COLOR);
        row.add(valueComp);
        
        panel.add(row);
    }
    
    private void styleButton(JButton button, Color bg, Color fg) {
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.darker(), 1),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    
    private void setupLogging() {
        // Create custom handler to capture log output
        guiLogHandler = new Handler() {
            private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            
            @Override
            public void publish(LogRecord record) {
                if (record == null) return;
                
                String message = record.getMessage();
                if (message == null) return;

                LogType type = LogType.INFO;
                if (record.getLevel() == Level.SEVERE || record.getLevel() == Level.WARNING) {
                    type = LogType.ERROR;
                } else if (isChatMessage(message)) {
                    type = LogType.CHAT;
                } else if (message.startsWith("/") || message.contains("issued server command")) {
                    type = LogType.COMMAND;
                }

                String time = sdf.format(new Date(record.getMillis()));
                String label = type == LogType.CHAT ? "CHAT" : record.getLevel().getName();
                String formatted = "[" + time + "] [" + label + "] " + message;
                
                appendLog(formatted, type);
            }
            
            @Override
            public void flush() {}
            
            @Override
            public void close() throws SecurityException {}
        };
        guiLogHandler.setLevel(Level.ALL); // Capture all log levels
        
        Logger rootLogger = Logger.getLogger("Minecraft");
        rootLogger.setLevel(Level.ALL); // Ensure logger captures all levels
        rootLogger.addHandler(guiLogHandler);
        
        // Also add to global logger
        Logger globalLogger = Logger.getLogger("");
        globalLogger.addHandler(guiLogHandler);
        
        // Also capture System.out and System.err
        PrintStream guiOut = new PrintStream(new OutputStream() {
            private StringBuilder buffer = new StringBuilder();
            
            @Override
            public void write(int b) {
                if (b == '\n') {
                    String line = buffer.toString();
                    if (!line.isEmpty() && !isConsoleFormattedChatEcho(line)) {
                        appendLog(line, LogType.INFO);
                    }
                    buffer = new StringBuilder();
                } else {
                    buffer.append((char) b);
                }
            }
        });

        PrintStream guiErr = new PrintStream(new OutputStream() {
            private StringBuilder buffer = new StringBuilder();

            @Override
            public void write(int b) {
                if (b == '\n') {
                    String line = buffer.toString();
                    if (!line.isEmpty()) {
                        appendLog(line, LogType.ERROR);
                    }
                    buffer = new StringBuilder();
                } else {
                    buffer.append((char) b);
                }
            }
        });
        
        // Keep original streams
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        
        // Create tee streams that write to both
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                originalOut.write(b);
                guiOut.write(b);
            }
        }));
        System.setErr(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                originalErr.write(b);
                guiErr.write(b);
            }
        }));
    }
    
    private boolean isChatMessage(String message) {
        if (message == null) {
            return false;
        }
        return message.matches("^<[^>]{1,32}>\\s.*");
    }

    private boolean isConsoleFormattedChatEcho(String line) {
        if (line == null) {
            return false;
        }
        Matcher matcher = CONSOLE_FORMATTED_LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return false;
        }
        String message = matcher.group(2);
        return isChatMessage(message);
    }

    private void appendLog(String message, LogType type) {
        LogEntry entry = new LogEntry(message, type);
        allLogs.add(entry);
        
        // Keep log size manageable
        while (allLogs.size() > 5000) {
            allLogs.remove(0);
        }
        
        // Only show if matches filter
        if (matchesFilter(entry)) {
            SwingUtilities.invokeLater(() -> appendToConsole(entry));
        }

        if (entry.type == LogType.CHAT) {
            chatOnlyLogs.add(entry);
            while (chatOnlyLogs.size() > 5000) {
                chatOnlyLogs.remove(0);
            }
            SwingUtilities.invokeLater(() -> appendToChatLogConsole(entry));
        }
    }
    
    private boolean matchesFilter(LogEntry entry) {
        switch (currentFilter) {
            case "Chat":
                return entry.type == LogType.CHAT;
            case "Errors":
                return entry.type == LogType.ERROR;
            case "Commands":
                return entry.type == LogType.COMMAND;
            default:
                return true;
        }
    }
    
    private void appendToConsole(LogEntry entry) {
        try {
            Style style = consolePane.addStyle("style", null);
            
            switch (entry.type) {
                case ERROR:
                    StyleConstants.setForeground(style, ERROR_COLOR);
                    break;
                case CHAT:
                    StyleConstants.setForeground(style, CHAT_COLOR);
                    break;
                case COMMAND:
                    StyleConstants.setForeground(style, COMMAND_COLOR);
                    break;
                default:
                    StyleConstants.setForeground(style, TEXT_COLOR);
            }
            
            consoleDoc.insertString(consoleDoc.getLength(), entry.message + "\n", style);
            
            // Auto-scroll to bottom
            consolePane.setCaretPosition(consoleDoc.getLength());
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    private void appendToChatLogConsole(LogEntry entry) {
        if (entry == null || entry.type != LogType.CHAT || chatLogPane == null || chatLogDoc == null) {
            return;
        }
        try {
            Style style = chatLogPane.addStyle("chatStyle", null);
            StyleConstants.setForeground(style, CHAT_COLOR);
            chatLogDoc.insertString(chatLogDoc.getLength(), entry.message + "\n", style);
            chatLogPane.setCaretPosition(chatLogDoc.getLength());
        } catch (BadLocationException ignored) {
        }
    }
    
    private void refreshConsole() {
        SwingUtilities.invokeLater(() -> {
            try {
                consoleDoc.remove(0, consoleDoc.getLength());
                for (LogEntry entry : allLogs) {
                    if (matchesFilter(entry)) {
                        appendToConsole(entry);
                    }
                }
            } catch (BadLocationException e) {
                // Ignore
            }
        });
    }

    private void refreshChatLogConsole() {
        SwingUtilities.invokeLater(() -> {
            if (chatLogDoc == null) {
                return;
            }
            try {
                chatLogDoc.remove(0, chatLogDoc.getLength());
                for (LogEntry entry : chatOnlyLogs) {
                    appendToChatLogConsole(entry);
                }
            } catch (BadLocationException ignored) {
            }
        });
    }
    
    private void startUpdateTimer() {
        updateTimer = new javax.swing.Timer(1000, e -> updateStatus());
        updateTimer.start();
    }
    
    private void updateStatus() {
        // Update RAM usage
        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMem = runtime.maxMemory() / (1024 * 1024);
        int ramPercentage = (int) ((usedMem * 100) / maxMem);
        
        ramUsageLabel.setText(String.format("RAM: %d MB / %d MB", usedMem, maxMem));
        ramProgressBar.setValue(ramPercentage);
        ramProgressBar.setString(ramPercentage + "%");
        
        if (ramPercentage > 90) {
            ramProgressBar.setForeground(ERROR_COLOR);
        } else if (ramPercentage > 70) {
            ramProgressBar.setForeground(new Color(230, 180, 80));
        } else {
            ramProgressBar.setForeground(ACCENT_COLOR);
        }
        
        // Update CPU usage
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double cpuLoad = osBean.getSystemLoadAverage();
            int cpuPercentage;
            
            // getSystemLoadAverage returns -1 if not available (on Windows)
            // Try to get process CPU if available
            if (cpuLoad < 0) {
                // On Windows, try using com.sun.management
                if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                    com.sun.management.OperatingSystemMXBean sunBean = 
                        (com.sun.management.OperatingSystemMXBean) osBean;
                    cpuPercentage = (int) (sunBean.getProcessCpuLoad() * 100);
                } else {
                    cpuPercentage = 0;
                }
            } else {
                // Unix-like systems
                int processors = runtime.availableProcessors();
                cpuPercentage = (int) ((cpuLoad / processors) * 100);
            }
            
            cpuPercentage = Math.max(0, Math.min(100, cpuPercentage));
            
            cpuUsageLabel.setText("CPU: " + cpuPercentage + "%");
            cpuProgressBar.setValue(cpuPercentage);
            cpuProgressBar.setString(cpuPercentage + "%");
            
            if (cpuPercentage > 90) {
                cpuProgressBar.setForeground(ERROR_COLOR);
            } else if (cpuPercentage > 70) {
                cpuProgressBar.setForeground(new Color(230, 180, 80));
            } else {
                cpuProgressBar.setForeground(new Color(156, 86, 214)); // Purple
            }
        } catch (Exception e) {
            cpuUsageLabel.setText("CPU: N/A");
            cpuProgressBar.setValue(0);
        }
        
        // Update server status
        if (server != null && serverStarted) {
            serverStatusLabel.setText("● Server Running");
            serverStatusLabel.setForeground(SUCCESS_COLOR);
            
            // Update player list
            updatePlayerList();
        } else {
            serverStatusLabel.setText("● Server Stopped");
            serverStatusLabel.setForeground(ERROR_COLOR);
            playerListModel.clear();
        }
    }
    
    private void updatePlayerList() {
        if (server == null || server.serverConfigurationManager == null) return;
        
        try {
            List<?> players = server.serverConfigurationManager.players;
            Set<String> currentPlayers = new HashSet<>();
            
            for (Object p : players) {
                if (p instanceof EntityPlayer) {
                    currentPlayers.add(((EntityPlayer) p).name);
                }
            }
            
            // Update list model
            SwingUtilities.invokeLater(() -> {
                // Remove players no longer online
                for (int i = playerListModel.size() - 1; i >= 0; i--) {
                    if (!currentPlayers.contains(playerListModel.get(i))) {
                        playerListModel.remove(i);
                    }
                }
                
                // Add new players
                for (String name : currentPlayers) {
                    if (!playerListModel.contains(name)) {
                        playerListModel.addElement(name);
                    }
                }
            });
        } catch (Exception e) {
            // Ignore
        }
    }
    
    private void startServer() {
        if (serverStarted) return;
        
        appendLog("[GUI] Starting server...", LogType.INFO);
        
        // Ensure GUI handler is attached to logger
        Logger minecraftLogger = Logger.getLogger("Minecraft");
        minecraftLogger.setLevel(Level.ALL);
        if (guiLogHandler != null) {
            // Check if already attached
            boolean hasHandler = false;
            for (Handler h : minecraftLogger.getHandlers()) {
                if (h == guiLogHandler) {
                    hasHandler = true;
                    break;
                }
            }
            if (!hasHandler) {
                minecraftLogger.addHandler(guiLogHandler);
            }
        }
        
        serverThread = new Thread(() -> {
            try {
                // Parse options and start server
                joptsimple.OptionParser parser = new joptsimple.OptionParser() {{
                    acceptsAll(java.util.Arrays.asList("c", "config"), "Properties file").withRequiredArg().ofType(java.io.File.class).defaultsTo(new java.io.File("server.properties"));
                    acceptsAll(java.util.Arrays.asList("P", "plugins"), "Plugin directory").withRequiredArg().ofType(java.io.File.class).defaultsTo(new java.io.File("plugins"));
                    acceptsAll(java.util.Arrays.asList("h", "host", "server-ip"), "Host").withRequiredArg().ofType(String.class);
                    acceptsAll(java.util.Arrays.asList("w", "world", "level-name"), "World").withRequiredArg().ofType(String.class);
                    acceptsAll(java.util.Arrays.asList("p", "port", "server-port"), "Port").withRequiredArg().ofType(Integer.class);
                    acceptsAll(java.util.Arrays.asList("o", "online-mode"), "Online mode").withRequiredArg().ofType(Boolean.class);
                    acceptsAll(java.util.Arrays.asList("s", "size", "max-players"), "Max players").withRequiredArg().ofType(Integer.class);
                    acceptsAll(java.util.Arrays.asList("d", "date-format"), "Date format").withRequiredArg().ofType(java.text.SimpleDateFormat.class);
                    acceptsAll(java.util.Arrays.asList("log-pattern"), "Log pattern").withRequiredArg().ofType(String.class).defaultsTo("server.log");
                    acceptsAll(java.util.Arrays.asList("log-limit"), "Log limit").withRequiredArg().ofType(Integer.class).defaultsTo(0);
                    acceptsAll(java.util.Arrays.asList("log-count"), "Log count").withRequiredArg().ofType(Integer.class).defaultsTo(1);
                    acceptsAll(java.util.Arrays.asList("log-append"), "Log append").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
                    acceptsAll(java.util.Arrays.asList("b", "bukkit-settings"), "Bukkit settings").withRequiredArg().ofType(java.io.File.class).defaultsTo(new java.io.File("bukkit.yml"));
                    acceptsAll(java.util.Arrays.asList("nojline"), "No JLine");
                    acceptsAll(java.util.Arrays.asList("nogui"), "No GUI");
                }};
                
                joptsimple.OptionSet options = parser.parse(serverArgs);
                
                // Disable JLine for GUI mode
                System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
                org.bukkit.craftbukkit.Main.useJline = false;
                
                net.minecraft.server.StatisticList.a();
                server = new MinecraftServer(options);
                serverStarted = true;
                
                // Verify GUI log handler is still attached after server init
                Logger mcLogger = Logger.getLogger("Minecraft");
                System.out.println("[GUI] Server initialized. Minecraft logger has " + mcLogger.getHandlers().length + " handlers");
                
                // Refresh all lists after server starts
                SwingUtilities.invokeLater(() -> {
                    refreshBannedList();
                    refreshOpsList();
                    refreshWhitelistList();
                });
                
                server.run();
            } catch (Throwable e) {
                appendLog("[GUI] Server startup/runtime failure: " + e.toString(), LogType.ERROR);
                appendThrowableToConsole(e);
                File dumpFile = writeStartupFailureDump(e);
                if (dumpFile != null) {
                    appendLog("[GUI] Wrote startup failure dump: " + dumpFile.getAbsolutePath(), LogType.ERROR);
                }
            } finally {
                serverStarted = false;
                server = null;
                appendLog("[GUI] Server stopped.", LogType.INFO);
                // Update UI to reflect server stopped
                SwingUtilities.invokeLater(() -> {
                    startButton.setText("Start Server");
                    styleButton(startButton, SUCCESS_COLOR, Color.WHITE);
                });
            }
        }, "Server Thread");
        
        serverThread.start();
    }

    private void appendThrowableToConsole(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        Throwable cursor = throwable;
        int depth = 0;
        while (cursor != null && depth < 8) {
            appendLog("[GUI] " + (depth == 0 ? "Exception" : "Caused by") + ": " + cursor.toString(), LogType.ERROR);
            StackTraceElement[] trace = cursor.getStackTrace();
            int maxFrames = Math.min(18, trace.length);
            for (int i = 0; i < maxFrames; i++) {
                appendLog("[GUI]   at " + trace[i].toString(), LogType.ERROR);
            }
            if (trace.length > maxFrames) {
                appendLog("[GUI]   ... " + (trace.length - maxFrames) + " more frames", LogType.ERROR);
            }
            cursor = cursor.getCause();
            depth++;
        }
    }

    private File writeStartupFailureDump(Throwable throwable) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        File dump = new File("startup-failure-" + sdf.format(new Date()) + ".log");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(dump)));
            writer.println("=== Uberbukkit GUI Startup Failure Dump ===");
            writer.println("timestamp=" + new Date());
            writer.println("cwd=" + new File(".").getAbsolutePath());
            writer.println("java.version=" + System.getProperty("java.version"));
            writer.println("java.vendor=" + System.getProperty("java.vendor"));
            writer.println("os.name=" + System.getProperty("os.name"));
            writer.println("os.arch=" + System.getProperty("os.arch"));
            writer.println("serverStartedFlag=" + serverStarted);
            writer.println("serverThread=" + (serverThread != null ? serverThread.getName() : "<null>"));
            writer.println();
            writer.println("=== Exception Stack Trace ===");
            if (throwable != null) {
                throwable.printStackTrace(writer);
            } else {
                writer.println("<none>");
            }
            writer.println();
            writer.println("=== Last Console Entries (tail) ===");
            int start = Math.max(0, allLogs.size() - 300);
            for (int i = start; i < allLogs.size(); i++) {
                LogEntry entry = allLogs.get(i);
                writer.println("[" + entry.type.name() + "] " + entry.message);
            }
            return dump;
        } catch (IOException ioException) {
            appendLog("[GUI] Failed to write startup failure dump: " + ioException.getMessage(), LogType.ERROR);
            return null;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
    
    private void stopServer() {
        if (server != null) {
            appendLog("[GUI] Stopping server...", LogType.INFO);
            server.issueCommand("stop", server);
        }
    }
    
    private void confirmAndShutdown() {
        if (serverStarted) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "Server is still running. Stop server and exit?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (result == JOptionPane.YES_OPTION) {
                stopServer();
                
                // Wait for server to stop
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {}
                    System.exit(0);
                }).start();
            }
        } else {
            System.exit(0);
        }
    }
    
    // Custom cell renderer for player list with face icons
    private class PlayerListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(5, 10, 5, 10));
            
            String playerName = value != null ? value.toString() : "";
            
            // Set player face icon
            ImageIcon faceIcon = getPlayerFace(playerName);
            if (faceIcon != null) {
                label.setIcon(faceIcon);
                label.setIconTextGap(10);
            } else {
                // Use a placeholder while loading
                label.setIcon(createPlaceholderIcon());
                label.setIconTextGap(10);
            }
            
            if (!isSelected) {
                label.setBackground(index % 2 == 0 ? BG_DARK : new Color(35, 35, 35));
            }
            
            return label;
        }
    }
    
    /**
     * Gets the player's face icon from cache or starts async fetch
     */
    private ImageIcon getPlayerFace(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        
        String key = username.toLowerCase();
        
        // Check cache first
        ImageIcon cached = playerFaceCache.get(key);
        if (cached != null) {
            return cached;
        }
        
        // Start async fetch if not already in progress
        if (!faceFetchInProgress.contains(key)) {
            faceFetchInProgress.add(key);
            fetchPlayerFaceAsync(username);
        }
        
        return null;
    }
    
    /**
     * Fetches player face from Mojang API asynchronously (same method as game client SkinManager)
     */
    private void fetchPlayerFaceAsync(final String username) {
        new Thread(() -> {
            try {
                // Step 1: Get UUID from username (try multiple endpoints like SkinManager)
                String uuid = fetchPlayerUUID(username);
                if (uuid == null) {
                    playerFaceCache.put(username.toLowerCase(), createPlaceholderIcon());
                    return;
                }
                
                // Step 2: Get profile with textures from session server
                String skinUrl = fetchSkinUrlFromProfile(uuid);
                if (skinUrl == null) {
                    playerFaceCache.put(username.toLowerCase(), createPlaceholderIcon());
                    return;
                }
                
                // Step 3: Download skin and extract face
                BufferedImage skin = ImageIO.read(new URL(skinUrl));
                if (skin != null) {
                    // Extract face from skin (8x8 pixels at position 8,8 on 64x64 skin)
                    // Also overlay the hat layer (8x8 at position 40,8)
                    BufferedImage face = extractFaceFromSkin(skin);
                    
                    // Scale up to display size (SCALE_REPLICATE for pixelated/nearest-neighbor look)
                    Image scaled = face.getScaledInstance(FACE_SIZE, FACE_SIZE, Image.SCALE_REPLICATE);
                    BufferedImage scaledBuf = new BufferedImage(FACE_SIZE, FACE_SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = scaledBuf.createGraphics();
                    g.drawImage(scaled, 0, 0, null);
                    g.dispose();
                    
                    ImageIcon icon = new ImageIcon(scaledBuf);
                    playerFaceCache.put(username.toLowerCase(), icon);
                    
                    // Refresh the player list to show the icon
                    SwingUtilities.invokeLater(() -> {
                        if (playerList != null) {
                            playerList.repaint();
                        }
                    });
                } else {
                    playerFaceCache.put(username.toLowerCase(), createPlaceholderIcon());
                }
            } catch (Exception e) {
                // Failed to fetch, use placeholder
                playerFaceCache.put(username.toLowerCase(), createPlaceholderIcon());
            } finally {
                faceFetchInProgress.remove(username.toLowerCase());
            }
        }, "FaceFetch-" + username).start();
    }
    
    /**
     * Fetches player UUID from username using Mojang API (same as SkinManager.fetchPlayerUUID)
     */
    private String fetchPlayerUUID(String username) {
        for (String endpoint : UUID_ENDPOINTS) {
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new URL(endpoint + username).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int code = conn.getResponseCode();
                if (code == 204 || code == 404) {
                    continue; // Username not found on this endpoint
                }
                if (code != 200) {
                    continue;
                }
                
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                
                String json = sb.toString();
                if (json == null || json.isEmpty()) continue;
                json = json.replaceAll("\\s+", "");
                
                // Parse "id" field from JSON
                int start = json.indexOf("\"id\":\"") + 6;
                int end = json.indexOf('"', start);
                if (start > 5 && end > start) {
                    return json.substring(start, end);
                }
            } catch (Exception e) {
                // Try next endpoint
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }
    
    /**
     * Fetches skin URL from player profile (same as SkinManager.fetchPlayerProfile)
     */
    private String fetchSkinUrlFromProfile(String uuid) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new URL(SESSION_SERVER_URL + uuid).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) return null;
            
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            
            String json = sb.toString();
            if (json == null) return null;
            json = json.replaceAll("\\s+", "");
            
            // Find properties array and extract value
            int prop = json.indexOf("\"properties\":[");
            if (prop < 0) return null;
            int valStart = json.indexOf("\"value\":\"", prop);
            if (valStart < 0) return null;
            valStart += 9;
            int valEnd = json.indexOf('"', valStart);
            if (valEnd < valStart) return null;
            
            // Decode base64 textures payload
            String decoded = new String(Base64.getDecoder().decode(json.substring(valStart, valEnd)));
            decoded = decoded.replaceAll("\\s+", "");
            
            // Extract skin URL from textures JSON
            int skinStart = decoded.indexOf("\"SKIN\":{");
            if (skinStart >= 0) {
                int urlSt = decoded.indexOf("\"url\":\"", skinStart);
                if (urlSt >= 0) {
                    urlSt += 7;
                    int urlEnd = decoded.indexOf('"', urlSt);
                    return decoded.substring(urlSt, urlEnd);
                }
            }
        } catch (Exception e) {
            // Failed to fetch profile
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
    
    /**
     * Extracts the face from a Minecraft skin image.
     * Face is at 8,8 (8x8 pixels), hat overlay is at 40,8 (8x8 pixels)
     */
    private BufferedImage extractFaceFromSkin(BufferedImage skin) {
        BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = face.createGraphics();
        
        // Draw base face (8x8 at position 8,8)
        g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
        
        // Overlay hat layer if skin is 64x64 (modern format)
        if (skin.getHeight() >= 64) {
            BufferedImage hat = skin.getSubimage(40, 8, 8, 8);
            g.drawImage(hat, 0, 0, null);
        }
        
        g.dispose();
        return face;
    }
    
    /**
     * Creates a placeholder icon for players while their face loads
     */
    private ImageIcon createPlaceholderIcon() {
        BufferedImage placeholder = new BufferedImage(FACE_SIZE, FACE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = placeholder.createGraphics();
        g.setColor(BG_INPUT);
        g.fillRect(0, 0, FACE_SIZE, FACE_SIZE);
        g.setColor(TEXT_COLOR);
        g.drawRect(0, 0, FACE_SIZE - 1, FACE_SIZE - 1);
        // Draw a simple head shape
        g.setColor(new Color(139, 90, 43)); // Brown for generic player
        g.fillRect(4, 4, FACE_SIZE - 8, FACE_SIZE - 8);
        g.dispose();
        return new ImageIcon(placeholder);
    }
    
    // Log entry class
    private static class LogEntry {
        final String message;
        final LogType type;
        
        LogEntry(String message, LogType type) {
            this.message = message;
            this.type = type;
        }
    }
    
    // Log type enum
    private enum LogType {
        INFO, ERROR, CHAT, COMMAND
    }
    
    // Entry point for GUI mode
    public static void main(String[] args) {
        // Set system properties for better rendering
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI(args);
            gui.setVisible(true);
            
            // Auto-start the server after GUI is visible
            gui.autoStartServer();
        });
    }
    
    /**
     * Automatically starts the server.
     * Called when the GUI is launched to provide immediate server startup.
     */
    private void autoStartServer() {
        if (!serverStarted) {
            appendLog("Auto-starting server...", LogType.INFO);
            startServer();
            startButton.setText("Stop Server");
            styleButton(startButton, ERROR_COLOR, Color.WHITE);
        }
    }
}
