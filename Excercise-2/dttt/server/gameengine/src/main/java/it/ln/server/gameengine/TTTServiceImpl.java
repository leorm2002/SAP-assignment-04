package it.ln.server.gameengine;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.ln.client.clientapi.Client;
import it.ln.commons.Models.Player;
import it.ln.server.gameengine.Game.EnrichedPlayer;
import it.ln.server.gameengine.Game.Play;
import it.ln.server.serverapi.TTTService;

public class TTTServiceImpl extends UnicastRemoteObject implements TTTService {
    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    protected TTTServiceImpl() throws RemoteException {
        super();
    }

    final ConcurrentLinkedQueue<EnrichedPlayer> waitlist = new ConcurrentLinkedQueue<>();
    final ConcurrentMap<Player, Game> games = new ConcurrentHashMap<>();

    @Override
    public Player join(Client c) {
        final Player p = new Player(UUID.randomUUID().toString());
        final EnrichedPlayer ep = new EnrichedPlayer(p, c);
        final EnrichedPlayer queuedPlayer = waitlist.poll();
        if (queuedPlayer == null) {
            System.err.println("Player just joined, waiting for another");
            waitlist.offer(ep);
        } else {
            System.err.println("Player just joined, creating a match");
            final Game game = new Game(queuedPlayer, ep);
            games.put(game.p1.p(), game);
            games.put(game.p2.p(), game);
            executor.submit(new GameHandler(this, game));
        }

        return p;
    }

    @Override
    public void play(Player p, int x, int y) {
        System.err.println("Recieved " + x + " " + y);
        if (p == null) {
            return;
        }
        final Game game = games.get(p);
        if (game == null) {
            return;
        }

        game.plays.offer(new Play(p, x, y));
    }

    void removeGame(Game game) {
        games.remove(game.p1.p());
        games.remove(game.p2.p());
    }

}
