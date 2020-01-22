import java.util.Vector;

public class Document {
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe Document rappresenta i documenti che gli utenti
	 * 			possono creare. 
	 */
	private String creator; //creatore del documento
	private int editingUsers; //# di sezioni di questo documento
	private String docName; 
	private String path; //percorso che identifica la posizione del documento
	private Vector<Section> sections; //vettore che rappresenta le singole sezioni
	
	private Vector<String> collaborators;
	private String address; //indirizzo di multicast usato per la chat
	
	
	public Document(String creator, String docName, int numSections,String path) {
		this.path = path;
		this.creator = creator;
		this.docName = docName;
		editingUsers = 0;
		sections = new Vector<Section>(numSections);
		for(int i=0;i<numSections;i++)
			sections.add(new Section(new String(path+"/section" + Integer.toString(i))));
		collaborators = new Vector<String>();
	}
	
	public void associateAddress(String address) {
		this.address = address;
	}
	
	public String getAddress() {
		return address;
	}
	
	public String getSectionPath(int section) {
		return sections.get(section).getPath();
	}
	
	public String getDocPath() {
		return path;
	}
	
	public String getDocName() {
		return docName;
	}
	
	public String getCreator() {
		return creator;
	}
	
	public Section getSection(int section) {
		if(section < sections.size())
			return sections.get(section);
		else return null;
	}
	
	public int getSectionsCount() {
		return sections.size();
	}
	
	/*
	 * Aggiiunge l'utente invitedUser all'insieme dei collaboratori
	 */
	public void addContribute(String invitedUser) {
		collaborators.add(invitedUser);
	}
	
	/*
	 * Controlla se l'utente user è un collaboratore
	 */
	public boolean collaborate(String user) {
		return collaborators.contains(user);
	}
	
	public int getEditingUsers() {
		return editingUsers;
	}
	
	public void incrEditingUsers() {
		editingUsers++;
	}
	
	public boolean decrEditingUsers() {
		editingUsers--;
		if(editingUsers > 0) return false;
		return true;
	}
	
	@Override
	public String toString() {
		return new String(docName + " " + creator);
	}
}
