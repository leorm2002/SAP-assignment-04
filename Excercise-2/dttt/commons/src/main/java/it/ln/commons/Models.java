package it.ln.commons;

import java.io.Serializable;
import java.util.Arrays;

public class Models {

    public enum PlayerState {
        WON,
        LOST,
        EVEN,
        FAILED
    }

    public enum Cell {
        EMPTY,
        CROSS,
        CIRCLE
    }

    public sealed interface ClientMessage extends Serializable
            permits ClientMessage.YourTurn, ClientMessage.Won, ClientMessage.Lost, ClientMessage.Even,
            ClientMessage.Failed {
        record YourTurn(Board board) implements ClientMessage {
        }

        record Won() implements ClientMessage {
        }

        record Lost() implements ClientMessage {
        }

        record Even() implements ClientMessage {
        }

        record Failed() implements ClientMessage {
        }
    }

    public record Player(String id) implements Serializable {
    }

    public record Board(Cell[] board) implements Serializable {

        public static Board init(int size) {
            final Cell[] board = new Cell[size * size];
            Arrays.fill(board, Cell.EMPTY);

            return new Board(board);
        }

        public int size() {
            return (int) Math.sqrt(board.length);
        }

        public Board with(int x, int y, Cell c) {

            final Cell[] newBoard = Arrays.copyOf(this.board, this.board.length);
            newBoard[x * size() + y] = c;
            return new Board(newBoard);
        }

        public boolean isLegal(int x, int y) {
            if (x < 0 || y < 0 || x >= size() || y >= size()) {
                return false;
            }

            return switch (this.board[x * size() + y]) {
                case EMPTY -> true;
                case CROSS, CIRCLE -> false;
            };
        }
    }

}
