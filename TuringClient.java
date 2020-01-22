
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TuringClient {
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe TuringClient rappresenta il client che un utente
	 * 			utilizza per interfacciarsi con il servizio Turing.
	 * 
	 */

	private final static int BLOCK_SIZE = 1024;
	private final static int REMOTE_PORT = 3000; //porta del servizio RMI
	private final static int SERVER_PORT = 2000; 
	private final static String HOST_ADDRESS = "127.0.0.1";
	
	public static void main(String[] args) {
		System.out.printf("Benvenuto!\nPer iniziare digita turing --help.\n");
		boolean finish = false;
		Listener listener = null;
		
		//canale per la lettura dei comandi
		ReadableByteChannel inChannel = Channels.newChannel(System.in);
		//per inviare i comandi
		ByteBuffer input = ByteBuffer.allocate(BLOCK_SIZE);
		//per ricevere i messaggi
		ByteBuffer output = ByteBuffer.allocate(BLOCK_SIZE);
		//per ricevere le diverse size
		ByteBuffer dimBuffer = ByteBuffer.allocate(4);
		
		String inputString;
		String username = null;
		
		try(SocketChannel client = SocketChannel.open(new InetSocketAddress(HOST_ADDRESS,SERVER_PORT))){
			//Fase di registrazione e login -Registration State-
			/*
			 * In questo stato è possibile solamente effettuare la registrazione degli utenti ed il login.
			 */
			while(!finish) {
				System.out.printf("\n>$ ");
				try {
					inChannel.read(input); //leggo il comando passato dall'utente
				} catch (IOException e) {
					System.out.println(e.toString());
				}
				inputString = new String(input.array(),0,input.position()).trim();
				input.clear();
				if(checkCommandLine(inputString)) { //controllo dell'input (ad es. presenza di --help...)
					String[] token = inputString.split(" ");
					switch(token[1]){
						case "register":
							//turing register nomeutente password
							if(token.length != 4)
								System.out.println("Il comando inserito non è valido.\nProva ad inserire turing --help per informazioni.");
							else{
								username = token[2];
								String password = token[3];
								//proceura di registrazione al servizio
								RegistrationService serverObject;
								Remote RemoteObject;
								try {
									//registrazione via RMI
									Registry r = LocateRegistry.getRegistry(REMOTE_PORT);
									RemoteObject = r.lookup("REGISTER-SERVICE");
									serverObject = (RegistrationService)RemoteObject;
									if(!serverObject.registerClient(username, password))
										System.out.println("L'utente risulta già registrato. Prova con un altro nome.");
									else System.out.println("Registrazione eseguita con successo.");
								}
								catch(Exception e) {
									System.out.println("Errore nell'invocazione del metodo: "+ e.toString());
								}
							}
							break;
						case "login":
							//turing login nome_utente password
							if(token.length != 4) System.out.println("Il comando inserito non è valido.\nProva ad inserire turing --help per informazioni.");
							else {
								//costruisco il comando che sarà inviato al server
								String login = token[1] + " " + token[2] + " " + token[3];
								//login username password
								output.putInt(login.length());//inserisco lunghezza
								output.put(login.getBytes());//inserisco il comando
								output.flip();
								while(output.hasRemaining())
									client.write(output);
								output.clear();
								//invato il comando al server
								System.out.println("Verifica delle credenziali in corso...");
								//leggo la risposta
								int dim = 0;
								while((dim+=client.read(input))<4 && dim != -1);
								
								if(dim == -1) {
									System.out.println("Ops, qualcosa è andato storto. Riprova più tardi.");
								}
								else {
									input.flip();
									int esito = input.getInt();
									if(esito != 1 && esito != 2) System.out.println("Login fallito.\nVerifica di nuovo le credenziali.");
									else {
										if(esito == 1) {
											finish = true;
											System.out.println("Login effettuato con successo.");
											username = token[2];
										}
										else {
											finish = true;
											System.out.println("Login effettuato con successo.\nHai ricevuto nuovi inviti mentri eri offline.\n"
													+ "Inserisci turing list per vedere i nuovi documenti.");
											username = token[2];
										}
										//login ok avvio il thread per la ricezione immediata delle notifiche
										listener = new Listener(SERVER_PORT,HOST_ADDRESS,username);
										listener.start();
									}
								}
								input.clear();
							}
							break;
						case "exit":
							System.out.println("A presto!");
							System.exit(0);
							break;
						default:
							System.out.printf("Comando non riconosciuto. Inserisci turing --help per"
									+ " informazioni\noppure inserisci turing exit per uscire.\n");
						break;
						}
				}
			}
			
			//Fase di lavoro -Signed in State-
			ClientOperations operation = new ClientOperations(username ,inChannel,client,input,output,dimBuffer);
			operation.executeOp();
			inChannel.close();
			listener.interrupt();
			System.out.println("A presto!");
			
		}
		catch(IOException e) {
			System.out.println("Server offline, riprova più tardi.");
		}
	}
	
	private static void printUsage() {
		System.out.println("\nUsage: turing COMMAND [ARGS...]\n");
		System.out.printf("COMMAND:\nregister <usernamen> <password>  registra l'utente\n"
				+ "login <username> <password>  effettua il login\n"
				+ "logout  effettua il logout\n\n");
		System.out.printf("create <document> <sec_number> <creator>  crea un documento\n"
				+ "share <document> <username>  condivide il documento\n"
				+ "show <document> <section> <creator>  mostra una sezione del documento\n"
				+ "show <document> <creator>  mostra l'intero docuemento\n"
				+ "list  mostra la lista dei documenti\n\n");
		System.out.printf("edit <document> <section> <creator>  modifica una sezione del documento\n"
				+ "end-edit <document> <section> <creator> fine modifica della sezione del documento\n");
	}
	
	private static boolean checkCommandLine(String toCheck) {
		if(toCheck.contains("help")) {
			printUsage();
			return false;
		}
		String[] tokenInput = toCheck.split(" ");
		if(tokenInput.length < 2) {
			System.out.println("Comando non riconosciuto. Inserisci turing --help per" 
					+ " informazioni\noppure inserisci turing exit per uscire.");
			return false;
		}
		if(!tokenInput[0].equals("turing")) {
			System.out.println("Comando non riconosciuto. Inserisci turing --help per" 
					+ " informazioni\noppure inserisci turing exit per uscire.");
			return false;
		}
		return true;
	}
	
}
