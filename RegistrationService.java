
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RegistrationService extends Remote{
	
	boolean registerClient(String username,String password) throws RemoteException;
	
	int getUsersCount() throws RemoteException;
}
