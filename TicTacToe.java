import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * A two-player networked Tic-Tac-Toe game.
 * Player 1 ('X') starts. Connects to localhost:12345.
 */
public class TicTacToe {
    // Game state
    private char[][] gameBoard = new char[3][3];
    private String playerName = "";
    private boolean gameActive = true;
    private boolean myTurn = false;
    private boolean isConnected = false;

    // Network
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // Player info
    private String opponentName = "";
    private boolean isPlayer1 = false;
    private char myMark = ' ';
    private char oppMark = ' ';

    // Composition: Own a JFrame instead of extending it
    private JFrame window;

    // Scores
    private int p1Wins = 0;
    private int p2Wins = 0;
    private int draws = 0;

    // GUI components
    private JTextField nameField;
    private JButton submitButton;
    private JLabel messageLabel;
    private JButton[][] buttons = new JButton[3][3];
    private JLabel p1WinLabel;
    private JLabel p2WinLabel;
    private JLabel drawLabel;
    private JLabel timeLabel;
    private Timer timeTimer;
    private SimpleDateFormat timeFormat;

    /**
     * Constructs the Tic Tac Toe frame
     */
    public TicTacToe() {
        // window
        window = new JFrame("Tic Tac Toe");
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
        window.setLayout(new BorderLayout());

        // Current time
        timeFormat = new SimpleDateFormat("HH:mm:ss");
        timeLabel = new JLabel("Current Time: " + timeFormat.format(new Date()));
        timeTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timeLabel.setText("Current Time: " + timeFormat.format(new Date()));
            }
        });
        timeTimer.start();

        // Menu bar
        JMenuBar menuBar = new JMenuBar();

        JMenu controlMenu = new JMenu("Control");
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> disconnect());
        controlMenu.add(exitItem);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem instructionItem = new JMenuItem("Instruction");
        instructionItem.addActionListener(e -> showInstructions());
        helpMenu.add(instructionItem);

        menuBar.add(controlMenu);
        menuBar.add(helpMenu);
        window.setJMenuBar(menuBar);

        // Message label
        messageLabel = new JLabel("Enter your player name...");
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Board panel
        JPanel boardPanel = new JPanel(new GridLayout(3, 3));
        ActionListener boardListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        if (e.getSource() == buttons[i][j]) {
                            makePlayerMove(i, j);
                        }
                    }
                }
            }
        };
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j] = new JButton("");
                buttons[i][j].setFont(new Font("Arial", Font.BOLD, 72));
                buttons[i][j].setPreferredSize(new Dimension(150, 150));
                buttons[i][j].addActionListener(boardListener);
                gameBoard[i][j] = ' ';
                boardPanel.add(buttons[i][j]);
            }
        }

        // Scores panel
        JPanel scoresPanel = new JPanel(new GridLayout(4, 1, 0, 10));
        JLabel scoreHeader = new JLabel("Score");
        scoreHeader.setHorizontalAlignment(SwingConstants.LEFT);
        p1WinLabel = new JLabel("P1 Wins: 0");
        p1WinLabel.setHorizontalAlignment(SwingConstants.LEFT);
        p2WinLabel = new JLabel("P2 Wins: 0");
        p2WinLabel.setHorizontalAlignment(SwingConstants.LEFT);
        drawLabel = new JLabel("Draws: 0");
        drawLabel.setHorizontalAlignment(SwingConstants.LEFT);
        scoresPanel.add(scoreHeader);
        scoresPanel.add(p1WinLabel);
        scoresPanel.add(p2WinLabel);
        scoresPanel.add(drawLabel);
        scoresPanel.setPreferredSize(new Dimension(150, 200));

        // Game center panel
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(boardPanel, BorderLayout.WEST);
        centerPanel.add(scoresPanel, BorderLayout.EAST);

        // Main game panel
        JPanel gamePanel = new JPanel(new BorderLayout());
        gamePanel.add(messageLabel, BorderLayout.NORTH);
        gamePanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel for name input and time
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));

        JPanel namePanel = new JPanel(new FlowLayout());
        namePanel.add(new JLabel("Enter your name:"));
        nameField = new JTextField(10);
        submitButton = new JButton("Submit");
        submitButton.addActionListener(e -> submitName());
        namePanel.add(nameField);
        namePanel.add(submitButton);

        bottomPanel.add(namePanel);
        bottomPanel.add(timeLabel); // On new line

        // Add panels to window
        window.add(gamePanel, BorderLayout.CENTER);
        window.add(bottomPanel, BorderLayout.SOUTH);

        window.pack();
        window.setSize(700, 600);
        window.setResizable(false);
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }

    /**
     * Submit name and connect to server
     */
    private void submitName() {
        String input = nameField.getText().trim();
        if (input.isEmpty()) {
            return;
        }
        playerName = input;
        nameField.setEditable(false);
        submitButton.setEnabled(false);
        window.setTitle("Tic Tac Toe - Player: " + playerName);
        messageLabel.setText("WELCOME " + playerName.toUpperCase() + " - Waiting for opponent...");
        try {
            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(playerName);
            isConnected = true;
            new Thread(this::listenToServer).start();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(window, "Failed to connect to server.");
        }
    }

    /**
     * Listen for server messages
     */
    private void listenToServer() {
        try {
            String line;
            while (isConnected && (line = in.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts[0].equals("START")) {
                    isPlayer1 = parts[1].equals("P1");
                    myMark = isPlayer1 ? 'X' : 'O';
                    oppMark = isPlayer1 ? 'O' : 'X';
                    opponentName = parts[2];
                    SwingUtilities.invokeLater(() -> {
                        window.setTitle("Tic Tac Toe - " + (isPlayer1 ? "P1: " : "P2: ") + playerName + " vs " + opponentName);
                        updateMessage("Game started!");
                    });
                } else if (parts[0].equals("UPDATE")) {
                    int row = Integer.parseInt(parts[1]);
                    int col = Integer.parseInt(parts[2]);
                    char mark = parts[3].charAt(0);
                    gameBoard[row][col] = mark;
                    SwingUtilities.invokeLater(() -> {
                        buttons[row][col].setText(String.valueOf(mark));
                        buttons[row][col].setForeground(mark == 'X' ? Color.GREEN : Color.RED);
                        buttons[row][col].setEnabled(false);
                    });
                } else if (parts[0].equals("MESSAGE")) {
                    String msg = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
                    if (msg.equals("Your turn to move.")) {
                        myTurn = true;
                    } else if (msg.equals("Wait for your opponent to move.")) {
                        myTurn = false;
                    } else if (msg.equals("Valid move, wait for your opponent.")) {
                        myTurn = false;
                    } else if (msg.equals("Your opponent has moved, now is your turn.")) {
                        myTurn = true;
                    }
                    SwingUtilities.invokeLater(() -> updateMessage(msg));
                } else if (parts[0].equals("GAMEOVER")) {
                    gameActive = false;
                    String type = parts[1];
                    String resultMsg;
                    if (type.equals("WIN")) {
                        resultMsg = "You win!";
                        if (isPlayer1) {
                            p1Wins++;
                        } else {
                            p2Wins++;
                        }
                    } else if (type.equals("LOSE")) {
                        resultMsg = "You lose!";
                        if (isPlayer1) {
                            p2Wins++;
                        } else {
                            p1Wins++;
                        }
                    } else { // DRAW
                        resultMsg = "It's a draw!";
                        draws++;
                    }
                    final String finalResultMsg = resultMsg;
                    SwingUtilities.invokeLater(() -> {
                        updateScores();
                        disableButtons();
                        int choice = JOptionPane.showConfirmDialog(window, finalResultMsg + "\n\nDo you want to restart the game?", "Game Over",
                                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                        if (choice == JOptionPane.YES_OPTION) {
                            out.println("RESTART yes");
                        } else {
                            out.println("RESTART no");
                        }
                    });
                } else if (parts[0].equals("RESET")) {
                    // Reset state
                    for (int i = 0; i < 3; i++) {
                        for (int j = 0; j < 3; j++) {
                            gameBoard[i][j] = ' ';
                        }
                    }
                    gameActive = true;
                    // Reset UI on EDT
                    SwingUtilities.invokeLater(() -> {
                        for (int i = 0; i < 3; i++) {
                            for (int j = 0; j < 3; j++) {
                                buttons[i][j].setText("");
                                buttons[i][j].setEnabled(true);
                            }
                        }
                    });
                } else if (parts[0].equals("END") || parts[0].equals("OPPONENT_LEFT")) {
                    String msg = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(window, msg, "Game End", JOptionPane.INFORMATION_MESSAGE);
                        disconnect();
                    });
                    return; // Exit the loop to avoid further reads
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            disconnect();
        }
    }

    /**
     * Processes the player's move at the specified position
     *
     * @param row the row index (0-2)
     * @param col the column index (0-2)
     */
    private void makePlayerMove(int row, int col) {
        if (!gameActive || !myTurn || gameBoard[row][col] != ' ') {
            return; // Invalid move
        }
        out.println("MOVE " + row + " " + col);
    }

    /**
     * Updates the score labels
     */
    private void updateScores() {
        p1WinLabel.setText("P1 Wins: " + p1Wins);
        p2WinLabel.setText("P2 Wins: " + p2Wins);
        drawLabel.setText("Draws: " + draws);
    }

    /**
     * Updates the message label
     *
     * @param message the new message text
     */
    private void updateMessage(String message) {
        messageLabel.setText(message);
    }

    /**
     * Disables all board buttons
     */
    private void disableButtons() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j].setEnabled(false);
            }
        }
    }

    /**
     * Disconnects from server
     */
    private void disconnect() {
        if (out != null) {
            out.println("DISCONNECT");
            out.close();
        }
        if (in != null) {
            try {
                in.close();
            } catch (IOException ex) {
                // ignore
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                // ignore
            }
        }
        isConnected = false;
        System.exit(0);
    }

    /**
     * Shows the instructions
     */
    private void showInstructions() {
        String instructions = "Tic-Tac-Toe is a two-player game over network.\n" +
                "• Players alternate turns, P1 ('X') starts.\n" +
                "• Mark empty spaces on the 3x3 board.\n" +
                "• First to three in a row/column/diagonal wins.\n" +
                "• Valid move: empty cell, your turn.\n" +
                "• Game ends in win, loss, or draw.\n" +
                "• Restart or exit after each round.\n" +
                "• Disconnecting notifies opponent.";
        JOptionPane.showMessageDialog(window, instructions, "Instructions",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Launch the application.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TicTacToe();
        });
    }
}
