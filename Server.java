import java.io.*;
import java.net.*;
import java.util.Arrays;

/**
 * Server for two-player Tic-Tac-Toe.
 * Listens on port 12345, pairs two clients, manages game state.
 */
public class Server {
    private ServerSocket serverSocket;
    private Socket p1Socket, p2Socket;
    private PrintWriter out1, out2;
    private BufferedReader in1, in2;
    private char[][] board = new char[3][3];
    private boolean p1Turn = true;
    private boolean gameOver = false;
    private boolean waitingForRestart = false;
    private String p1Choice = null, p2Choice = null;
    private String p1Name, p2Name;

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server listening on port " + port);
            p1Socket = serverSocket.accept();
            in1 = new BufferedReader(new InputStreamReader(p1Socket.getInputStream()));
            out1 = new PrintWriter(p1Socket.getOutputStream(), true);
            p1Name = in1.readLine();
            System.out.println("P1 connected: " + p1Name);
            p2Socket = serverSocket.accept();
            in2 = new BufferedReader(new InputStreamReader(p2Socket.getInputStream()));
            out2 = new PrintWriter(p2Socket.getOutputStream(), true);
            p2Name = in2.readLine();
            System.out.println("P2 connected: " + p2Name);
            // Init board
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    board[i][j] = ' ';
                }
            }
            // Start game
            out1.println("START P1 " + p2Name);
            out2.println("START P2 " + p1Name);
            out1.println("MESSAGE Your turn to move.");
            out2.println("MESSAGE Wait for your opponent to move.");
            // Handle clients
            new Thread(() -> handleClient(in1, out1, out2, true)).start();
            new Thread(() -> handleClient(in2, out2, out1, false)).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(BufferedReader in, PrintWriter myOut, PrintWriter oppOut, boolean isP1) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts[0].equals("MOVE")) {
                    if (gameOver || waitingForRestart) continue;
                    int row = Integer.parseInt(parts[1]);
                    int col = Integer.parseInt(parts[2]);
                    boolean currentTurn = isP1 ? p1Turn : !p1Turn;
                    if (board[row][col] == ' ' && currentTurn) {
                        char mark = isP1 ? 'X' : 'O';
                        board[row][col] = mark;
                        // Broadcast update
                        myOut.println("UPDATE " + row + " " + col + " " + mark);
                        oppOut.println("UPDATE " + row + " " + col + " " + mark);
                        if (checkWin(mark)) {
                            myOut.println("GAMEOVER WIN You win!");
                            oppOut.println("GAMEOVER LOSE You lose!");
                            gameOver = true;
                            waitingForRestart = true;
                            p1Choice = null;
                            p2Choice = null;
                        } else if (isBoardFull()) {
                            myOut.println("GAMEOVER DRAW It's a draw!");
                            oppOut.println("GAMEOVER DRAW It's a draw!");
                            gameOver = true;
                            waitingForRestart = true;
                            p1Choice = null;
                            p2Choice = null;
                        } else {
                            p1Turn = !p1Turn;
                            myOut.println("MESSAGE Valid move, wait for your opponent.");
                            oppOut.println("MESSAGE Your opponent has moved, now is your turn.");
                        }
                    }
                } else if (parts[0].equals("RESTART")) {
                    if (!waitingForRestart) continue;
                    String choice = parts[1];
                    if (isP1) {
                        p1Choice = choice;
                    } else {
                        p2Choice = choice;
                    }
                    if (p1Choice != null && p2Choice != null) {
                        if (p1Choice.equals("yes") && p2Choice.equals("yes")) {
                            // Reset
                            for (int i = 0; i < 3; i++) {
                                for (int j = 0; j < 3; j++) {
                                    board[i][j] = ' ';
                                }
                            }
                            gameOver = false;
                            waitingForRestart = false;
                            p1Turn = true;
                            out1.println("RESET");
                            out1.println("MESSAGE Your turn to move.");
                            out2.println("RESET");
                            out2.println("MESSAGE Wait for your opponent to move.");
                        } else {
                            out1.println("END Game Ends. One player chose not to continue.");
                            out2.println("END Game Ends. One player chose not to continue.");
                            p1Socket.close();
                            p2Socket.close();
                        }
                        p1Choice = null;
                        p2Choice = null;
                    }
                } else if (parts[0].equals("DISCONNECT")) {
                    String msg = "OPPONENT_LEFT Game Ends. One of the players left.";
                    if (isP1) {
                        out2.println(msg);
                    } else {
                        out1.println(msg);
                    }
                    p1Socket.close();
                    p2Socket.close();
                    break;
                }
            }
            // Disconnected
            String msg = "OPPONENT_LEFT Game Ends. One of the players left.";
            if (isP1) {
                if (out2 != null) out2.println(msg);
            } else {
                if (out1 != null) out1.println(msg);
            }
            if (p1Socket != null) p1Socket.close();
            if (p2Socket != null) p2Socket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkWin(char mark) {
        // Rows
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == mark && board[i][1] == mark && board[i][2] == mark) {
                return true;
            }
        }
        // Columns
        for (int j = 0; j < 3; j++) {
            if (board[0][j] == mark && board[1][j] == mark && board[2][j] == mark) {
                return true;
            }
        }
        // Diagonals
        if (board[0][0] == mark && board[1][1] == mark && board[2][2] == mark) {
            return true;
        }
        if (board[0][2] == mark && board[1][1] == mark && board[2][0] == mark) {
            return true;
        }
        return false;
    }

    private boolean isBoardFull() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == ' ') {
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        new Server(12345);
    }
}
