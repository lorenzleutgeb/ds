package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import cli.Command;
import cli.Shell;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import util.Config;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserver, INameserverCli, Runnable {
	
	
	/* fields for Config & args for shell */
	private Config config;
	private String componentName;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private Shell shell;

	private Registry registry;
	
	private  ConcurrentSkipListMap<String, INameserver> zones;
	private  ConcurrentSkipListMap<String, String> users;
	
	private String zone;
	private String domain;
 

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Nameserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		
		/* initialize collections for zones and users */
		this.zones = new ConcurrentSkipListMap<String, INameserver>();
		this.users = new ConcurrentSkipListMap<String, String>();
		
		/* setup config and args for shell */
		this.config = config;
		this.componentName = componentName;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		/* register shell */
		this.shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		this.shell.register(this);
	}

	@Override
	public void run() {
		/* start shell */
		(new Thread(this.shell)).start();
		
		/* read domain */
		this.domain = null;
		try{
			this.domain = this.config.getString("domain");
			printToShell("[[ Nameserver for " + domain + " has been started. ]]");
		}catch(java.util.MissingResourceException ex){
			printToShell("[[ Root-nameserver has been started. ]]");
		}
		
		
		/* nameserver is root-nameserver */
		if(this. domain==null){
			String bindingName = config.getString("root_id");
			
			this.domain = "root-domain";
			this.zone = "root-zone";
			
			/* Initialize Registry. */
			try {
				this.registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
			} catch (RemoteException e) {
				this.printToShell("[[ Creating Registry failed. ]]");
			}
			
			/* create remoteobject */
			INameserver remoteObject = null;
			try {
				remoteObject = (INameserver) UnicastRemoteObject
						.exportObject((Remote) this, 0);
			} catch (RemoteException e1) {
				this.printToShell("[[ Creating remote object failed. ]]");
			}
			
			/* binding remoteObject to registry */
			try {
				registry.bind(bindingName, remoteObject);
			} catch (AccessException e) {
				this.printToShell("[[ Binding to registry failed: AccessException ]]");
			} catch (RemoteException e) {
				this.printToShell("[[ Binding to registry failed: RemoteException ]]");
			} catch (AlreadyBoundException e) {
				this.printToShell("[[ Binding to registry failed: AlreadyBoundException ]]");
			}
			
			this.printToShell("[[ Bound to " + bindingName + ". ]]");
			
		/* nameserver is lower level */
		}else{
			String regHost = this.config.getString("registry.host");
			int regPort = this.config.getInt("registry.port");
			
			try {
				this.registry = LocateRegistry.getRegistry(regHost, regPort);
			} catch (RemoteException e) {
				this.printToShell("[[ Locating Registry failed. ]]");
			}
			
			String lookupName = this.config.getString("root_id");
			INameserver rootServer  = null;
			try {
				rootServer = (INameserver) this.registry.lookup(lookupName);
			} catch (AccessException e) {
				this.printToShell("[[ Looking up from registry failed: AccessException ]]");
			} catch (RemoteException e) {
				this.printToShell("[[ Looking up from registry failed: RemoteException ]]");
			} catch (NotBoundException e) {
				this.printToShell("[[ Looking up from registry failed: NotBoundException ]]");
			}
			
			/* create remoteobject */
			INameserver remoteObject = null;
			try {
				remoteObject = (INameserver) UnicastRemoteObject
						.exportObject((Remote) this, 0);
			} catch (RemoteException e1) {
				this.printToShell("[[ Creating remote object failed. ]]");
			}
			
			try {
				rootServer.registerNameserver(this.domain, remoteObject, remoteObject);
			} catch (RemoteException e) {
				this.printToShell("[[ Registering Nameserver failed:\n\tRemoteException: " + e.getMessage() + " ]]");
				this.teardown();
			} catch (AlreadyRegisteredException e) {
				this.printToShell("[[ Registering Nameserver failed:\n\tAlreadyRegisteredException: " + e.getMessage() + " ]]");
				this.teardown();
			} catch (InvalidDomainException e) {
				this.printToShell("[[ Registering Nameserver failed:\n\tInavlidDomainException: " + e.getMessage() + " ]]");
				this.teardown();
			}
		}
	}

	@Override
	@Command
	public String nameservers() throws IOException {
		
		if(this.zones.size()==0){
			return "No Nameservers registered on this Nameserver.";
		}
		
		String ret = "";
		Set<String> keys = this.zones.keySet();
		
		int zeros = (keys.size()/10);
		String formatString = (zeros > 0)? ("%" + zeros + "d. %20s\n") : ("%d. %20s\n");
		
		int cnt = 0;
		for(Iterator<String> i = keys.iterator();i.hasNext();){
			String key   = (String) i.next();
			cnt++;
			
			ret += (String.format(formatString, cnt, key));
		}
		
		return ret;
	}

	@Override
	@Command
	public String addresses() throws IOException {
		
		if(this.users.size()==0){
			return "No addresses registered on this Nameserver.";
		}
		
		
		String ret = "";
		Set<String> keys = this.users.keySet();

		int zeros = (keys.size()/10);
		String formatString = (zeros > 0)? ("%" + zeros + "d. %20s %20s\n") : ("d. %20s %20s\n");
		
		int cnt = 0;
		for(Iterator<String> i = keys.iterator();i.hasNext();){
			String key   = (String) i.next();
			String value = (String) this.users.get(key);
			cnt++;
			
			ret += (String.format(formatString, cnt, key, value));
		}
		
		return ret;
	}

	@Override
	@Command
	public String exit() throws IOException {
		this.teardown();
		
		
		return "[[ System is going down for shutdown now. ]]";
	}
	
	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
				System.in, System.out);

		new Thread(nameserver).start();
	}
	/* private methods */

	private void teardown(){
		/* close streams */
		this.shell.close();
		System.exit(1);

	}
	
	private void printToShell(String msg){
		try {
			this.shell.writeLine(msg);
		} catch (IOException e) {
			
		};
	}
	
	private String[] stripToResolve(String str){
		if(str == null){
			return null;
		}
		
		String[] ret =  null;
		int sep = str.lastIndexOf(".");
		
		if(sep==-1){
			ret = new String[1];
			ret[0] = str;
			return ret;
		}
		
		ret = new String[2];
		ret[0] = str.substring(0, sep);
		ret[1] = str.substring(sep+1);
		
		return ret;
	}

	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		String[] splitStr = this.stripToResolve(zone);
		INameserver ret = null;
		
		if(splitStr == null){
			this.printToShell("[[ Bad Arguments for lookup passed. ]]");
			return null;
		}
		
		if(splitStr.length==1){
			ret = this.zones.get(splitStr[0]);
			
			if(ret == null){
				this.printToShell("[[ No user matching '" + zone + "' found. ]]");
			}
			
			return ret;
		}
		
		INameserver next=this.zones.get(splitStr[1]);
		
		if(next==null){
			this.printToShell("[[ No zone matching '" + splitStr[1] + "' found. ]]");
			return null;
		}
		
		this.printToShell("[[ Came across " + this.domain + " going deeper to find " + splitStr[0] + "]]");
		return next.getNameserver(splitStr[0]);
	}

	@Override
	public String lookup(String username) throws RemoteException {
		String[] splitStr = this.stripToResolve(username);
		String ret = null;
		
		if(splitStr == null){
			this.printToShell("[[ Bad Arguments for lookup passed. ]]");
			return null;
		}
		
		if(splitStr.length==1){
			ret = this.users.get(splitStr[0]);
			
			if(ret == null){
				this.printToShell("[[ No user matching '" + username + "' found. ]]");
			}
			
			return ret;
		}
		
		INameserver next=this.zones.get(splitStr[1]);
		
		if(next==null){
			this.printToShell("[[ No zone matching '" + splitStr[1] + "' found. ]]");
			return null;
		}
		
		this.printToShell("[[ Came across " + this.domain + " going deeper to find " + splitStr[0] + "]]");
		return next.lookup(splitStr[0]);
		
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
					throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		
		String[] splitStr = this.stripToResolve(domain);
		
		if(splitStr == null || nameserver == null || nameserverForChatserver == null){
			this.printToShell("[[ Bad Arguments for registering nameserver passed. ]]");
			return;
		}
		
		if(splitStr.length==1){			
			if(this.zones.get(splitStr[0]) != null){
				throw new AlreadyRegisteredException("[[ Already registered a nameserver known as '" + splitStr[0] + "' . Therefore " + domain + " cannot be registered on " + this.domain + ".");
			}
			
			this.zones.put(splitStr[0], nameserver);
			this.printToShell("[[ Successfully registered " + splitStr[0] + " to domain " + this.domain + "]]");
			return;
		}
		
		INameserver next=this.zones.get(splitStr[1]);
		
		if(next==null){
			this.printToShell("[[ No zone matching '" + splitStr[1] + "' found. ]]");
			throw new InvalidDomainException("No zone matching '" + splitStr[1] + "' found. Therefore " + domain + " cannot be registered on " + this.domain + "." );
		}
		
		this.printToShell("[[ Came across " + this.domain + " going deeper to find " + splitStr[0] + "]]");
		next.registerNameserver(splitStr[0], nameserver, nameserverForChatserver);
	}
}
