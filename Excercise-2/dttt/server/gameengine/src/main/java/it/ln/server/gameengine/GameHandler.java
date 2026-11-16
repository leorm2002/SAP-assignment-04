package it.ln.server.gameengine;

import java.rmi.RemoteException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import it.ln.commons.Config;
import it.ln.commons.Models.ClientMessage;
import it.ln.server.gameengine.Game.EnrichedPlayer;
import it.ln.server.gameengine.Game.Play;

record GameHandler(TTTServiceImpl service, Game game) implements Runnable {
    @Override
    public void run() {
        try {
            if (!notifyPlay()) {
                fail();
                return;
            }

            while (service.games.containsKey(game.p1.p())) {
                final Play play = nextPlay();
                if (!game.isCurrent(play.p()) || !game.game.isLegal(play.x(), play.y())) {
                    notifyPlay();
                    continue;
                }

                game.with(play.x(), play.y(), game.getSymbol(play.p()));

                if (game.checkWin(play.p())) {
                    send(game.current, new ClientMessage.Won());
                    send(game.isCurrent(game.p2.p()) ? game.p1 : game.p2, new ClientMessage.Lost());
                    service.removeGame(game);
                    return;
                }

                if (game.noMorePlays()) {
                    send(game.p1, new ClientMessage.Even());
                    send(game.p2, new ClientMessage.Even());
                    service.removeGame(game);
                    return;
                }

                game.switchCurrent();
                if (!notifyPlay()) {
                    fail();
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail();
        } catch (ExecutionException | TimeoutException e) {
            fail();
        }
    }

    private Play nextPlay() throws InterruptedException, ExecutionException, TimeoutException {
        final Future<Play> play = service.executor.submit(() -> game.plays.take());
        try {
            return play.get(Config.MOVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            play.cancel(true);
            throw e;
        }
    }

    private void fail() {
        send(game.p1, new ClientMessage.Failed());
        send(game.p2, new ClientMessage.Failed());
        service.removeGame(game);
    }

    private boolean notifyPlay() {
        try {
            game.current.c().receive(new ClientMessage.YourTurn(game.game));
            return true;
        } catch (RemoteException e) {
            System.out.println("Il giocatore si e' disconnesso bruscamente!");
            return false;
        }
    }

    private static void send(EnrichedPlayer p, ClientMessage message) {
        try {
            p.c().receive(message);
        } catch (RemoteException e) {
            System.out.println("Il giocatore si e' disconnesso bruscamente!");
        }
    }
}
