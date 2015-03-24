

/**
# #Copyright (C) 2015, Delft University of Technology, Faculty of Electrical Engineering, Mathematics and Computer Science, Network Architectures and Services, Niels van Adrichem
#
# This file is part of NDNFlow.
#
# NDNFlow is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# NDNFlow is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with NDNFlow. If not, see <http://www.gnu.org/licenses/>.
**/

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.CCNInterestHandler;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.impl.CCNNetworkManager.NetworkProtocol;
import org.ccnx.ccn.profiles.ccnd.CCNDaemonException;
import org.ccnx.ccn.profiles.ccnd.FaceManager;
import org.ccnx.ccn.profiles.ccnd.PrefixRegistrationManager;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.json.*;

import static java.lang.System.out;



public class ClientManager implements CCNInterestHandler {
	
	private CCNHandle ccnHandle;
	private Socket sock;
	private DataOutputStream os;
	private BufferedReader is;
	
	private String controller;
	private int controller_port;
	private long dpid;
	private InetAddress ip;
		
	private Map<ContentName, ContentProps> content;
	private FaceManager fHandle;
	private PrefixRegistrationManager prfxMgr;
	
	
	
	

	public static void main(String[] args) {
		new ClientManager().run();
	}
	
	public void run()  {
		try {
			
			getConfig();
			
			connectCCNx();
			connectController();
			
			out.println("Connected to CCNx and Controller");
			
			announceContent();
			
			readInput();
			
			
		} catch (MalformedContentNameStringException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CCNDaemonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}

	private void readInput() throws JSONException, IOException {
		String input;
		while((input = is.readLine()) != null) {
			out.println(input);
			JSONObject jo = new JSONObject(input);
			String Type = jo.getString("Type");
			if (Type == null) {
				out.println("Received message without Type: " + input);
			} else if(Type.equals("Rule")) {
				
				try {
					
					ContentName prefix = ContentName.fromURI(jo.getString("Name"));
					String action = jo.getString("Action");					
					JSONArray actionParams = jo.getJSONArray("ActionParams");
					
					out.println("Add rule for prefix ccnx:"+prefix+", action: "+action+", "+actionParams);
					Integer faceID = fHandle.createFace(NetworkProtocol.valueOf(action), actionParams.getString(0), 9695);
					prfxMgr.registerPrefix(prefix, faceID, null);
					out.println("Added rule for prefix ccnx:"+prefix+", action: "+action+", "+actionParams);
					
				} catch (MalformedContentNameStringException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CCNDaemonException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
			} else {
				out.println("Received incorrect Type: " + Type);
			}
		}
	}

	private void announceContent() {
		JSONObject jo = new JSONObject();
		jo.put("Type", "AvailableContent");
		jo.put("Content", content);
		try {
			send(jo);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		out.println("Announced available content "+jo);
	}

	private void connectController() throws UnknownHostException, IOException {
		sock = new Socket(controller, controller_port);
		os = new DataOutputStream( sock.getOutputStream() );
		is = new BufferedReader(new InputStreamReader( sock.getInputStream() ));
		
		JSONObject jo = new JSONObject();
		jo.put("Type", "Announce");
		jo.put("DPID", dpid);
		jo.put("IP", ip.getHostAddress());
		
		send(jo);
		out.println("Sent "+jo);
	}

	private void connectCCNx() throws ConfigurationException, IOException, MalformedContentNameStringException, CCNDaemonException {
		ccnHandle = CCNHandle.open();
		//incomingCCNHandle.registerFilter(ContentName.fromURI("/"), this);
		int _flags = PrefixRegistrationManager.CCN_FORW_ACTIVE | PrefixRegistrationManager.CCN_FORW_LAST; //We only want to search for new mappings if no other mappings exist.
		ccnHandle.getNetworkManager().setInterestFilter(ContentName.fromURI("/"), this, _flags);
		
		fHandle = new FaceManager(ccnHandle);
		prfxMgr = new PrefixRegistrationManager(ccnHandle);
		
		
	}

	private void getConfig() throws IOException, MalformedContentNameStringException {
		Properties confFile = new Properties();
		FileInputStream confStream = new FileInputStream("config.properties");
		confFile.load(confStream);
		
		dpid = Long.decode( confFile.getProperty("dpid") );
		ip = InetAddress.getByName( confFile.getProperty("ip") );
	
		out.println("Datapath ID dpid="+ Long.toHexString(dpid) );
		controller = confFile.getProperty("server");
		controller_port = Integer.decode( confFile.getProperty("port", "6635") );
		out.println("Found server address: "+ controller + ":" + controller_port);
		
		content = new HashMap<ContentName, ContentProps>();
		
		String entrypoint;
		for(int i = 0; (entrypoint  = confFile.getProperty("entry."+i)) != null; i++)
		{
			int cost = Integer.parseInt( confFile.getProperty("entry."+i+".cost", "0") );
			int priority = Integer.parseInt( confFile.getProperty("entry."+i+".priority", "0") );
			
			content.put(ContentName.fromURI(entrypoint), new ContentProps(cost, priority));
			
			out.println("Found entrypoint  "+entrypoint+" @ cost="+cost+" and priority="+priority);
		}
	}

	@Override
	public boolean handleInterest(Interest intrst) {
		out.println("Received Interest: " + intrst.name());
		
		try {
				
				JSONObject jo = new JSONObject();
				jo.put("Type", "Interest");
				jo.put("Name", intrst.name().toString());
				out.println(jo);
				
				send(jo);
				
			} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return false;
	}

	public void send(JSONObject jo) throws IOException {
		synchronized (os) {
			os.writeBytes(jo.toString()+"\n");
			os.flush();
		}
	}

}
