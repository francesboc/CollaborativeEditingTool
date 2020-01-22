
public class MulticastManager {
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe MulticastManager è utilizzata per il riuso degli indirizzi
	 * 			di multicast. Un indirizzo di multicast relativo ad un documento è
	 * 			valido fino a quando esiste almeno un utente che sta modificando il
	 * 			documento.
	 * 
	 */
	private String multicastAddress;
	private String nomeDoc;
	
	public MulticastManager(String multicastAddress, String nomeDoc) {
		this.multicastAddress = multicastAddress;
		this.nomeDoc = nomeDoc;
	}
	
	public String getAssociatedDocument() {
		return nomeDoc;
	}
	
	public void setAssociatedDocument(String nomeDoc) {
		this.nomeDoc = nomeDoc;
	}
	
	public String getMulticastAddress() {
		return multicastAddress;
	}
}
