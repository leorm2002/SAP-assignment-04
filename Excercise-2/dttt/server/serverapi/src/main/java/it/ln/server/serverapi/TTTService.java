package it.ln.server.serverapi;

import java.rmi.Remote;
import java.rmi.RemoteException;

import it.ln.client.clientapi.Client;
import it.ln.commons.Models.Player;

public interface TTTService extends Remote {

    /**
     * Join a game, the client will be notified when to play.
     * 
     * @return the identity required to play
     */
    public Player join(Client c) throws RemoteException;

    /***
     * Make a play
     */
    public void play(Player p, int x, int y) throws RemoteException;

}
