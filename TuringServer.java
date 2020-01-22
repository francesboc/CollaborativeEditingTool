
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

public class TuringServer {
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe TuringServer corrisponde al server e contiene tutti
	 * 			i meccanismi per la gestione delle operazioni previste dalla
	 * 			specifica.
	 * 
	 */
	private final static int REMOTE_PORT = 3000;
	private final static int SERVER_PORT =2000;
	private final static String HOST_ADDRESS = "127.0.0.1";
	private final static int BLOCK_SIZE = 1024;
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		//Classe per la gestione delle strutture dati che utilizza il server
		ServerStructures serverStruct = new ServerStructures();
		String lastMulticastIp = "224.0.1.2";
		Vector<MulticastManager> mAddress = new Vector<MulticastManager>();
		mAddress.add(new MulticastManager(lastMulticastIp,null));
		//Creazione della cartella di lavoro in cui salvo i documenti
		String currentDir = System.getProperty("user.dir");
		String allDocuments = currentDir + "/Documenti";
		Path documentPath = Paths.get(allDocuments);
		if(Files.exists(documentPath))
			deleteDir(documentPath.toFile());
		try {
			Files.createDirectory(documentPath);
		} catch (IOException e1) {
			System.out.println(e1.toString());
			return;
		}
		
		//attivazione del servizio remoto per la registrazione
		RegistrationServiceImpl registerService = new RegistrationServiceImpl(serverStruct.getUsers());
		RegistrationService stub;
		Registry r = null ;
		
		//buffer utilizzati per inviare/ricevere al/dal client
		ByteBuffer inBuffer = ByteBuffer.allocate(BLOCK_SIZE);
		ByteBuffer outBuffer = ByteBuffer.allocate(BLOCK_SIZE);
		ByteBuffer dimBuffer = ByteBuffer.allocate(4); //per ricevere le dimensioni dei comandi,file...
		
		int read = 0; //read byte count
		int dim = 0;
		String documentName;
		int section;
		String commandString;
		String username;
		String sectionPath = "";
		Document doc = null;
		String creator;
		String passwd;
		
		//publico il servizio remoto per eseguire la registrazione
		try {
			//la porta 0 indica la porta standard di RMI
			stub = (RegistrationService) UnicastRemoteObject.exportObject(registerService, 0);
			LocateRegistry.createRegistry(REMOTE_PORT);
			r = LocateRegistry.getRegistry(REMOTE_PORT);
			r.rebind("REGISTER-SERVICE", stub);
		} catch (RemoteException e) {
			System.out.println("Unable to export registratio service object" + e.toString());
			System.exit(0);
		} catch (NumberFormatException e) {
			System.out.println("port is invalid");
			System.exit(0);
		}
		
		HelloThread helloThread = new HelloThread(serverStruct.getUsers(),serverStruct.getOnlineUsers(),mAddress);
		helloThread.start();
		
