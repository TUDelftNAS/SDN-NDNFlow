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

from pox.core import core
from pox.lib.util import *
import pox.openflow.libopenflow_01 as of
import threading
import json
from collections import defaultdict
import forwarding

log = core.getLogger()
enabledSwitches = {}
availableContentByDPID = {}
availableContentByName = {}

class Content:
	def __init__ (self, dpid, name, cost, priority):
		self.dpid = dpid
		self.name = name
		self.cost = cost
		self.priority = priority

class Switch:
	def __init__ (self, dpid, conn, ip):
		self.dpid = dpid
		self.conn = conn
		self.ip = ip
		
def _get_path(src, dsts):
	##Copy original adjacency
	#adj = defaultdict(lambda: defaultdict(lambda: None) )
	#for (key, value) in forwarding.adj:
	#	adj[key] = value.copy()
	
	#Reference original adjacency matrix
	adj = forwarding.adj
		
	#Bellman-Ford algorithm
	keys = forwarding.switches.keys()
	distance = {}
	previous = {}
	
	for dpid in keys:
		distance[dpid] = float("+inf")
		previous[dpid] = None

	distance[src] = 0
	for i in range(len(keys)-1):
		for u in adj.keys(): #nested dict
			for v in adj[u].keys():
				w = 1
				if distance[u] + w < distance[v]:
					distance[v] = distance[u] + w
					previous[v] = u 

	for u in adj.keys(): #nested dict
		for v in adj[u].keys():
			w = 1
			if distance[u] + w < distance[v]:
				log.error("Graph contains a negative-weight cycle")
				return None
	
	#Determine cheapest destination from set of destinations
	dst = None
	cost = float("+inf")
	for _dst in dsts:
		if (_dst.cost + distance[_dst.dpid]) < cost:
			cost = _dst.cost + distance[_dst.dpid]
			dst = _dst.dpid
	
	first_port = None
	v = dst
	u = previous[v]
	while u is not None:
		if u == src:
			first_port = adj[u][v]
		
		v = u
		u = previous[v]
				
	return forwarding.Path(src, dst, previous, first_port)  #path

