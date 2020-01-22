
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RegistrationServiceImpl extends RemoteServer implements RegistrationService{
	/* 
	 * 
	 * Author:	Francesco Bocchi 550145 
	 * Brief:	La classe RSI implementa il RemoteServer che permette agli utenti di 
	 * 			registrarsi al servizio.	
	 */
	private static final long serialVersionUID = 1L;
	private ConcurrentMap<String,UserData> users;
	
	public RegistrationServiceImpl(ConcurrentHashMap<String,UserData> users) {
		this.users=users;
	}
	
	@Override
	public boolean registerClient(String username, String password) throws RemoteException {
		UserData tmp = users.putIfAbsent(username, new UserData(password));
		if(tmp == null) return true;
		else return false; //utente già registrato
	}

	@Override
	public int getUsersCount() throws RemoteException {
		return users.size();
	}
	

}
