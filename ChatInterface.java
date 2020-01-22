
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class ChatInterface extends JFrame implements ActionListener{
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe ChatInterface gestisce l'interfaccia della chat
	 * 
	 */

	private static final long serialVersionUID = 1L;
	private JPanel panel1;
	private JTextField InputArea;
	private String username;
	private InetAddress multicastAddress;
	private JTextArea printArea;
	private ExecutorService es;
	private JButton chiudi;
	
	public ChatInterface(String username,InetAddress multicastAddress) {
		setTitle("Turing Chat");
		setSize(600,450);
		panel1 = new JPanel();
		panel1.setLayout(null);
		this.username = username;
		this.multicastAddress=multicastAddress;
		initChat();
	}
	
	public void initChat() {
		this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		
		JLabel statusMsg = new JLabel("Messaggi Ricevuti");
		statusMsg.setBounds(45,20,150,30);
		panel1.add(statusMsg);
		printArea = new JTextArea();
		printArea.setEditable(false);
		JScrollPane scrollPane = new JScrollPane(printArea);
		scrollPane.setBounds(45,50,280,300);
		panel1.add(scrollPane);
		JLabel inputLabel = new JLabel("Scrivi");
		inputLabel.setBounds(355,190,100,11);
		panel1.add(inputLabel);
		//campo di testo, si può leggere il valore inserito in questo campo dall'utente
		InputArea = new JTextField();
		InputArea.setBounds(355,215,200,100);
		InputArea.setEditable(true);
		panel1.add(InputArea);
		//pulsante che invia il testo editato nell'area JTextField, al costruttore viene passata la stringa che rappresenta
		//il testo visualizzato sul pulsante
		JButton invia = new JButton("invia");
		invia.setEnabled(true);
		invia.addActionListener(this);
		invia.setBounds(405,330,80,20);
		panel1.add(invia);
		
		chiudi = new JButton("chiudi");
		chiudi.setEnabled(true);
		chiudi.setBounds(405,70,80,20);
		chiudi.addActionListener(this);
		panel1.add(chiudi);
		//Aggiungo il pannello creato al JFrame (la finestra)
		getContentPane().add(panel1);
		es = Executors.newSingleThreadExecutor();
		es.submit(new ChatReceiver(multicastAddress,printArea,username));
	}
	
	public void closeChat() {
		chiudi.doClick();
		es.shutdown();
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		String op = e.getActionCommand();
		String sendMessage;
		try(DatagramSocket s = new DatagramSocket(4000)){
			if(op.equals("invia")) {
				sendMessage = username + ": " +InputArea.getText()+ "\n";
				//invio il messaggio richiesto agli altri partecipanti alla chat
					DatagramPacket p = new DatagramPacket(sendMessage.getBytes("UTF-8"),
							0,sendMessage.getBytes("UTF-8").length,
							multicastAddress,4500);
					s.send(p);
					InputArea.setText("");
			}
			if(op.equals("chiudi")) {
				sendMessage = username + " " +"ha lasciato la chat\n";
				DatagramPacket p = new DatagramPacket(sendMessage.getBytes("UTF-8"),
						0,sendMessage.getBytes("UTF-8").length,
						multicastAddress,4500);
				s.send(p);
				this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			}
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
}
