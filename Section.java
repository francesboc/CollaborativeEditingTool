
public class Section {
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe Section rappresenta una sezione, la più piccola
	 * 			unità modificabile dal servizio turing.
	 * 
	 */
	private int isBusy; //rappresenta lo stato della sezione (0 disponibile, 1 occupata)
	private String path; //il percorso che identifica la sezione
	
	public Section(String path) {
		isBusy = 0;
		this.path = path;
	}

	public int getStatus() {
		return isBusy;
	}
	
	public void setStatus() {
		isBusy = 1;
	}
	
	public void releaseSection() {
		isBusy = 0;
	}
	
	public String getPath() {
		return path;
	}
}
