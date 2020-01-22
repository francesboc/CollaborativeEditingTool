
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import javax.swing.JTextArea;

public class ChatReceiver implements Runnable{
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe ChatReceiver riceve in messaggi multicast e li mostra sulla
	 * 			chat associata alla modifica del documento.
	 * 
	 */

	private InetAddress multicastAddress;
	private JTextArea printArea;
	private boolean exit;
	private String username;
	
	public ChatReceiver(InetAddress ia, JTextArea jta,String user) {
		multicastAddress = ia;
		printArea = jta;
		exit = false;
		username = user;
	}
	
	@Override
	public void run() {
		try(MulticastSocket socket = new MulticastSocket(4500);){
			DatagramPacket packet = new DatagramPacket(new byte[512],512);
			socket.joinGroup(multicastAddress);
			do {
				socket.receive(packet);
				String toAppend = new String(packet.getData(),
									packet.getOffset(),
									packet.getLength(),
									"UTF-8");
				if(!toAppend.equals(username + " ha lasciato la chat\n"))
					printArea.append(toAppend);
				else {
					exit = true;
				}
			}
			while(!exit);
		}  catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
