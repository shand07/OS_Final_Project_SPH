import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;

public class TicTacToeServer {

    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 2;
    private static Semaphore turnSemaphore = new Semaphore(1);  // Semaphore to manage turn-taking
    private static String[] board = new String[9];
    private static int currentPlayer = 0;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static boolean gameActive = true;

    public static void main(String[] args) {
        System.out.println("Server is running...");
        Arrays.fill(board, null);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (clients.size() < MAX_PLAYERS) {
                Socket socket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(socket, clients.size());
                clients.add(clientHandler);
                new Thread(clientHandler).start();
                System.out.println("Player connected: Player " + (clients.size()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized boolean makeMove(int player, int position) {
        if (position >= 0 && position < 9 && board[position] == null) {
            board[position] = (player == 0) ? "X" : "O";
            return true;
        }
        return false;
    }

    private static boolean checkWinner() {
        int[][] winPatterns = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // Rows
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // Columns
            {0, 4, 8}, {2, 4, 6}             // Diagonals
        };

        for (int[] pattern : winPatterns) {
            if (board[pattern[0]] != null &&
                board[pattern[0]].equals(board[pattern[1]]) &&
                board[pattern[1]].equals(board[pattern[2]])) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void resetGame() {
        Arrays.fill(board, null);
        currentPlayer = 0;
        gameActive = true;
        System.out.println("Game reset successfully!");
    }

    private static synchronized void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.out.println(message);
        }
    }

    private static boolean handleGameReset() throws IOException {
        broadcastMessage("Enter 1 to restart the game or any other key to exit.");

        boolean[] playerResponses = new boolean[clients.size()];
        for (int i = 0; i < clients.size(); i++) {
            ClientHandler client = clients.get(i);
            client.out.println("Player " + (i + 1) + ", do you want to restart?");
            String response = client.in.readLine();
            playerResponses[i] = "1".equals(response);
        }

        if (playerResponses[0] && playerResponses[1]) {
            resetGame();
            broadcastMessage("Both players agreed to restart! Resetting the game...");
            broadcastMessage("New game starting!\n" + formatBoard());
            return true;
        } else {
            broadcastMessage("Game over. One or both players chose to exit.");
            return false;
        }
    }

    private static String formatBoard() {
        StringBuilder formattedBoard = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            formattedBoard.append(board[i] == null ? "-" : board[i]);
            if ((i + 1) % 3 == 0) {
                formattedBoard.append("\n");
            } else {
                formattedBoard.append(" | ");
            }
        }
        return formattedBoard.toString();
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private int playerId;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket, int playerId) {
            this.socket = socket;
            this.playerId = playerId;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("Welcome to Tic-Tac-Toe! You are Player " + (playerId + 1));
                if (playerId == 1) {
                    synchronized (clients) {
                        clients.notifyAll();
                    }
                } else {
                    out.println("Waiting for another player to join...");
                    synchronized (clients) {
                        while (clients.size() < MAX_PLAYERS) {
                            clients.wait();
                        }
                    }
                }

                broadcastMessage("Game starting!\n" + formatBoard());

                while (gameActive) {
                    if (playerId == currentPlayer) {
                        turnSemaphore.acquire();  // Block other players' turns
                        out.println("Your turn. Enter a position (0-8):");
                        String input = in.readLine();
                        try {
                            int position = Integer.parseInt(input);

                            if (makeMove(playerId, position)) {
                                broadcastMessage("Player " + (playerId + 1) + " made a move.");
                                broadcastMessage("Current board:\n" + formatBoard());

                                if (checkWinner()) {
                                    broadcastMessage("Player " + (playerId + 1) + " wins!");
                                    gameActive = handleGameReset();
                                } else if (Arrays.stream(board).allMatch(Objects::nonNull)) {
                                    broadcastMessage("It's a draw!");
                                    gameActive = handleGameReset();
                                } else {
                                    currentPlayer = (currentPlayer + 1) % MAX_PLAYERS;
                                }
                            } else {
                                out.println("Invalid move. Try again.");
                            }
                        } catch (NumberFormatException e) {
                            out.println("Invalid input. Enter a number between 0 and 8.");
                        } finally {
                            turnSemaphore.release();  // Release the semaphore to let the next player take their turn
                        }
                    } else {
                        out.println("Waiting for Player " + (currentPlayer + 1) + "'s turn.");
                        Thread.sleep(100);
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
