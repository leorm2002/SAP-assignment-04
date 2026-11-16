package it.ln.client.game;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import it.ln.cli.Renderer;
import it.ln.commons.Models.ClientMessage;
import it.ln.commons.Models.Player;
import it.ln.server.serverapi.TTTService;

class ClientImpl implements it.ln.client.clientapi.Client {
    private final TTTService server;
    private final Renderer r;
    private final BlockingQueue<ClientMessage> messages = new LinkedBlockingQueue<>();
    private Player myIdentity;

    public ClientImpl(TTTService server, Renderer r) throws RemoteException {
        this.server = server;
        this.r = r;
        UnicastRemoteObject.exportObject(this, 0);
    }

    @Override
    public void receive(ClientMessage message) {
        messages.offer(message);
    }

    private boolean handle(ClientMessage message) throws RemoteException {
        return switch (message) {
            case ClientMessage.YourTurn turn -> {
                final Renderer.Move move = r.move(turn.board());
                server.play(myIdentity, move.x(), move.y());
                yield true;
            }
            case ClientMessage.Won _ -> {
                r.won();
                yield false;
            }
            case ClientMessage.Lost _ -> {
                r.lost();
                yield false;
            }
            case ClientMessage.Even _ -> {
                r.even();
                yield false;
            }
            case ClientMessage.Failed _ -> {
                r.failed();
                yield false;
            }
        };
    }

    public void play() throws InterruptedException, RemoteException {
        try {
            r.joining();
            this.myIdentity = server.join(this);

            while (handle(messages.take())) {
            }

            r.ended();
        } finally {
            UnicastRemoteObject.unexportObject(this, true);
        }
    }
}
