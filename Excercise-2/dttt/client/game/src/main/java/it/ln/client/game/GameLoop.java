package it.ln.client.game;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.NoSuchElementException;

import it.ln.cli.CliRenderer;
import it.ln.cli.Renderer;
import it.ln.commons.Config;
import it.ln.server.serverapi.TTTService;

public class GameLoop {

    public static void main(String[] args) throws InterruptedException {
        final Renderer r = new CliRenderer();
        final TTTService server;

        try {
            final Registry registry = LocateRegistry.getRegistry(Config.HOST, Config.PORT);
            server = (TTTService) registry.lookup(Config.SERVICE_NAME);
            r.connected();
        } catch (Exception e) {
            r.connectionFailed(e.getMessage());
            return;
        }

        while (true) {
            switch (r.command()) {
                case EXIT -> {
                    r.exiting();
                    return;
                }
                case PLAY -> {
                    r.starting();
                    try {
                        new ClientImpl(server, r).play();
                    } catch (RemoteException e) {
                        r.connectionFailed(e.getMessage());
                    } catch (NoSuchElementException e) {
                        r.exiting();
                        return;
                    }
                }
                case UNKNOWN -> r.invalid();
            }
        }
    }
}
