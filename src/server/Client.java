package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;


public class Client implements Runnable{

    private Socket socket;
    private int ID;
    private String username;
    private PrintWriter writer;
    private BufferedReader in;
    public ArrayList<Stock> ownedStock;


    //Client object
    public Client(Socket socket, String name) {
        try {
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }catch (IOException e){
            System.out.println("Error creating I/O");
        }
        this.username = setUsername();
        this.ownedStock = new ArrayList<Stock>();
        this.socket = socket;
        this.ID = generateID();
    }

    public String setUsername(){
        String setName = "noName";
        try {
            setName = in.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return setName;
    }

    public String getUsername(){
        return this.username;
    }

    //Generates user ID. First tries to make the user ID equal to the size of the clients array. If the Id has been taken, it increments by 1.
    //Alternative to have an index variable which stores the last assigned ID, but this may become unstable at higher numbers (in a theoretical setting with more connections)
    public int generateID(){
        int firstID = Main.clients.size();
        boolean idValid = false;
        while (!idValid){
            for (int i = 0; i < Main.clients.size(); i++){
                if (Main.clients.get(i).getID() == firstID){
                    System.out.println("id not valid");
                    firstID += 1;
                }
            }
            idValid = true;
        }
        return firstID;
    }

    public int getID(){
        return this.ID;
    }

    private void buyStock(){
        //TODO Buy stock
        // TODO might need to reorganise project so Stock market variable is accessable, as Main.market has not been initialised
        System.out.println("buy stock");
        //testing id = 1
        Stock stockToBuy = StockMarket.getStock(1);
        if (stockToBuy != null){
            if (stockToBuy.hasOwner()){
                Client owner = stockToBuy.getOwner();

                if(Main.market.trade(owner, this, stockToBuy)){
                    System.out.println("Trade successful");
                    Main.broadcast("Trade successful, owner of stock is: " + this.getUsername());
                }else{
                    sendMessage("Trade unsuccessful");
                    System.out.println("Trade unsuccessful");
                }
                //TODO trade
            }else{
                System.out.println("doesnt have owner"); //if owner has disconnected
                stockToBuy.setOwner(this);
                this.ownedStock.add(stockToBuy);
            }
        }else if (stockToBuy == null){
            System.out.println("Stock doesnt exist");
        }

    }

    public boolean isConnected(){
        if (socket.isClosed()){
            return false;
        }else{
            return true;
        }
    }

    public void sendMessage(String msg){
        writer.println(msg);
    }

    private void sellStock(){
        int recipientID = 0;
        
        try {
            recipientID = Integer.valueOf(in.readLine());
        } catch (IOException e) {
            sendMessage("Please provide a valid user ID");
            e.printStackTrace();
        }

        //Check user owns the stock
        if (ownedStock.size() != 0 ){
            Client recipient = Main.getClient(recipientID);

            if (recipient != null) {
                if (Main.market.trade(this, recipient, this.ownedStock.get(0)) == true) { //In a single stock market will always be index 0
                    sendMessage("Trade successful");
                }else{
                    sendMessage("Trade unsuccessful");
                }
            }
            else{
                sendMessage("Invalid user ID. Type 'connections' for a list of users connected");

            }
        }else{
            writer.println("You do not own the stock! Type 'status' to see who owns the stock");
        }
    }

    private void balance(){
        //TODO show balance
        String stockMessage = "Stock: ";
        for (int i = 0; i < ownedStock.size(); i++){stockMessage+= ownedStock.get(i).name + " ";}
        sendMessage(stockMessage);
    }

    //Could be modified to use a stock ID in a multi-stock market by taking stock id through arguement
    public void status(){
        Client stockOwner = Main.market.getStock(1).getOwner();
        if (stockOwner != null){
            writer.println("Stock owned by: " + stockOwner.getUsername());
        }else{
            writer.println("Stock not owned.");
        }


    }

    public void connections(){
        System.out.println("here");
        String connections = "";
        for (int i = 0; i < Main.clients.size(); i++){
            connections += (Main.clients.get(i).username + " ID: " + Main.clients.get(i).getID() + "\n");
        }
        sendMessage(connections);
    }

    public void quit(){
        System.out.println("User: " + this.username + " quitting");
        Main.broadcast("User: " + this.username + " disconnected");
        Main.clients.remove(this);
        try {
            System.out.println("reseeting stock in quit function");
            for (int i = 0; i < ownedStock.size(); i++){
                Main.resetStock(this);
            }


            socket.close();
        } catch (IOException e) {
            System.out.println("Error closing socket");
        }
    }

    @Override
    public void run() {
        boolean running = true;
        try {
            //Scanner scanner = new Scanner(socket.getInputStream());

            try {
                System.out.println("Client connected, ID: " + this.ID);

                while (running) {

                    String inputLine;
                    try {

                        while ((inputLine = in.readLine()) != null) {
                            System.out.println(inputLine);
                            switch (inputLine){
                                case "sell":
                                    System.out.println("sell found for user: " + this.getUsername());
                                    sellStock();
                                    break;
                                case "buy":
                                    buyStock();
                                    break;
                                case "balance":
                                    balance();
                                    break;
                                case "status":
                                    status();
                                    break;
                                case "connections":
                                    connections();
                                    break;
                                case "quit":
                                    quit();
                                    break;
                            }
                        }
                        running = false;

                    } catch (IOException e) {
                        quit();
                        running = false;
                    }

                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                socket.close();
            }

        } catch (IOException e) {
            System.out.println("Error");

            Main.resetStock(this);

            this.ownedStock.clear();
            System.exit(1);
        }
    }


    /*
    public void resetStock(){
        if (ownedStock.size() > 0){
            for(int i = 0; i < ownedStock.size(); i++){
                if (Main.clients.size() > 0){
                    System.out.println("setting owner");
                    Client newOwner = Main.clients.get(0);
                    Stock stock = ownedStock.get(i);
                    if(stock.setOwner(newOwner)){//As user disconnects, if they are the owner of the stock it will set to the first client in the connection array
                        System.out.println("New owner success");
                        System.out.println(stock.getOwner().username);
                    }else{
                        System.out.println("new owner failure.");
                    }

                    System.out.println("here");
                }else{
                    ownedStock.get(i).setOwner(null); //if there are no users connected, resets the stock ownership to null
                }

            }
        }
    }*/


}
