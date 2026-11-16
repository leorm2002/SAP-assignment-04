package it.ln.cli;

import java.util.Scanner;

import it.ln.commons.Models.Board;

public class CliRenderer implements Renderer {

    private static final String SEPARATOR = "=".repeat(100);
    private final Scanner scanner = new Scanner(System.in);

    @Override
    public Command command() {
        System.out.println("\n===== GAME MENU =====");
        System.out.println("Press 'P' then Enter to PLAY");
        System.out.println("Press 'E' or 'ESC' then Enter to EXIT");
        System.out.print("Your choice: ");

        return switch (scanner.nextLine().trim().toLowerCase()) {
            case "p" -> Command.PLAY;
            case "e", "esc" -> Command.EXIT;
            default -> Command.UNKNOWN;
        };
    }

    @Override
    public Move move(Board board) {
        render(board);
        System.out.println("E' il tuo turno! Inserisci coordinate (x e y)...");
        System.out.print("Riga (x): ");
        int x = scanner.nextInt();
        System.out.print("Colonna (y): ");
        int y = scanner.nextInt();
        scanner.nextLine();
        return new Move(x, y);
    }

    @Override
    public void connected() {
        System.out.println("Connesso al server RMI con successo.");
    }

    @Override
    public void connectionFailed(String message) {
        System.err.println("Impossibile connettersi al server: " + message);
    }

    @Override
    public void starting() {
        System.out.println("Starting game...");
    }

    @Override
    public void exiting() {
        System.out.println("Exiting game. Goodbye!");
    }

    @Override
    public void invalid() {
        System.out.println("Invalid input. Please try again.");
    }

    @Override
    public void joining() {
        System.out.println("Inviando richiesta di partecipazione al server...");
    }

    @Override
    public void ended() {
        System.out.println("Partita terminata. Ritorno al menu principale.");
    }

    @Override
    public void render(Board board) {

        int width = board.size();

        clearConsole();
        System.out.println(SEPARATOR);

        System.out.println("┌" + "─".repeat(width * 2 + 1) + "┐");
        for (int i = 0; i < width; i++) {
            System.out.print("| ");

            for (int j = 0; j < width; j++) {

                final String character = switch (board.board()[i * width + j]) {
                    case EMPTY -> ".";
                    case CIRCLE -> "O";
                    case CROSS -> "X";
                };
                System.out.print(character);
                System.out.print(" ");
            }
            System.out.print("|");
            System.out.println("");
        }
        System.out.println("└" + "─".repeat(width * 2 + 1) + "┘");
    }

    public static void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    @Override
    public void won() {
        System.err.println(SEPARATOR);
        System.out.println("You won");
        System.err.println(SEPARATOR);
    }

    @Override
    public void lost() {
        System.err.println(SEPARATOR);
        System.out.println("You lost");
        System.err.println(SEPARATOR);
    }

    @Override
    public void even() {
        System.err.println(SEPARATOR);
        System.out.println("Game is even");
        System.err.println(SEPARATOR);
    }

    @Override
    public void failed() {
        System.err.println(SEPARATOR);
        System.out.println("Game is failed");
        System.err.println(SEPARATOR);
    }
}