		boolean finish = false;
		try {
			ServerSocketChannel server = ServerSocketChannel.open();
			server.bind(new InetSocketAddress(HOST_ADDRESS,SERVER_PORT));
			System.out.printf("[Turing server] %s in ascolto sulla porta %d\n",
					InetAddress.getLocalHost().getHostAddress(),SERVER_PORT);
			server.configureBlocking(false);
			Selector selector = Selector.open();
			server.register(selector,SelectionKey.OP_ACCEPT);
			ArrayList<Object> attach = null;
			while(!finish) {
				selector.select();
				Set<SelectionKey> readyKeys = selector.selectedKeys();
				Iterator<SelectionKey> iterator = readyKeys.iterator();
				while(iterator.hasNext()) {
					SelectionKey key = iterator.next();
					iterator.remove();
					if((key.isValid()) && (key.isAcceptable())) {
						server = (ServerSocketChannel) key.channel();
						SocketChannel client = server.accept();
						System.out.println("[Turing server] connesso con il client "+client.getRemoteAddress());
						client.configureBlocking(false);
						SelectionKey key2 = client.register(selector, SelectionKey.OP_READ);
						//array associato ad ogni client per inviare le risposte
						ArrayList<Object> attachment = new ArrayList<Object>();
						key2.attach(attachment);
					}
					if((key.isValid()) && (key.isReadable())) {
						SocketChannel client = (SocketChannel) key.channel();
						//leggo la size del comando
						read=0;
						while((read+= client.read(dimBuffer)) < 4 && read != -1);
						if(read == -1) client.close();
						else{
							StringBuilder cmd = new StringBuilder();
							dimBuffer.flip();
							dim = dimBuffer.getInt();
							dimBuffer.clear();
							//leggo il comando ricevuto dal client
							int i=0;
							while(i<dim) {
								i += client.read(inBuffer);
								inBuffer.flip();
								while(inBuffer.hasRemaining())
									cmd.append((char) inBuffer.get());
								inBuffer.clear();
							}
							commandString = cmd.toString();
							System.out.println("[Turing server] Comando ricevuto: "+commandString);
							String[] token = commandString.split(" ");
							
							switch(token[0]) {
								case "login":
									//login username password
									attach = (ArrayList<Object>) key.attachment();
									attach.add(0,token[0]);
									username = token[1];
									passwd = token[2];
									if(serverStruct.isRegistered(username, passwd)) {
										//username è effettivamente registrato
										if(!serverStruct.isOnline(username)) {
											//username non è ancora online, lo connetto
											if(serverStruct.isNotified(username)==1) {
												//ho ricevuto un invito mentre ero offline
												attach.add(1,2);
											}
											else attach.add(1,1);
											serverStruct.addUserOnline(username);
										}
										else attach.add(1,-1); //è già online
									}
									else attach.add(1,0); //username non registrato
									
									client.register(selector, SelectionKey.OP_WRITE,attach);
									break;
								case "loginNotify":
									//operazione che associa il thread listener di nuovi inviti al client corrente.
									//Comando particolare che non viene inviato direttamente dal client, ma dal thread
									//listener a lui associato
									username = token[1];
									serverStruct.putListener(username, client);
									break;
								case "create":
									//[0]	 [1]		   [2]	   [3]
									//create nomedocumento sezione username
									attach = (ArrayList<Object>) key.attachment();
									attach.add(0,token[0]);
									int numSezioni = Integer.parseInt(token[2]);
									username = token[3];
									
									//rendo il nome del documento univoco associandolo al nome del proprietario
									documentName = token[1] + "_" + username;
									String currentDocPath = allDocuments + "/" + documentName; 
									Path path = Paths.get(currentDocPath);
									if(!Files.exists(path)) {//se già esiste non lo creo di nuovo
										try {
											Files.createDirectories(path);
											try{
												serverStruct.addNewDocument(username, documentName, numSezioni,currentDocPath);
											}
											catch(NullPointerException e) {
												System.out.println(e.toString());
											}
											attach.add(1,true);
										}
										catch(IOException e) {
											System.out.println(e.toString());
											attach.add(1,false);
										}
									}
									else attach.add(1,false);
									client.register(selector, SelectionKey.OP_WRITE,attach);
									break;
								case "share":
									//[0]	[1]			  [2]	   [3]
									//share nomedocumento username creatore
									attach = (ArrayList<Object>) key.attachment();
									attach.add(0,token[0]);
									doc = null;
									documentName = token[1];
									username = token[2];
									creator = token[3];
									//controllo se l'utente che ha richiesto il comando sia l'effettivo creatore
									if((doc = serverStruct.checkDocumentPermission(documentName, creator))!=null) {
										//devo controllare se l'user da invitare che mi è stato passato è effettivamente registrato
										if(serverStruct.isUserRegistered(username)) {
											//l'utente è effettivamente registrato
											if(!serverStruct.isCollaborator(documentName,username,creator)) {
												//l'utente invitato è registrato e ancora non è un collaboratore
												attach.add(1,1);
												serverStruct.addCollaborator(documentName,username,creator);//aggiungo il nome "username" ai collaboratori
												serverStruct.addSharedDocument(username, doc); //aggiungo il documento ai documenti codivisi
												if(!serverStruct.isOnline(username)) serverStruct.notify(username); //setto il bit di notifica a 1
												else {
													//devo inviare la notifica al nuovo partecipante che è online
													serverStruct.notifyNow(username);
												}
											}
											else attach.add(1,-1); //l'utente è registrato ma è già un collaboratore
										}
										else{
											System.out.println("non registrato "+username);
											attach.add(1,0); //l'utente non è registrato
										}
									}
									else {
										attach.add(1,-2); //l'utente non è il creatore del documento
									}
									client.register(selector,SelectionKey.OP_WRITE,attach);
									break;
								case "list":
									//list username
									username = token[1];
									Vector<Document> myDoc = serverStruct.getOwnDocument(username);
									Vector<Document> sharedDoc = serverStruct.getSharedDocument(username);
									
									StringBuilder lista = new StringBuilder();
									for(int j=0; j<myDoc.size();j++) {
										lista.append(myDoc.get(j).toString());
										lista.append('-');
									}
									lista.append('/');
									for(int j=0; j<sharedDoc.size();j++) {
										lista.append(sharedDoc.get(j).toString());
										lista.append('-');
									}
									
									attach = (ArrayList<Object>) key.attachment();
									attach.add(0,token[0]);
									attach.add(1,lista);
									client.register(selector,SelectionKey.OP_WRITE,attach);
									break;
								case "edit":
									//[0]  [1] 			 [2] 	 [3]      [4]
									//edit nomedocumento sezione creatore username
									attach = (ArrayList<Object>) key.attachment();
									attach.add(0,token[0]);
									documentName = token[1];
									section = Integer.parseInt(token[2]);
									username = token[4];
									creator = token[3];
									//cerca di reperire il documento. Null se non lo trova
									if(creator.equals(username)) doc = serverStruct.getDocument(creator, documentName);
									else doc = serverStruct.getAccessToEdit(creator, documentName, username); //controllo se username è un collaboratore
									attach.add(1,doc);
									attach.add(2,section);
									attach.add(3,username);
									client.register(selector,SelectionKey.OP_WRITE,attach);
									break;
								case "end-edit":
									//[0]	   [1]		 	[2]	 [3]	  [4]
									//end-edit nomedoc_user sect creatore username
									documentName = token[1];
									section = Integer.parseInt(token[2]);
									username = token[4];
									Document docMod; //rappresenta il documento modificato
									//controllo se username corrisponde al creatore del documento, altrimenti devo cercare
									//il documento tra quelli condivisi.
									if(username.equals(token[3])) docMod = serverStruct.getDocument(username, documentName);
									else docMod = serverStruct.getAccessToEdit(token[3], documentName, username);
									Section sec = docMod.getSection(section);
									sec.releaseSection();
									
									sectionPath = docMod.getDocPath() +"/section"+section;
									Files.deleteIfExists(Paths.get(sectionPath));
									Files.createFile(Paths.get(sectionPath));
									FileChannel file = FileChannel.open(Paths.get(sectionPath), StandardOpenOption.WRITE);
									//leggo la dimensione
									dim = 0;
									while((dim += client.read(dimBuffer))<4);
									dimBuffer.flip();
									int filesize = dimBuffer.getInt();
									System.out.println("[Turing Server] la size del file da scrivere è "+filesize);
									dimBuffer.clear();
									//leggo il file che è stato modificato dal client per sovascriverlo con quello attualmente presente
									long transfered = 0;
									while((int) transfered < filesize)
										transfered += file.transferFrom(client, transfered,filesize);
									System.out.println("[Turing Server] file ricevuto correttamente " +transfered);
									
									//funzione usata per implementare la consistenza dei documenti in caso di crash del client.
									serverStruct.clearEditInfo(username);
									
									//se nessuno sta "usando" il documento, tolgo l'associazione con l'indirizzo di multicast
									if(docMod.decrEditingUsers()) { //se true => nessuno sta editando il documento
										int toRelease = releaseMulticastAddress(mAddress,docMod.getDocName());
										if(toRelease != -1) mAddress.get(toRelease).setAssociatedDocument(null);
									}
									
									break;
								case "show":
									//show nomedoc sec creator username
									if(token.length == 5) {
										documentName = token[1];
										section = Integer.parseInt(token[2]);
										creator = token[3];
										username = token[4];
										int status = -1;
										//controllo se username corrisponde al creatore del documento, altrimenti devo cercare
										//il documento tra quelli condivisi.
										if(creator.equals(username)) doc = serverStruct.getDocument(username, documentName);
										else doc = serverStruct.getAccessToEdit(creator,documentName, username);
										if(doc != null) {
											if(doc.getSectionsCount() <= section) {
												status = -1;
											}
											else{
												sectionPath = doc.getSectionPath(section);
												status = doc.getSection(section).getStatus();
											}
										}
										//ora invio lo status, la dimensione del file ed il file
										attach = (ArrayList<Object>) key.attachment();
										attach.add(0,token[0]);
										attach.add(1,sectionPath);
										attach.add(2,status);
										client.register(selector, SelectionKey.OP_WRITE, attach);
									}
									else {
										//show nomedoc creator username
										documentName = token[1];
										username = token[3];
										creator = token[2];
										if(creator.equals(username)) doc = serverStruct.getDocument(username, documentName);
										else doc = serverStruct.getAccessToEdit(creator,documentName, username);
										attach = (ArrayList<Object>) key.attachment();
										attach.add(0,token[0]);
										attach.add(1,doc);
										client.register(selector, SelectionKey.OP_WRITE, attach);
									}
									break;
								case "logout":
									username = token[1];
									serverStruct.disconnect(username);
									key.cancel();
									key.channel().close();
									break;
								default: System.out.println("Comando non riconosciuto.");
								}
						}
					}
					if((key.isValid()) && (key.isWritable())) {
						SocketChannel client = (SocketChannel) key.channel();
						attach = (ArrayList<Object>) key.attachment();
						switch((String) attach.get(0)) {
						case "login":
							int esito = (int) attach.get(1);
							outBuffer.putInt(esito);
							outBuffer.flip();
							//invio l'esito al client
							client.write(outBuffer);
							outBuffer.clear();
							attach.clear();
							client.register(selector, SelectionKey.OP_READ,attach);
							break;
						case "create":
							if((boolean) attach.get(1)) outBuffer.putInt(1);
							else outBuffer.putInt(0);
							outBuffer.flip();
							//invio l'esito al client
							client.write(outBuffer);
							outBuffer.clear();
							attach.clear();
							client.register(selector, SelectionKey.OP_READ,attach);
							break;
						case "share":
							outBuffer.putInt((int) attach.get(1));
							outBuffer.flip();
							client.write(outBuffer);
							outBuffer.clear();
							attach.clear();
							client.register(selector, SelectionKey.OP_READ,attach);
							break;
						case "list":
							StringBuilder lista = (StringBuilder) attach.get(1);
							int size = lista.toString().length();
							outBuffer.putInt(size);
							outBuffer.put(lista.toString().getBytes());
							outBuffer.flip();
							//invio la lista creata in precedenza
							while(outBuffer.hasRemaining())
								client.write(outBuffer);
							outBuffer.clear();
							attach.clear();
							client.register(selector, SelectionKey.OP_READ,attach);
							break;
						case "edit":
							doc = (Document) attach.get(1);
							if(doc != null) {
								//l'utente ha accesso alla modifica del file
								section = (int) attach.get(2);
								//ora devo controllare se la sezione è occupata
								Section thisSection = doc.getSection(section);
								//se uguale a null può significare che la sezione passata non è valida
								if(thisSection != null) {
									if(thisSection.getStatus()==1) {
										//la sezione è occupata
										outBuffer.putInt(-1); //invio messaggio di errore
										outBuffer.flip();
										client.write(outBuffer);
										attach.clear();
										outBuffer.clear();
										client.register(selector, SelectionKey.OP_READ,attach);
										break;
									}
									else {
										//sezione libera
										doc.getSection(section).setStatus();
										
										//associa l'indirizzo di multicast al documento
										if(doc.getEditingUsers() == 0) {
											String newAddress = getMulticastAddress(mAddress,doc.getDocName());
											if(newAddress == null) {//tutti gli indirizzi di multicast sono occupati
												lastMulticastIp = generateNextMulticastIp(lastMulticastIp);
												mAddress.add(new MulticastManager(lastMulticastIp,doc.getDocName()));
												doc.associateAddress(lastMulticastIp);
											}
											else doc.associateAddress(newAddress);
											doc.incrEditingUsers();
										}
										else doc.incrEditingUsers();
										
										//invio la risposta positiva al client
										outBuffer.putInt(1);
										outBuffer.flip();
										client.write(outBuffer);
										outBuffer.clear();
										
										//funzione usata per implementare la consistenza dei documenti in caso di crash del client.
										serverStruct.storeEditInfo((String) attach.get(3),doc,section);
										
										//ora devo inviare il contenuto della sezione
										System.out.println("[Turing server] sezione richiesta "+ Integer.toString(section));
										sectionPath = doc.getDocPath() +"/section" + Integer.toString(section);
										if(!Files.exists(Paths.get(sectionPath))) Files.createFile(Paths.get(sectionPath));
										FileChannel file = FileChannel.open(Paths.get(sectionPath));
										
										//invio la dimensione del file
										System.out.println("Invio la dimensione del file " + file.size());
										outBuffer.putInt((int)file.size());
										outBuffer.flip();
										client.write(outBuffer);
										outBuffer.clear();
										
										System.out.println("Invio del file in corso...");
										read = 0;
										while(read < (int)file.size())
											read+= (int) file.transferTo(read, file.size(), client);
										System.out.println("[Turing Server] file inviato con successo.");
										read=0;
										attach.clear();
										client.register(selector,SelectionKey.OP_READ,attach);
										//invio indirizzo multicast
										outBuffer.put(doc.getAddress().getBytes());
										outBuffer.flip();
										client.write(outBuffer);
										outBuffer.clear();
									}
								}
								else {//la sezione che mi è stata passata non esiste
									outBuffer.putInt(-1); //invio messaggio di errore
									outBuffer.flip();
									client.write(outBuffer);
									attach.clear();
									outBuffer.clear();
									client.register(selector, SelectionKey.OP_READ,attach);
									break;
								}
							}
							else{
								outBuffer.putInt(0); //l'utente non è ne un creatore ne un collaboratore del documento
								outBuffer.flip();
								client.write(outBuffer);
								attach.clear();
								outBuffer.clear();
								client.register(selector, SelectionKey.OP_READ,attach);
							}
							break;
						case "show":
							if(attach.size() == 3) { //riguarda la richiesta di una sezione
								sectionPath = (String) attach.get(1);
								int status = (int) attach.get(2);
								if(status != -1) {//se uguale a -1 non è stato trovato il documento
									Path path = Paths.get(sectionPath);
									//creo il file che conterrà il testo della sezione richiesta
									if(!Files.exists(path)) Files.createFile(path);
									FileChannel file = FileChannel.open(path);
									
									//invio lo stato della sezione
									System.out.println("Invio lo stato della sezione " + status);
									outBuffer.putInt(status);
									//invio la dimensione del file
									System.out.println("Invio la dimensione del file " + file.size());
									outBuffer.putInt((int)file.size());
									outBuffer.flip();
									client.write(outBuffer);
									outBuffer.clear();
									
									System.out.println("Invio il file");
									read = (int) file.transferTo(0, file.size(), client);
									System.out.println("file inviato " +read);
								}
								else {
									System.out.println("Invio lo stato della sezione " + status);
									outBuffer.putInt(status);
									outBuffer.flip();
									client.write(outBuffer);
									outBuffer.clear();
								}
							}
							else {
								Document requestedDoc = (Document) attach.get(1);
								if(requestedDoc != null) {
									int totalSection = requestedDoc.getSectionsCount();
									//invio il numero di sezioni da cui è composto il documento
									outBuffer.putInt(totalSection);
									outBuffer.flip();
									client.write(outBuffer);
									outBuffer.clear();
									//procedo all'invio delle singole sezioni
									for(int i=0;i<totalSection;i++) {
										sectionPath = requestedDoc.getSectionPath(i);
										Path path = Paths.get(sectionPath);
										if(!Files.exists(path)) Files.createFile(path);
										FileChannel file = FileChannel.open(path);
										outBuffer.putInt(requestedDoc.getSection(i).getStatus());
										outBuffer.flip();
										client.write(outBuffer);
										outBuffer.clear();
										//invio la dimensione della sezione
										System.out.println("Invio la dimensione del file " + file.size());
										outBuffer.putInt((int)file.size());
										outBuffer.flip();
										client.write(outBuffer);
										outBuffer.clear();
										System.out.println("Invio il file");
										read = (int) file.transferTo(0, file.size(), client);
										System.out.println("file inviato " +read);
									}
								}
								else {
									//il client non ha accesso al documento
									outBuffer.putInt(-1);
									outBuffer.flip();
									client.write(outBuffer);
									outBuffer.clear();
								}
							}
							read=0;
							attach.clear();
							client.register(selector,SelectionKey.OP_READ,attach);
							break;
						} 
					}
				}
			}
		}
		catch(IOException e) {
			System.out.println(e.toString());
		}
	}
	
	/*
	 * Restituisce il prossimo indirizzo di multicast per lanciare la chat
	 */
	public static String generateNextMulticastIp(String ip) {
		String[] ips = ip.split("\\.");
		Integer tmp;
		if(ips[0].equals("239")) return null;
		if(ips[3].equals("255")) {
			if(ips[2].equals("255")) {
				if(ips[1].equals("255")) {
					tmp = Integer.parseInt(ips[0]);
					tmp+=1;
					ips[0] = tmp.toString();
					ips[1] = "0";
					ips[2] = "0";
					ips[3] = "0";
				}
				else{
					tmp = Integer.parseInt(ips[1]);
					tmp+=1;
					ips[1] = tmp.toString();
				}
			}
			else{
				tmp = Integer.parseInt(ips[2]);
				tmp+=1;
				ips[2] = tmp.toString();
			}
		}
		else{
			tmp = Integer.parseInt(ips[3]);
			tmp+=1;
			ips[3] = tmp.toString();
		}
		return ips[0] + "." + ips[1] + "." + ips[2] + "." + ips[3]; 
	}
	
	public static String getMulticastAddress(Vector<MulticastManager> multicast, String nomeDoc) {
		int i;
		for(i=0;i<multicast.size();i++) {
			if(multicast.get(i).getAssociatedDocument() == null) { //indirizzo di multicast libero
				multicast.get(i).setAssociatedDocument(nomeDoc);
				return multicast.get(i).getMulticastAddress();
			}
		}
		return null;
	}
	
	public static int releaseMulticastAddress(Vector<MulticastManager> multicast, String nomeDoc) {
		int i=0;
		while(i<multicast.size()) {
			if(multicast.get(i).getAssociatedDocument().equals(nomeDoc)) 
				return i;
			i++;
		}
		return -1;
	}
	
	/*
	 * Funzione di clean-up.
	 */
	private static void deleteDir(File dir) {
		File[] files = dir.listFiles();
		for(int i=0; i<files.length;i++) {
			if(files[i].isDirectory())
				deleteDir(files[i]);
			else
				files[i].delete();
		}
		dir.delete();
	}

}
