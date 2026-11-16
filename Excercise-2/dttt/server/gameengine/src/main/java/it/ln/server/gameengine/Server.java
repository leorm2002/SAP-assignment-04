package it.ln.server.gameengine;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import it.ln.commons.Config;
import it.ln.server.serverapi.TTTService;

public class Server {

    public static void main(String[] args) {
        try {
            final Registry registry = LocateRegistry.createRegistry(Config.PORT);
            System.out.println("RMI Registry started on 1099.");
            final TTTService obj = new TTTServiceImpl();
            registry.rebind(Config.SERVICE_NAME, obj);
            System.out.println("Server ready");
        } catch (Exception e) {
            System.err.println("Errore del server: " + e.toString());
            e.printStackTrace();
        }
    }
}