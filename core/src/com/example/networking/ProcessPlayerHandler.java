package com.example.networking;

import com.example.manager.command.Command;
import com.example.manager.command.CommandHandler;
import com.example.manager.player.Bot;
import com.example.manager.player.HumanPlayer;
import com.example.manager.player.Player;
import com.example.manager.player.PlayerHandler;
import com.example.networking.data.GameInformation;
import com.example.networking.data.TurnInformation;
import com.example.networking.rmi.ProcessCommunicator;
import com.example.networking.rmi.ProcessCommunicatorImpl;
import com.example.simulation.GameState;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public final class ProcessPlayerHandler implements PlayerHandler {

    public static final int registryPort = 1099;
    public static final String stubNamePrefix = "ProcessCommunicator_";

    private final Class<? extends Player> playerClass;
    private final String remoteReferenceName;

    private Registry registry;
    private ProcessCommunicator communicator;
    private Process process;


    public ProcessPlayerHandler(Class<? extends Player> playerClass, int gameId, int playerId) {
        this.playerClass = playerClass;
        remoteReferenceName = stubNamePrefix + gameId + "_" + playerId;
    }

    @Override
    public boolean isHumanPlayer() {
        return HumanPlayer.class.isAssignableFrom(playerClass);
    }

    @Override
    public boolean isBotPlayer() {
        return Bot.class.isAssignableFrom(playerClass);
    }

    @Override
    public Future<?> init(GameState gameState, boolean isDebug, long seed, CommandHandler commandHandler) {
        try {
            // createRegistry() wirft eine RemoteException, wenn an dem Port bereits ein Registry-Objekt existiert
            registry = LocateRegistry.createRegistry(registryPort);
        } catch (RemoteException e) {
            // In diesem Fall wird die bereits existierende Registry verwendet
            try {
                registry = LocateRegistry.getRegistry(registryPort);
            } catch (RemoteException ex) {
                throw new RuntimeException(ex);
            }
        }

        // Lokale Instanz des ProcessCommunicator-Objekts
        ProcessCommunicator localCommunicator = new ProcessCommunicatorImpl();
        try {
            // Exportieren des Objekts, damit es von anderen Prozessen verwendet werden kann
            communicator = (ProcessCommunicator) UnicastRemoteObject.exportObject(localCommunicator, 0);
            registry.rebind(remoteReferenceName, communicator);
            communicator.queueInformation(new GameInformation(gameState, isDebug, seed));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder.inheritIO();

        File currentJar;
        try {
            currentJar = new File(ProcessPlayerHandler.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        builder.command(
                "java", "-cp", currentJar.getPath(), BotProcessLauncher.class.getName(), // Starten der JVM in der main() vom BotProcessLauncher
                "-p", playerClass.getName(), // Angabe des Klasse des Spielers
                "-port", String.valueOf(registryPort), // Angabe des Ports der Remote Object Registry
                "-reference", remoteReferenceName // Angabe des Namens, unter dem die Remote Reference im Remote Object Registry gebunden ist
        );

        try {
            process = builder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new FutureTask<>(
                () -> {
                    Command command;
                    do {
                        try {
                            command = communicator.dequeueCommand();
                        } catch (RemoteException e) {
                            throw new RuntimeException(e);
                        }
                        commandHandler.handleCommand(command);
                    } while (!command.endsTurn());
                },
                null
        );
    }

    @Override
    public Future<?> executeTurn(GameState gameState, CommandHandler commandHandler) {
        try {
            communicator.queueInformation(new TurnInformation(gameState));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        return new FutureTask<>(
                () -> {
                    Command command;
                    do {
                        try {
                            command = communicator.dequeueCommand();
                        } catch (RemoteException e) {
                            throw new RuntimeException(e);
                        }
                        commandHandler.handleCommand(command);
                    } while (!command.endsTurn());
                },
                null
        );
    }

    @Override
    public void dispose() {
        try {
            registry.unbind(remoteReferenceName);
        } catch (RemoteException | NotBoundException e) {
            throw new RuntimeException(e);
        }
    }
}
