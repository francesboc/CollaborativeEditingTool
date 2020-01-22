import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.util.Vector;

public class ClientOperations {
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe ClientOperaions rappresenta lo stato in cui
	 * 			il client può interfacciarsi al server tramite i
	 * 			comandi messi a disposizione dal servizio
	 * 
	 */
	private boolean finish ;
	private ReadableByteChannel in;
	String currentDir = System.getProperty("user.dir");
	
	private ReadableByteChannel waitEdit; //utilizzato nella fase di editing
	private String username; //identifica il nome del client corrente
	private final static int INPUT_SIZE = 256;
	private SocketChannel client;
	private Vector<String> documents;
	byte[] toSend;
	//buffer utilizzati per inviare/ricevere al/dal server
	ByteBuffer sendBuffer;
	ByteBuffer receiveBuffer;
	String documentName;
	int section;
	String command;
	String creator;
	//usato per gli esiti ricevuti dal server
	ByteBuffer serverReplyBuffer = ByteBuffer.allocate(4);
	
	public ClientOperations(String username,ReadableByteChannel in,SocketChannel client
			,ByteBuffer input,ByteBuffer output, ByteBuffer dim) {
		this.username = username;
		this.in = in;
		finish = false;
		waitEdit = Channels.newChannel(System.in);
		this.client = client;
		sendBuffer = input;
		receiveBuffer = output;
		serverReplyBuffer = dim;
	}
	
