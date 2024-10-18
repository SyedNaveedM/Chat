import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static Set<ClientHandler> clientHandlers = new HashSet<>();
    private static final int PORT = 12345;
    private static final String USER_DATA_FILE = "users.txt";  // File to store user credentials

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT + ", waiting for clients...");

            while (true) {
                Socket clientSocket = serverSocket.accept();  // Accept client connections
                System.out.println("New client connected: " + clientSocket);
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();  // Start a new thread for each client
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Broadcast message to all clients except the sender
    public static void broadcast(String message, ClientHandler excludeUser) {
        for (ClientHandler client : clientHandlers) {
            if (client != excludeUser) {
                client.sendMessage(message);
            }
        }
    }

    // Add a client after successful login
    public static void addClient(ClientHandler clientHandler) {
        clientHandlers.add(clientHandler);
        broadcast(clientHandler.getUsername() + " has joined the chat!", null);
    }

    // Remove a client on disconnect
    public static void removeClient(ClientHandler clientHandler) {
        clientHandlers.remove(clientHandler);
        System.out.println(clientHandler.getUsername() + " disconnected.");
        broadcast(clientHandler.getUsername() + " has left the chat!", null);
    }

    // Verify if username exists and the password matches
    public static boolean authenticate(String username, String password) {
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_DATA_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] userDetails = line.split(":");
                if (userDetails[0].equals(username) && userDetails[1].equals(password)) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Register a new user by writing to the file
    public static boolean registerUser(String username, String password) {
        // Check if the username already exists
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_DATA_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] userDetails = line.split(":");
                if (userDetails[0].equals(username)) {
                    return false;  // Username already exists
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Add the new user to the file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_DATA_FILE, true))) {
            writer.write(username + ":" + password);
            writer.newLine();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Setup input/output streams
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Handle authentication
            boolean isAuthenticated = false;
            while (!isAuthenticated) {
                out.println("Do you want to (1) Login or (2) Sign up?");
                String choice = in.readLine();
                if (choice.equals("1")) {
                    isAuthenticated = handleLogin();
                } else if (choice.equals("2")) {
                    isAuthenticated = handleSignUp();
                }
            }

            // Once authenticated, join the chat
            ChatServer.addClient(this);

            // Handle messages from the client
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println(username + ": " + message);
                ChatServer.broadcast(username + ": " + message, this);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            ChatServer.removeClient(this);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Handle the login process
    private boolean handleLogin() throws IOException {
        out.println("Enter your username: ");
        String inputUsername = in.readLine();
        out.println("Enter your password: ");
        String inputPassword = in.readLine();

        if (ChatServer.authenticate(inputUsername, inputPassword)) {
            this.username = inputUsername;
            out.println("Login successful! Welcome to the chat.");
            return true;
        } else {
            out.println("Invalid credentials. Please try again.");
            return false;
        }
    }

    // Handle the signup process
    private boolean handleSignUp() throws IOException {
        out.println("Choose a username: ");
        String inputUsername = in.readLine();
        out.println("Choose a password: ");
        String inputPassword = in.readLine();

        if (ChatServer.registerUser(inputUsername, inputPassword)) {
            this.username = inputUsername;
            out.println("Signup successful! You are now logged in.");
            return true;
        } else {
            out.println("Username already exists. Please try another username.");
            return false;
        }
    }

    // Send message to the client
    public void sendMessage(String message) {
        out.println(message);
    }

    // Get the client's username
    public String getUsername() {
        return username;
    }
}
