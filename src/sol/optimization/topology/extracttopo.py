#!/usr/bin/python
import urllib2
import json
import networkx as nx
from networkx.readwrite import json_graph
#import matplotlib.pyplot as plt

def device_to_num(device_list,device_name):

	chassisid=0
	for device in device_list:
		if device['id'] == device_name:
			chassisid = int(device['chassisId'])
			break
	return chassisid-1 

def extractGraph(controllerIP):

	devices_dict = json.load(urllib2.urlopen("http://%s:8181/onos/v1/devices" %controllerIP))
	links_dict = json.load(urllib2.urlopen("http://%s:8181/onos/v1/links" %controllerIP))
	hosts_dict = json.load(urllib2.urlopen("http://%s:8181/onos/v1/hosts" %controllerIP))
	device_list = devices_dict['devices']
	link_list = links_dict['links']
	host_list = hosts_dict['hosts']

	no_of_devs = len(device_list)

	Grph=dict()
	Grph['directed'] = True
	Grph['graph'] = [['name','complete_graph(%d)'%(no_of_devs)]]
	Grph['nodes'] = []

	for device in device_list:
		Grph['nodes'].append({'id' : (int(device['chassisId']) - 1)})

	Grph['links'] = []

	for link in link_list:		
		Grph['links'].append({'source' : device_to_num(device_list,link['src']['device']),
							  'target' : device_to_num(device_list,link['dst']['device'])}) 
	
	Grph['multigraph'] = False
	G = json_graph.node_link_graph(json.loads(json.dumps(Grph,indent=4)))
	#plt.show(nx.draw(G))
	return G