	public void executeOp() throws IOException {
		String downloadedDoc = currentDir + "/documenti_scaricati_"+username;
		String downloadedSec = currentDir + "/sezioni_scaricate_"+username;
		String modifiedSec = currentDir + "/sezioni_modificate_"+username;
		//Creazione delle cartelle necessarie per l'esecuzione delle operazioni
		if(!Files.exists(Paths.get(downloadedDoc))) Files.createDirectory(Paths.get(downloadedDoc));
		if(!Files.exists(Paths.get(downloadedSec))) Files.createDirectory(Paths.get(downloadedSec));
		if(!Files.exists(Paths.get(modifiedSec))) Files.createDirectory(Paths.get(modifiedSec));
		
		documents = new Vector<String>(); //tutti i documenti creati da questo client
		//Fase di lavoro - Signed-in State -
		/*
		 * In questo stato è possibile effettuare le principali operazioni previste dal server
		 */
		ByteBuffer input = ByteBuffer.allocate(INPUT_SIZE);
		String inputString;
		int helper = 0; //per mostrare il messaggio di hello_editing
		while(!finish) {
			input.clear();
			System.out.printf("\n>$ ");
			try {
				in.read(input);
			} catch (IOException e) {
				System.out.println(e.toString());
			}
			inputString = new String(input.array(),0,input.position()).trim();
			if(checkCommandLine(inputString)) {
				String[] token = inputString.split(" ");
				token[1] = token[1].toLowerCase();
				switch(token[1]){
				case "create":
					//turing create nome_documento num_sezioni
					if(token.length != 4) printShortUsage();
					else {
						documentName = token[2];
						int numSezioni;
						try{
							numSezioni = Integer.parseInt(token[3]);
							if(numSezioni < 1) {
								System.out.println("Non è possibile creare documenti senza sezioni.");
								break;
							}
						}
						catch(NumberFormatException e) {
							System.out.println("Errore nel parametro. <numsezioni> deve essere un intero.");
							break;
						}
						//costruzione del comando da inviare al server
						command = token[1] +
								" " + documentName + " " + token[3] + " " + username;
						//invio il comando con la sua dimensione
						sendBuffer.putInt(command.length());
						sendBuffer.put(command.getBytes());
						sendBuffer.flip();
						while(sendBuffer.hasRemaining())
							client.write(sendBuffer);
						sendBuffer.clear();
						
						//leggo la risposta dal server
						int dim =0;
						while((dim += client.read(serverReplyBuffer))<4);
						serverReplyBuffer.flip();
						if(serverReplyBuffer.getInt()==1) {
							System.out.printf("documento %s creato con successo\ncomposto da %d sezioni\n",documentName,numSezioni);
							//l'univocità dei documenti è garantita dal fatto che il nome è dato dal nome del documento
							//insieme al nome dell'utente che lo ha creato.
							documents.add(new String(documentName + "_" + username));
						}
						else System.out.println("Errore nella creazione del documento.");
						serverReplyBuffer.clear();
					}
					break;
				case "share":
					//turing share nomedoc utente
					if(token.length != 4) printShortUsage();
					else {
						documentName = token[2] + "_" + username;
						String user = token[3];
						command = token[1] + " " + documentName + " " + user + " " + username;
						//share nomedocumento utente username
						sendBuffer.putInt(command.length());
						sendBuffer.put(command.getBytes());
						sendBuffer.flip();
						while(sendBuffer.hasRemaining())
							client.write(sendBuffer);
						sendBuffer.clear();
						//invio la richiesta. Attendo l'esito.
						
						int dim =0;
						while((dim += client.read(serverReplyBuffer))<4);
						serverReplyBuffer.flip();
						int esito = serverReplyBuffer.getInt();
						if(esito==1)
							System.out.printf("documento %s condiviso\n"
									+ "con %s con successo.\n",token[2],user);
						else if(esito == -1) 
							System.out.println("L'utente invitato è già un collaboratore.");
						else if(esito == 0)
							System.out.println("L'utente invitato non è un utente registrato al servizio TURING.");
						else if(esito == -2)
							System.out.println("Non puoi condividere documenti, a meno che tu non ne sia il creatore.");
						serverReplyBuffer.clear();
					}
					break;
				case "list":
					if(token.length != 2) {
						printShortUsage();
						break;
					}
					command = token[1] + " " + username;
					sendBuffer.putInt(command.length());
					sendBuffer.put(command.getBytes());
					sendBuffer.flip();
					while(sendBuffer.hasRemaining())
						client.write(sendBuffer);
					sendBuffer.clear();
					//richiesta inviata, attendo la size e i dati richiesti
					int dim1 =0;
					while((dim1 += client.read(serverReplyBuffer))<4);
					serverReplyBuffer.flip();
					int size = serverReplyBuffer.getInt();
					serverReplyBuffer.clear();
					
					String list = "";
					int readbyte = 0;
					readbyte+=client.read(receiveBuffer);
					list += new String(receiveBuffer.array(),0,receiveBuffer.position());
					
					while(readbyte<size) {
						readbyte+=client.read(receiveBuffer);
						list += new String(receiveBuffer.array(),0,receiveBuffer.position());
						receiveBuffer.clear();
					}
					receiveBuffer.clear();
					
					//splitted[0] contiene i miei documenti
					//splitted[1] quelli che mi hanno condiviso
					
					String[] splitted = list.split("/");
					if(splitted.length == 0) {
						System.out.println("Non hai ancora accesso ad alcun documento.");
					}
					else{
						System.out.println("Ecco la lista dei documenti a cui hai accesso:");
						if(splitted[0].length() > 0) System.out.printf("Documento e proprietario: ");
						for(int j=0; j<splitted[0].length();j++) {
							if(splitted[0].charAt(j)== '-') {
								System.out.printf("\n");
								if(j != (splitted[0].length())-1) System.out.printf("Documento e proprietario: ");
							}
							else System.out.print(splitted[0].charAt(j));
						}
						if(splitted.length == 2) {
							System.out.printf("Documento e proprietario: ");
							for(int j=0; j<splitted[1].length();j++) {
								if(splitted[1].charAt(j)== '-') {
									System.out.printf("\n");
									if(j != (splitted[1].length())-1) System.out.printf("Documento e proprietario: ");
								}
								else System.out.print(splitted[1].charAt(j));
							}
						}
					}
					break;
				case "edit": //edit <nomedoc> <sec> <creatore>
					if(!(token.length == 5)) {
						System.out.println("Comando non riconosciuto. Digita turing --help per informazioni.");
						System.out.printf("Per accedere invece alla lista dei documenti\na cui hai accesso digita turing list\n");
					}
					else {
						documentName = token[2] + "_" + token[4]; //nomedoc_creatore
						//invio la richiesta di modifica al server
						command = token[1] + " " + documentName + " " + token[3] + " " +token[4] + " " + username;
						//comando inviato: edit <nomedocumento> <sezione> <creator> <utente>
						 
						sendBuffer.putInt(command.length());
						sendBuffer.put(command.getBytes());
						sendBuffer.flip();
						while(sendBuffer.hasRemaining())
							client.write(sendBuffer);
						sendBuffer.clear();
						
						//ho inviato la richiesta di editing, attendo la risposta
						
						int dim =0;
						while((dim += client.read(serverReplyBuffer))<4);
						serverReplyBuffer.flip();
						int esito = serverReplyBuffer.getInt();
						serverReplyBuffer.clear();
						if(esito == 1) {
							//creo la cartella in cui saranno presenti le sezioni modificate dal client relative a qeusto documento
							//Creata se e solo se entro in fase di editing.
							String sectionModPath = modifiedSec + "/" + documentName + "_" +username;
							if(!Files.exists(Paths.get(sectionModPath))) Files.createDirectory(Paths.get(sectionModPath));
							
							try{
								section = Integer.parseInt(token[3]);
							}
							catch(NumberFormatException e) {
								System.out.println("Errore, <section> deve essere un intero.");
								break;
							}
							//se arrivo fino a qua significa che posso modificare la sezione
							//creo il file che ospiterà il testo ricevuto dal server
							String tmppath = sectionModPath + "/section" + section;
							Files.deleteIfExists(Paths.get(tmppath));
							Files.createFile(Paths.get(tmppath));
							FileChannel tmpfile = FileChannel.open(Paths.get(tmppath)
									,StandardOpenOption.WRITE,StandardOpenOption.READ);
							System.out.println("Esito positivo ho creato il file che conterrà la sezione. Attendo dimensione file");
							dim = 0;
							//leggo la size del file
							while((dim += client.read(serverReplyBuffer))<4);
							serverReplyBuffer.flip();
							int filesize = serverReplyBuffer.getInt();
							System.out.println("[Turing client] la size del file è "+filesize);
							serverReplyBuffer.clear();
							//leggo il file
							long transfered = 0;
							while((int)transfered < filesize)
								transfered+= tmpfile.transferFrom(client, transfered,filesize);
							tmpfile.close();
							if(transfered == filesize) System.out.println("[Turing client] file ricevuto correttamente");
							else{
								System.out.println("[Turing client] errore nella ricezione del file richiesto");
								break;
							}
								
							//ora leggo l'indirizzo di multicast del documento per avviare la chat
							client.read(receiveBuffer);
							String mAddress = new String(receiveBuffer.array(),0,receiveBuffer.position());
							InetAddress multicastAddress = InetAddress.getByName(mAddress);
							receiveBuffer.clear();
							//inizializzo la chat
							ChatInterface chat= new ChatInterface(username,multicastAddress);
							chat.setVisible(true);
							boolean endEdit = false;
							if(helper == 0) {
								showEditHelper();
								helper++;
							}
							//entro nel ciclo di editing. Uscirà solo quando sarà inserito end-edit
							while(!endEdit) {
								System.out.printf("\n>$ ");
								try {
									input.clear();
									in.read(input);
									String typed = new String(input.array(),0,input.position()).trim();
									String[] tokenEdit = typed.split(" ");
									if(tokenEdit.length != 5) System.out.println("Comando non riconosciuto.");
									else {
										String doctmp;
										if(tokenEdit[1].equals("end-edit")) {
										//end-edit nomedoc sec creatore
											doctmp = tokenEdit[2] + "_" + tokenEdit[4];
											if(!doctmp.equals(documentName) || !(Integer.parseInt(tokenEdit[3])==section)) {
												System.out.println("Puoi eseguire il comando end-edit solo sul documento e la sezione\n"
														+ "attualmente in fase di modifica.");
											}
											else{
												endEdit = true;
												String endEditString = tokenEdit[1] + " " + doctmp + " " + tokenEdit[3] +" " + tokenEdit[4]+ " "+username;
												sendBuffer.putInt(endEditString.length());
												sendBuffer.put(endEditString.getBytes());
												sendBuffer.flip();
												while(sendBuffer.hasRemaining())
													client.write(sendBuffer);
												sendBuffer.clear();
												//invio end-edit nomedoc_user sect creatore username
											}
										}
										else System.out.println("Stai editando un documento.\nTermina le modifiche per eseguire altre operazioni.");
									}
								} catch (IOException e) {
									System.out.println(e.toString());
								}
							}
							//chiudo la chat
							chat.closeChat();
							input.clear();
							
							//finito il ciclo di editing, ora devo inviare il file modificato al server
							//invio la grandezza
							
							tmpfile = FileChannel.open(Paths.get(tmppath),StandardOpenOption.READ,StandardOpenOption.WRITE);
							System.out.println("Invio file di size " + tmpfile.size());
							sendBuffer.putInt((int)tmpfile.size());
							sendBuffer.flip();
							client.write(sendBuffer);
							sendBuffer.clear();
							transfered = 0;
							filesize = (int) tmpfile.size();
							while((int)transfered < filesize)
								transfered+= tmpfile.transferTo(transfered,filesize,client);
							
							tmpfile.close();
							System.out.println("File inviato correttamente. Dimensione trasferita: "+transfered);
							
						}
						else{
							if(esito == -1) System.out.println("La sezione richiesta non è disponibile");
							else System.out.printf("Non hai i permessi per modificare questo documento.\nInserisci turing list per vedere"
									+ "i documenti a cui hai accesso\n");
						}
					}
					break;
				case "show":
					//turing show docname sec creatore
					if(token.length < 4 || token.length > 5) printShortUsage();
					else if(token.length == 5){
						creator = token[4];
						documentName = token[2] + "_" +creator;
						//mi ha richiesto la visualizzazione di una sezione
							
						String showDirectory = downloadedSec + "/" + documentName;
						try {
							section = Integer.parseInt(token[3]);
						}
						catch(NumberFormatException e) {
							System.out.println("Errore, <section> deve essere un intero");
							break;
						}
						command = token[1] + " " + documentName + " " + token[3] + " " + creator + " "+ username;
						//show nomedoc_user sec creator username
						sendBuffer.putInt(command.length());
						sendBuffer.put(command.getBytes());
						sendBuffer.flip();
						while(sendBuffer.hasRemaining())
							client.write(sendBuffer);
						sendBuffer.clear();
						//inviato il comando.
						//attendo stato sezione e relativa grandezza
						
						int dim = 0;
						while((dim += client.read(serverReplyBuffer))<4);
						serverReplyBuffer.flip();
						int status = serverReplyBuffer.getInt();
						if(status == -1) {
							System.out.println("Non hai accesso a questo documento.");
							serverReplyBuffer.clear();
							break;
						}
						System.out.println("[Turing client] lo stato della sezione è "+status);
						serverReplyBuffer.clear();
						//creo la cartella che conterrà le sezioni scaricate di questo documento
						if(!Files.exists(Paths.get(showDirectory))) Files.createDirectory(Paths.get(showDirectory));
						System.out.println("Cartella di show creata");
						dim = 0;
						while((dim += client.read(serverReplyBuffer))<4);
						serverReplyBuffer.flip();
						int filesize = serverReplyBuffer.getInt();
						System.out.println("[Turing client] la size della sezione è "+ filesize);
						serverReplyBuffer.clear();
						
						//ora ricevo il file
						String tmppath = showDirectory + "/section" + section;
						Files.deleteIfExists(Paths.get(tmppath));
						Files.createFile(Paths.get(tmppath));
						FileChannel tmpfile = FileChannel.open(Paths.get(tmppath),StandardOpenOption.READ,StandardOpenOption.WRITE);
						
						tmpfile.transferFrom(client, 0,filesize);
						tmpfile.close();
						System.out.printf("Sezione %d scaricata con successo.",section);
						if(status == 1) System.out.println("La sezione richiesta attualmente è occupata.");
						else System.out.println("La sezione richiesta è attualmente libera");
					}
					else {
						//turing show docname creator
						//mi ha richiesto la visualizzazione di un documento
						creator = token[3];
						documentName = token[2] + "_" + creator;
						
						command = token[1] + " " + documentName + " " + creator + " " +username;
						//show nomedoc_user username
						sendBuffer.putInt(command.length());
						sendBuffer.put(command.getBytes());
						sendBuffer.flip();
						while(sendBuffer.hasRemaining())
							client.write(sendBuffer);
						sendBuffer.clear();
						//inviato il comando.
						int dim = 0;
						while((dim += client.read(serverReplyBuffer))<4);
						serverReplyBuffer.flip();
						int totalSections = serverReplyBuffer.getInt();
						if(totalSections == -1) {
							System.out.println("Sembra che tu non abbia accesso a questo documento.");
							serverReplyBuffer.clear();
							break;
						}
						//se tutti i controlli sono andati a buon fine creo la cartella, se non esiste, che ospiterà la
						//sezione modificata
						String showDocDirectory = downloadedDoc + "/" + documentName;
						if(!Files.exists(Paths.get(showDocDirectory))) Files.createDirectory(Paths.get(showDocDirectory));
						System.out.println("Cartella di showDoc creata");
						System.out.println("Leggo il numero di sezioni da cui è composto il documento...");
						System.out.println("[Turing client] il numero di sezioni da scaricare è "+totalSections);
						serverReplyBuffer.clear();
						String tmppath;
						int[] statusCheck = new int[totalSections];
						int k=0;
						for(int i=0;i<totalSections;i++) {
							dim = 0;
							while((dim += client.read(serverReplyBuffer))<4);
							serverReplyBuffer.flip();
							int status = serverReplyBuffer.getInt();
							serverReplyBuffer.clear();
							if(status == 1) {
								k++;
								statusCheck[i] = 1;
							}
							//leggo la size della sezione
							dim = 0;
							while((dim += client.read(serverReplyBuffer))<4);
							serverReplyBuffer.flip();
							int filesize = serverReplyBuffer.getInt();
							System.out.println("[Turing client] la size della sezione è "+ filesize);
							serverReplyBuffer.clear();
								
							//ora ricevo il file
							tmppath = showDocDirectory + "/" + "section" + i;
							Files.deleteIfExists(Paths.get(tmppath));
							Files.createFile(Paths.get(tmppath));
							FileChannel tmpfile = FileChannel.open(Paths.get(tmppath),StandardOpenOption.READ,StandardOpenOption.WRITE);
							
							tmpfile.transferFrom(client, 0,filesize);
							tmpfile.close();
						}
						if(k>0) {
							System.out.print("Ecco le sezioni in fase di modifica:");
							for(int i=0;i<totalSections;i++) {
								if(statusCheck[i] == 1) {
									System.out.print("\nLa sezione " +i +" è in fase di modifica");
								}
							}
							System.out.printf("\n");
						}
						else System.out.println("Nessuna sezione attualmente è in fase di modifica");
					}
					break;
				case "logout":
					System.out.println("Eseguo il logout...");
					if(token.length != 2) {
						printShortUsage();
						break;
					}
					finish = true;
					input.clear();
					command = token[1] + " " + username;
					sendBuffer.putInt(command.length());
					sendBuffer.put(command.getBytes());
					sendBuffer.flip();
					while(sendBuffer.hasRemaining())
						client.write(sendBuffer);
					sendBuffer.clear();
					break;
				default:
					System.out.println("Comando non riconosciuto.\nProva a inserire turing --help per la lista dei comandi.");
				}
			}
		}
		in.close();
		waitEdit.close();
}
	
	private void showEditHelper() {
		System.out.println("\nBenvenuto nel tool di editing di TURING.");
		System.out.println("Editing Usage: turing COMMAND [ARGS...]");
		System.out.printf("commads:\nshow <doc> <sec> <creator> mostra una sezione del documento\n"
				+ "show <doc> <creator> mostra l'intero documento\n"
				+ "end-edit <doc> <sec> <creator> fine modifica della sezione del documento\n");
		System.out.printf("Nota che i parametri di end-edit devono essere consistenti\ncon quelli utilizzati per avviare il tool\n");
	}

	private void printShortUsage() {
		System.out.println("Comando non riconosciuto.\nInserisci turing --help per ulteriori informazioni.");
	}

	private void printUsage() {
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
	
	private boolean checkCommandLine(String toCheck) {
		//System.out.println(toCheck);
		if(toCheck.contains("help")) {
			printUsage();
			return false;
		}
		String[] tokenInput = toCheck.split(" ");
		if(tokenInput.length < 2) {
			printShortUsage();
			return false;
		}
		if(!tokenInput[0].equals("turing")) {
			printShortUsage();
			return false;
		}
		return true;
	}
}
