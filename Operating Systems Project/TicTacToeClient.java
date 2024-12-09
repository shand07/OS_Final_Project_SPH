import java.io.*;
import java.net.*;

public class TicTacToeClient {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to Tic-Tac-Toe server!");

            boolean isWaitingDisplayed = false; // Prevents repeated "waiting" messages
            String serverMessage;

            while ((serverMessage = in.readLine()) != null) {
                // Suppress redundant "Waiting for Player X's turn" messages
                if (serverMessage.contains("Waiting for Player") && isWaitingDisplayed) {
                    continue;
                }

                System.out.println(serverMessage);

                // Reset the waiting message flag when the server changes state
                if (!serverMessage.contains("Waiting for Player")) {
                    isWaitingDisplayed = false;
                }

                // Handle restart prompt after the game ends
                if (serverMessage.contains("do you want to restart")) {
                    System.out.print("Enter your choice (1 to restart, any other key to exit): ");
                    String restartResponse = userInput.readLine();
                    out.println(restartResponse);
                    isWaitingDisplayed = false; // Reset waiting status after game reset
                }

                // Handle player's turn
                if (serverMessage.startsWith("Your turn.")) {
                    isWaitingDisplayed = false; // Reset waiting status
                    System.out.print("Enter a position (0-8): ");
                    String position = userInput.readLine();
                    out.println(position);
                }

                // Mark "waiting" message as displayed
                if (serverMessage.contains("Waiting for Player")) {
                    isWaitingDisplayed = true; // Set flag to suppress future messages
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
