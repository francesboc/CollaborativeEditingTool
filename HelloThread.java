
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class HelloThread extends Thread{
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe HelloThread rappresenta un thread che viene attivato dal
	 * 			server e che, con una certa frequenza, controlla le connessioni dei
	 * 			client, per verificare che queste siano ancora attive.
	 * 
	 */
	private ConcurrentHashMap<String,UserData> users;
	private Vector<String> onlineUsers;
	private Vector<MulticastManager> mAddress;
	private final static int TIME_WAIT = 180000; //millisecondi
	private ByteBuffer helloBuffer;
	private UserData userdata;
	private String name;
	private SocketChannel channel;
	
	public HelloThread(ConcurrentHashMap<String,UserData> users,Vector<String> onlineUsers,Vector<MulticastManager> mAddress) {
		this.users = users;
		this.onlineUsers = onlineUsers;
		helloBuffer = ByteBuffer.allocate(4);
		this.mAddress = mAddress;
	}

	@Override
	public void run() {
		while(true) {
			int i=0;
			try {
				Thread.sleep(TIME_WAIT);//180 secondi
			}
			catch(InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("[Hello Thread] in esecuzione...");
			for(i=0;i<onlineUsers.size();i++) {
				name = onlineUsers.get(i);
				userdata = users.get(name);
				
				//utilizzo il socket del listener associato al client
				//che ignorerà il messaggio.
				channel = userdata.getNotifyChannel();
				helloBuffer.putInt(0);
				helloBuffer.flip();
				//lock sul socket secondario
				userdata.socketLock.lock();
				try {
					channel.write(helloBuffer);
				}
				//unlcok socket secondario
				catch(IOException e) {
					userdata.socketLock.unlock();
					//il client non è più attivo
					helloBuffer.clear();
					onlineUsers.remove(name);
					i--;
					//devo controllare se il client è crashato mentre stava editando una sezione
					//in tal caso devo rilasciare la sezione
					
					userdata.documentLock.lock();
					if(userdata.getSelectedDocument() != null) {
						Document thisDocument = userdata.getSelectedDocument();
						thisDocument.getSection(userdata.getSelectedSection()).releaseSection();
						if(thisDocument.decrEditingUsers()) { //nessuno sta più modificando il documento
							//elimino anche l'indirizzo di multicast associato al documento
							int j=0;
							boolean found = false;
							while(!found) {
								if(mAddress.get(j).getAssociatedDocument().equals(thisDocument.getDocName())) {
									mAddress.get(j).setAssociatedDocument(null);
									found = true;
								}
								j++;
							}
						}
					}
					userdata.documentLock.unlock();
				}
				try {
					userdata.socketLock.unlock();
				}
				catch(IllegalMonitorStateException w) {}//già liberata nel precedente catch
				helloBuffer.clear();
			}
		}
	}
	
	
}
