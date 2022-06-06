/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.projeto.chat;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

/**
 *
 * @author bruno
 */
// Runnable is implemented on a class whose instances will be executed by a thread.
public class ClientHandler implements Runnable {

    // Array list of all the threads handling clients so each message can be sent to the client the thread is handling.
    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    public static ArrayList<String> users = new ArrayList<>();
    // Id that will increment with each new client.

    // Socket for a connection, buffer reader and writer for receiving and sending data respectively.
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String clientUsername;
    private String clientGroup = "";

    // Creating the client handler from the socket the server passes.
    public ClientHandler(Socket socket) {
        try {
            this.socket = socket;
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.bufferedWriter= new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            // When a client connects their username is sent.
            this.clientUsername = bufferedReader.readLine();
            // Add the new client handler to the array so they can receive messages from others.
            clientHandlers.add(this);
            users.add(clientUsername);
            broadcastMessage("SERVER: " + clientUsername + " entrou no chat!");
        } catch (IOException e) {
            // Close everything more gracefully.
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

    // Everything in this method is run on a separate thread. We want to listen for messages
    // on a separate thread because listening (bufferedReader.readLine()) is a blocking operation.
    // A blocking operation means the caller waits for the callee to finish its operation.
    @Override
    public void run() {
        String messageFromClient;
        // Continue to listen for messages while a connection with the client is still established.
        while (socket.isConnected()) {
            try {
                // Read what the client sent and then send it to every other client.
                messageFromClient = bufferedReader.readLine();
                String comando = messageFromClient.split(" ")[1];
                
                if(comando.trim().equalsIgnoreCase("/ajuda")){
                    messageFromClient = getHelpCommands();
                    broadcastPersonalMessage(messageFromClient);
                }
                else if(comando.trim().equalsIgnoreCase("/listarUsuarios")){
                    messageFromClient = getUsers();
                    broadcastPersonalMessage(messageFromClient);
                }
                else if(comando.trim().equalsIgnoreCase("/mensagemPrivada")){
                    String usuario = messageFromClient.split(" ")[2];
                    messageFromClient = messageFromClient.replace(comando, "");
                    messageFromClient = messageFromClient.replace(usuario, "");
                    broadcastPrivateMessage(messageFromClient.trim(), usuario);
                }
                
                else if(comando.trim().equalsIgnoreCase("/mensagemGrupo")){
                    if(!this.clientGroup.equals("")){
                        messageFromClient = messageFromClient.replace(comando, "");
                        messageFromClient = messageFromClient.replace(this.clientGroup, "");
                        broadcastGroupMessage(messageFromClient.trim(), this.clientGroup);
                    }
                    else
                        broadcastPersonalMessage("SERVER: Nao foi possivel enviar a mensagem pois voce ainda nao esta em nenhum grupo");
                }
                
                else if(comando.trim().equalsIgnoreCase("/entrarGrupo")){
                    String grupo = messageFromClient.split(" ")[2];
                    if(this.clientGroup.equals("")){
                        this.clientGroup = grupo;
                        messageFromClient = "SERVER: Voce entrou no grupo " + grupo;
                        broadcastPersonalMessage(messageFromClient);
                    }
                    else
                        broadcastPersonalMessage("SERVER: Nao foi possivel entrar no grupo " + grupo + ", pois voce ja esta no grupo " + this.clientGroup);
                }
                
                else if(comando.trim().equalsIgnoreCase("/sairGrupo")){
                    if(!this.clientGroup.equals("")){
                        String grupo = this.clientGroup;
                        this.clientGroup = "";
                        messageFromClient = "SERVER: Voce saiu do grupo " + grupo;
                        broadcastPersonalMessage(messageFromClient);
                    }
                    else
                        broadcastPersonalMessage("SERVER: Nao foi possivel sair do grupo pois voce ainda nao esta em nenhum grupo");
                    
                }
                else
                    broadcastMessage(messageFromClient);
                
            } catch (IOException e) {
                // Close everything gracefully.
                closeEverything(socket, bufferedReader, bufferedWriter);
                break;
            }
        }
    }

    // Send a message through each client handler thread so that everyone gets the message.
    // Basically each client handler is a connection to a client. So for any message that
    // is received, loop through each connection and send it down it.
    public void broadcastMessage(String messageToSend) {
        for (ClientHandler clientHandler : clientHandlers) {
            try {
                // You don't want to broadcast the message to the user who sent it.
                if (!clientHandler.clientUsername.equals(clientUsername)) {
                    clientHandler.bufferedWriter.write(messageToSend);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                }
            } catch (IOException e) {
                // Gracefully close everything.
                closeEverything(socket, bufferedReader, bufferedWriter);
            }
        }
    }
    
    public void broadcastPersonalMessage(String messageToSend) {
        for (ClientHandler clientHandler : clientHandlers) {
            try {
                // You don't want to broadcast the message to the user who sent it.
                if (clientHandler.clientUsername.equals(clientUsername)) {
                    clientHandler.bufferedWriter.write(messageToSend);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                }
            } catch (IOException e) {
                // Gracefully close everything.
                closeEverything(socket, bufferedReader, bufferedWriter);
            }
        }
    }
    
    public void broadcastPrivateMessage(String messageToSend, String usuario) {
        for (ClientHandler clientHandler : clientHandlers) {
            try {
                // You don't want to broadcast the message to the user who sent it.
                if (clientHandler.clientUsername.equals(usuario)) {
                    clientHandler.bufferedWriter.write(messageToSend);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                }
            } catch (IOException e) {
                // Gracefully close everything.
                closeEverything(socket, bufferedReader, bufferedWriter);
            }
        }
    }
    
    public void broadcastGroupMessage(String messageToSend, String group) {
        for (ClientHandler clientHandler : clientHandlers) {
            try {
                // You don't want to broadcast the message to the user who sent it.
                if (clientHandler.clientGroup.equals(group) && !clientHandler.clientUsername.equals(clientUsername)) {
                    clientHandler.bufferedWriter.write(messageToSend);
                    clientHandler.bufferedWriter.newLine();
                    clientHandler.bufferedWriter.flush();
                }
            } catch (IOException e) {
                // Gracefully close everything.
                closeEverything(socket, bufferedReader, bufferedWriter);
            }
        }
    }

    // If the client disconnects for any reason remove them from the list so a message isn't sent down a broken connection.
    public void removeClientHandler() {
        clientHandlers.remove(this);
        broadcastMessage("SERVER: " + clientUsername + " desconectou do chat!");
    }

    // Helper method to close everything so you don't have to repeat yourself.
    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        // Note you only need to close the outer wrapper as the underlying streams are closed when you close the wrapper.
        // Note you want to close the outermost wrapper so that everything gets flushed.
        // Note that closing a socket will also close the socket's InputStream and OutputStream.
        // Closing the input stream closes the socket. You need to use shutdownInput() on socket to just close the input stream.
        // Closing the socket will also close the socket's input stream and output stream.
        // Close the socket after closing the streams.

        // The client disconnected or an error occurred so remove them from the list so no message is broadcasted.
        removeClientHandler();
        try {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public String getUsers(){
        var result = String.join(", ", users);
        return "SERVER: Usuarios do chat: " + result;
    }
    
    public String getHelpCommands(){
        var comandos = "SERVER: Comandos disponiveis: \n"
                + "/listarUsuarios = lista todos os usuarios do chat. \n"
                + "/mensagemPrivada + {usuario} = envia mensagem privada para determinado usuario. \n"
                + "/entrarGrupo + {nomeGrupo} = entra em determinado grupo. \n"
                + "/mensagemGrupo = envia mensagem para membros do grupo que voce faz parte. \n"
                + "/sairGrupo = sai do seu grupo atual.";
        return comandos;
    }
}