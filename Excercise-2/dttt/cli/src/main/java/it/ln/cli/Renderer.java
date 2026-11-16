package it.ln.cli;

import it.ln.commons.Models.Board;

public interface Renderer {

    enum Command {
        PLAY,
        EXIT,
        UNKNOWN
    }

    record Move(int x, int y) {
    }

    Command command();

    Move move(Board board);

    void connected();

    void connectionFailed(String message);

    void starting();

    void exiting();

    void invalid();

    void joining();

    void ended();

    void render(Board board);

    void won();

    void lost();

    void even();

    void failed();
}