class CCNxPlugin (object):
	
		
	def SockClient(self, conn):
		log.debug("Client connection thread started")
		buffer = ''
		data = True
		while data:					#Created our own buffered reader
			data = conn.recv(4096)
			buffer += data
			
			while buffer.find('\n') != -1:
				msg, buffer = buffer.split('\n', 1)
				
				#Actual \n delimited messages
				log.debug("Received message: "+ msg)
				tMsg = json.loads(msg)
				if tMsg["Type"] == "Interest":
					log.debug("%s received Interest message for name %s"%(dpid, tMsg["Name"]))
					prefix = tMsg["Name"]
					
					#Do prefix match, also check if resulting list is actually filled
					while prefix != "" and (prefix not in availableContentByName or not availableContentByName[prefix]):
						log.debug("Could not find prefix %s"%(prefix))
						prefix = prefix[:prefix.rfind("/")]
						
					if prefix == "":
						log.debug("Could not find any prefix matches for Interest name")
						continue #with next message
							
					log.debug("Found %d elements for prefix %s"%(len(availableContentByName[prefix]), prefix))
					
					#select exclusively highest prio (thus minimal value) content elements)
					#Might want to look into a priority queue for this, as theoretical number of requests exceeds number of inserts, updates and deletes
					minPrio = min([ci.priority for ci in availableContentByName[prefix].values()])
					prioContent = [ci for ci in availableContentByName[prefix].values() if ci.priority == minPrio]

					log.debug("Found %d remaining elements for prio = %d"%(len(prioContent), minPrio))
					
					prev_path = _get_path(dpid, prioContent)
					
					if prev_path is None:
						log.warn( "No path found from %s to %s"%(dpid_to_str(prev_path.src), dpid_to_str(prev_path.dst)) )
						continue #with next message
					
					log.debug( "Path found from %s to %s over path %s"%(dpid_to_str(prev_path.src), dpid_to_str(prev_path.dst), prev_path) )
					
					#Assume src and dst to be enabled, technically has to be, else the complete thing would not go through, but still.
					assert prev_path.dst in enabledSwitches
					assert prev_path.src in enabledSwitches
					
					subdst = prev_path.dst
					subsrc = prev_path.prev[subdst]
					while subsrc != None:
						if subsrc in enabledSwitches:
							log.debug("Found subpath %s to %s"%(dpid_to_str(subsrc), dpid_to_str(subdst)) )
							nMsg = json.dumps ( { 	'Type' : 'Rule',
												'Name' : prefix,
													'Action' : 'TCP',
													'ActionParams' : [ enabledSwitches[subdst].ip ] } )

							enabledSwitches[subsrc].conn.send ( nMsg + '\n' )
							log.debug("Send %s to %s"%(nMsg, dpid_to_str(subsrc)) )
							subdst = subsrc
						subsrc = prev_path.prev[subsrc]
					
						
					
					#log.debug("Name can be found on DPIDs %s"%(dpids))
					#nMsg = json.dumps( { 	'Type' : 'Rule',
					#					'Name' : '/test/ping',
					#						'Action' : 'TCP',
					#						'ActionParams' : ['10.0.0.7'] } )
					#log.debug ( nMsg )
					#conn.send( nMsg + '\n' )
					
				elif tMsg["Type"] == "Announce":
					assert dpid == None #You cannot change the DPID of an already connected switch as it would invalidate path computations, available content, etc.
					assert ip == None #Same here
					
					dpid = int(tMsg["DPID"])
					ip = tMsg["IP"]
					
					enabledSwitches[dpid] = Switch(dpid, conn, ip)
					log.debug("Switch with dpid announced itself %s" % (dpid_to_str(dpid)) )
					
				elif tMsg["Type"] == "AvailableContent":
					#availableContentByDPID[dpid] = tMsg["Content"]
					availableContentByDPID[dpid] = {}
					for (name, value) in tMsg["Content"].items():
						log.debug("Add %s : %s"%(name, json.dumps(value)) )
						availableContentByDPID[dpid][name] = Content(dpid, name, value["cost"], value["priority"])
						
						if name not in availableContentByName:
							availableContentByName[name] = {}
						availableContentByName[name][dpid] = Content(dpid, name, value["cost"], value["priority"])
					
					log.debug("%s updated AvailableContent to %d items"%(dpid_to_str(dpid), len(availableContentByDPID[dpid])))
				else:
					log.debug("Unknown message: " + tMsg["Type"])				
					
		conn.close()
		
		#Remove dpid from enabledSwitches
		del enabledSwitches[dpid]
		
		#Still need to clean availableContent
		###		
		
		log.debug("End of thread")

	def SockListener(self,sock):
		log.debug("I am your SockListener thread")
		while 1:
			(clientsock, address) = sock.accept()
			log.debug("New connection accepted")
			threading.Thread(target=self.SockClient, args=(clientsock,)).start()
				
	def __init__(self, *args, **kwargs):
		core.openflow.addListeners(self)
		log.debug("Added Openflow Listeners")
		sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
		sock.bind( ('', 6635) )
		sock.listen(5)
		log.debug("Created Socket")
		threading.Thread(target=self.SockListener, args=(sock,)).start()
		log.debug("Gave socket to async thread")
		
	def _handle_ConnectionUp (self, event):
		if event.dpid in enabledSwitches :
			log.debug("NDN overlay enabled switch %s has come up.", dpid_to_str(event.dpid))
			
		else:
			log.debug("Switch %s has come up.", dpid_to_str(event.dpid))

def launch ():
	
	log.debug("Registering CCNxPlugin")
	
	core.registerNew(CCNxPlugin)
	
	log.debug("Registered CCNxPlugin")
	
