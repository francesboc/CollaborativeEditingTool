
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
//thread che intercetta le notfiche di nuovi inviti
public class Listener extends Thread {
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe Listener rappresenta il thread che ascolta le notifiche
	 * 			associato ad uno specifico client.
	 * 
	 */

	private int port;
	private String host;
	private String username;
	
	public Listener(int port,String host,String username) {
		this.port = port;
		this.host = host;
		this.username = username;
	}
	@Override
	public void run() {
		ByteBuffer outBuffer = ByteBuffer.allocate(256);
		ByteBuffer inBuffer = ByteBuffer.allocate(4);
		String loginNotify = "loginNotify" + " " + username;
		try(SocketChannel notify = SocketChannel.open(new InetSocketAddress(host,port))){
			//richiedo al server l'associazione di questo listener all'utente di nome username
			outBuffer.putInt(loginNotify.length());//inserisco lunghezza
			outBuffer.put(loginNotify.getBytes());//inserisco il comando
			outBuffer.flip();
			while(outBuffer.hasRemaining())
				notify.write(outBuffer);
			outBuffer.clear();
			
			//mi metto in attesa degli inviti
			boolean finish = false;
			while(!finish) {
				try{
					notify.read(inBuffer);
				}
				catch(ClosedByInterruptException e) {
					//interrotto poichè il client è terminato
					finish = true;
				}
				if(!finish) {
					inBuffer.flip();
					//se uguale a 1 ho ricevuto un invito e stampo la notifica
					//se uguale a 0 allora è un messaggio dal thread hello, lo ignoro.
					if(inBuffer.getInt() == 1) {
						System.out.println("Hai ricuevuto un invito ad una collaborazione.");
						System.out.printf("\n>$ ");
					}
					inBuffer.clear();
				}
			}
		} catch (IOException e) {
			System.out.println(e.toString());
		}
	}

}
