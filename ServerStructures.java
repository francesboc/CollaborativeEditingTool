
import java.nio.channels.SocketChannel;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class ServerStructures {
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe ServerStructure contiene le strutture dati utilizzate
	 * 			dal server (tabella hash degli utenti registrati e vettore degli
	 * 			utenti online) e permette di gestirle tramite diverse operazioni.
	 * 
	 */
	private ConcurrentHashMap<String,UserData> users; //per salvare gli utenti registrati
	private Vector<String> onlineUsers;//struttura che memorizza gli utenti online
	
	public ServerStructures () {
		users = new ConcurrentHashMap<String,UserData>();
		onlineUsers = new Vector<String>();
	}
	
	public ConcurrentHashMap<String,UserData> getUsers(){
		return users;
	}
	
	public Vector<String> getOnlineUsers(){
		return onlineUsers;
	}
	
	/*
	 * Restituisce il documento dell'utente user identificato dal nome docName,
	 * se esiste.
	 */
	public Document getDocument(String user,String docName) {
		UserData userdata = users.get(user);
		return userdata.getDocumentByName(docName);
	}
	
	/*
	 * Restituiscie il vettore dei documenti di cui l'utente name è proprietario.
	 */
	public Vector<Document> getOwnDocument(String name){
		if(name != null) {
			UserData userdata = users.get(name);
			return userdata.getMyDocument();
		}
		return null;
	}
	
	/*
	 * Restituiscie il vettore dei documenti a cui l'utente name collabora.
	 */
	public Vector<Document> getSharedDocument(String name){
		if(name != null) {
			UserData userdata = users.get(name);
			return userdata.getSharedDocument();
		}
		return null;
	}
	
	public void addSharedDocument(String username, Document doc) {
		UserData userdata = users.get(username);
		userdata.addToShared(doc);
	}
	
	public void addNewDocument(String username,String nomeDoc, int sections,String path) {
		if(username != null && nomeDoc != null) {
			UserData userdata = users.get(username);
			Document doc = new Document(username,nomeDoc,sections,path);
			userdata.addDocument(doc);
		}
	}
	
	/*
	 * Controlla se l'utente è registrato, andando a verificare nome utente e password.
	 */
	public boolean isRegistered(String username,String password) {
		if(username != null && password != null) {
			UserData savedPassword = users.get(username);
			if(savedPassword == null) return false;
			if(savedPassword.equals(new UserData(password))) return true;
			else return false;
		}
		return false;
	}
	
	/*
	 * Controlla se un utente è registrato al servizio Turing.
	 */
	public boolean isUserRegistered(String username) {
		if(username != null) {
			if(users.containsKey(username)) return true;
		}
		return false;
	}
	
	/*
	 * Controlla se l'utente username è proprietario del documento docName,
	 * oppure se è un collaboratore.
	 */
	public Document checkDocumentPermission(String docName, String username) {
		if(username!=null && docName != null) {
			UserData userdata = users.get(username);
			return userdata.getDocumentByName(docName);
		}
		return null;
	}
	
	/*
	 * Controlla specificatamente se l'utente user collabora al documento docname di cui
	 * creator ne è proprietario. Restituisce il documento.
	 */
	public Document getAccessToEdit(String creator, String docname, String user) {
		UserData userdata = users.get(creator);
		if(userdata == null) return null;
		Document doc = userdata.getDocumentByName(docname);
		if(doc == null) return doc;
		if(doc.collaborate(user)) return doc;
		else return null;
	}
	
	/*
	 * Simile a getAccessTOEdit, ma restituisce un booleano.
	 */
	public boolean isCollaborator(String docname, String invitedUser, String username) {
		UserData userdata = users.get(username);
		Document doc = userdata.getDocumentByName(docname);
		return doc.collaborate(invitedUser);
	}
	
	public void addCollaborator(String docname,String invitedUser,String username) {
		UserData userdata = users.get(username);
		Document doc = userdata.getDocumentByName(docname);
		doc.addContribute(invitedUser);
	}
	
	//implementano la consistenza
	public void storeEditInfo(String username, Document document, int section) {
		UserData userdata = users.get(username);
		//lock utilizzate per non entrare in conflitto con l'helloThread
		userdata.documentLock.lock();
		userdata.setSelectedDocuement(document);
		userdata.setSelectedSection(section);
		userdata.documentLock.unlock();
	}
	
	public void clearEditInfo(String username) {
		UserData userdata = users.get(username);
		userdata.documentLock.lock();
		userdata.setSelectedDocuement(null);
		userdata.setSelectedSection(-1);
		userdata.documentLock.unlock();
	}
	
	/*
	 * Associa un thread listener che ascolta gli inviti diretti al client di nome name.
	 */
	public void putListener(String name, SocketChannel notify) {
		UserData userdata = users.get(name);
		userdata.associateListener(notify);
	}
	
	/*
	 * Notifica l'invito immediatamente al thread listener dell'utente username.
	 */
	public void notifyNow(String username) {
		UserData userdata = users.get(username);
		userdata.notifyListener();
	}
	
	/*
	 * Imposta il valore della notifica. Uitlizzato se il client è offline, dunque
	 * l'invito non può essere consegnato immediatamente all'utente.
	 */
	public void notify(String username) {
		UserData userdata = users.get(username);
		userdata.setNotification();
	}
	
	/*
	 * Controlla lo stato della notifica dell'utente username.
	 */
	public int isNotified(String username) {
		UserData userdata = users.get(username);
		return userdata.getNotification();
	}
	
	public boolean addUserOnline(String username) {
		if(username != null) {
			onlineUsers.add(username);
			return true;
		}
		return false;
	}
	
	public boolean isOnline(String user) {
		return onlineUsers.contains(user);
	}
	
	public void disconnect(String user) {
		onlineUsers.remove(user);
	}
}
