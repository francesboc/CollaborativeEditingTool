
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;
/* 
 * 
 * Author:	Francesco Bocchi 550145 
 * Brief:	La classe UserData contiene tutte le informazioni associate ad un utente
 * 			registrato al servizio Turing. In particolare rappresenta i documenti di cui
 * 		  	l'utente è proprietario e quelli di cui è collaboratore.	
 * 
 */
public class UserData {
	private String password;
	private Vector<Document> myDocument;
	private Vector<Document> sharedToMe;
	private int notification;
	//lock per il socket secondario utilizzato anche dall'helloThread
	public ReentrantLock socketLock = new ReentrantLock();
	private SocketChannel notifyChannel;
	private ByteBuffer buffer;
	
	//utilizzate per garantire la consistenza dei documenti in caso di crash del client
	public final ReentrantLock documentLock = new ReentrantLock();
	private Document selectedDocument = null;
	private int selectedSection = -1;
	
	public UserData(String password) {
		buffer = ByteBuffer.allocate(4);
		this.password = password;
		sharedToMe = new Vector<Document>();
		myDocument = new Vector<Document>();
		notification = 0;
	}
	
	/*
	 * Associa il SocketChannel che verrà utilizzato per notificare immediatamente
	 * un nuovo invito di collaborazione.
	 */
	public void associateListener(SocketChannel notify) {
		notifyChannel = notify;
	}
	
	/*
	 * Notifica al listener associato a questo client la ricezione 
	 * di un nuovo invito immediatamente.
	 */
	public void notifyListener() {
		buffer.putInt(1);
		buffer.flip();
		socketLock.lock();
		try {
			notifyChannel.write(buffer);
		} catch (IOException e) {
			System.out.println("[Turing Server] Non è stato possibile notificare il client.");
		}
		socketLock.unlock();
		buffer.clear();
	}
	
	public SocketChannel getNotifyChannel() {
		return notifyChannel;
	}
	
	public Document getSelectedDocument() {
		return selectedDocument;
	}
	
	public int getSelectedSection() {
		return selectedSection;
	}
	
	public void setSelectedDocuement(Document doc) {
		selectedDocument = doc;
	}
	
	public void setSelectedSection(int sec) {
		selectedSection = sec;
	}
	/*
	 * Aggiunge un nuovo documento nel vettore di documenti a cui collaboro.
	 */
	public void addToShared(Document doc) {
		if(doc != null)
			sharedToMe.add(doc);
	}
	
	/*
	 * Aggiunge un nuovo documento nel vettore di documenti di cui sono proprietario.
	 */
	public void addDocument(Document doc) {
		if(doc != null)
			myDocument.add(doc);
	}
	
	public Vector<Document> getMyDocument(){
		return myDocument;
	}
	
	/*
	 * Imposta il valore di notifica. Utilizzato quando il client è offline.
	 */
	public void setNotification() {
		notification = 1;
	}
	
	/*
	 * Ogni volta che si richiede il valore di notifica, questo viene riportato a 0.
	 * Il motivo è che questa funzione viene richiamata dal server appena il client
	 * effettua il login.
	 */
	public int getNotification() {
		int tmp = notification;
		notification = 0;
		return tmp;
	}
	
	public Vector<Document> getSharedDocument(){
		return sharedToMe;
	}
	
	/*
	 * Ricerca il documento identificato dal nome passato come argomento prima
	 * tra i documenti di cui sono proprietario, poi tra quelli a cui
	 * collaboro.
	 */
	public Document getDocumentByName(String docName) {
		int i=0;
		while(i<myDocument.size()) {
			Document doc = myDocument.get(i);
			if(doc.getDocName().equals(docName)) return doc;
			i++;
		}
		i=0;
		while(i<sharedToMe.size()) {
			Document doc = sharedToMe.get(i);
			if(doc.getDocName().equals(docName)) return doc;
			i++;
		}
		return null;
	}

	public String getPassword() {
		return password;
	}
	
	/*
	 * Controlla se sono un collaboratore del documento di cui passo il nome
	 * come parametro.
	 */
	public boolean canAccess(String docName) {
		int i =0 ;
		while(i<sharedToMe.size()) {
			if(sharedToMe.get(i).getDocName().equals(docName))
				return true;
			i++;
		}
		return false;
	}
	
	/*
	 * Usata per verificare la correttezza sia del nome utente inserito 
	 * sia della password.
	 */
	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof UserData))
			return false;
		else {
			UserData that = (UserData) obj;
			return password.equals(that.getPassword());
		}
	}
	
	
}
