package it.ln.client.clientapi;

import java.rmi.Remote;
import java.rmi.RemoteException;

import it.ln.commons.Models.ClientMessage;

/** Represents the callback exposed by the client to be notified */
public interface Client extends Remote {
    public void receive(ClientMessage message) throws RemoteException;
}
