package it.ln.server.gameengine;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

import it.ln.client.clientapi.Client;
import it.ln.commons.Models.Board;
import it.ln.commons.Models.Cell;
import it.ln.commons.Models.Player;

class Game {
    record Play(Player p, int x, int y) {
    }

    record EnrichedPlayer(Player p, Client c) {
    }

    final EnrichedPlayer p1;
    final EnrichedPlayer p2;
    final BlockingQueue<Play> plays = new LinkedBlockingQueue<>();
    EnrichedPlayer current;
    Board game;

    Game(EnrichedPlayer p1, EnrichedPlayer p2) {
        this.p1 = p1;
        this.p2 = p2;
        this.current = p1;
        this.game = Board.init(3);
    }

    void with(int x, int y, Cell c) {
        game = game.with(x, y, c);
    }

    Cell getSymbol(Player p) {
        if (areSame(p, p1.p)) {
            return Cell.CIRCLE;
        } else {
            return Cell.CROSS;
        }
    }

    public boolean checkWin(Player p) {
        final Cell symbol = getSymbol(p);
        final int size = game.size();
        return IntStream.range(0, size)
                .anyMatch(i -> IntStream.range(0, size).allMatch(j -> game.board()[i * size + j] == symbol)
                        || IntStream.range(0, size).allMatch(j -> game.board()[j * size + i] == symbol))
                || IntStream.range(0, size).allMatch(i -> game.board()[i * size + i] == symbol)
                || IntStream.range(0, size).allMatch(i -> game.board()[i * size + size - 1 - i] == symbol);
    }

    public boolean noMorePlays() {
        return !Arrays.stream(game.board()).anyMatch(Cell.EMPTY::equals);
    }

    public void switchCurrent() {
        if (areSame(current, p1)) {
            current = p2;
        } else {
            current = p1;
        }
    }

    public boolean isCurrent(Player p) {
        return areSame(current.p, p);
    }

    public static boolean areSame(EnrichedPlayer p1, EnrichedPlayer p2) {
        return areSame(p1.p, p2.p);
    }

    public static boolean areSame(Player p1, Player p2) {
        return p1.id().equals(p2.id());
    }

}
