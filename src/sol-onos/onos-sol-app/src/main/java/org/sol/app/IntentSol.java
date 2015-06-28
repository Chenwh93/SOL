/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sol.app;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.Constraint;
import org.onosproject.net.intent.HostToHostIntent;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.ConnectivityIntent;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.onlab.packet.Ethernet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import org.onosproject.net.apps.ForwardingMapService;

import com.google.common.collect.Lists;

@Component(immediate = true)
//@Service
public class IntentSol //implements ForwardingMapService
{

    private final Logger log = LoggerFactory.getLogger(getClass());
	
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
	protected CoreService coreService; //Service for interacting with the core system of the controller
	
	@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService; //Service for providing network topology information
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;//Service for intercepting data plane packets and for emitting synthetic outbound packets
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentService intentService;//Service for application submitting or withdrawing their intents
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService; //Service for interacting with the inventory of end-station hosts
    
    private PacketIntentProcessor processor = new PacketIntentProcessor();
    private ApplicationId appId;
    private final Set<Intent> existingIntents = new HashSet<>();
    
    protected final ConcurrentMap<HostId, HostId> endPoints = new ConcurrentHashMap<>();
    
    @Activate
    protected void activate() {
        log.info("Started");
        System.out.println("Activated v1!");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
        System.out.println("Deactivated v1!");
    }

	private class PacketIntentProcessor implements PacketProcessor
	{
		@Override
		public void process(PacketContext context) //Represents context for processing an inbound packet, and (optionally) emitting a corresponding outbound packet
		{
		// Stop processing if the packet has been handled, since we
		// can't do any more to it.
			System.out.println("Packet processing started!");
			log.info("Packet processing started!");
			if (context.isHandled()) {
		            return;
		        }
		    
			InboundPacket pkt = context.inPacket(); //Input packet object
		    Ethernet ethPkt = pkt.parsed(); //Store parsed packet components in Ethernet class
		    
		    HostId srcId = HostId.hostId(ethPkt.getSourceMAC());
		    HostId dstId = HostId.hostId(ethPkt.getDestinationMAC());
		    
		    Host dst = hostService.getHost(dstId);
		    if (dst == null) {
		    	System.out.println("Flooding!!");
		        flood(context);		   
		        return;
		    }

		    // Otherwise forward and be done with it.
		    endPoints.put(srcId, dstId);
		    setUpConnectivity(context, srcId, dstId);
		    forwardPacketToDst(context, dst);
		}
	}
	
	// Floods the specified packet if permissible.
    private void flood(PacketContext context) 
    {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) 
        {
        	//deciding whether broadcast is allowed from the origination point of the packet
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();//Blocks the outbound packet from being sent from this point onward
        }
    }
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }
    private void forwardPacketToDst(PacketContext context, Host dst) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(dst.location().port()).build();
        OutboundPacket packet = new DefaultOutboundPacket(dst.location().deviceId(),
                                                          treatment, context.inPacket().unparsed());
        System.out.println("Emitting packet!");
        packetService.emit(packet);
        log.info("sending packet: {}", packet);
    }

    // Install a rule forwarding the packet to the specified port.
    private void setUpConnectivity(PacketContext context, HostId srcId, HostId dstId) {
        TrafficSelector selector = DefaultTrafficSelector.builder().build();
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().build();
        List<Constraint> constraint = Lists.newArrayList();
        HostToHostIntent intent = HostToHostIntent.builder()
               .appId(appId)
               .one(srcId)
               .two(dstId)
               .selector(selector)
               .treatment(treatment)
               .constraints(constraint)
               .build();
        System.out.println("Installing Rules!");
        existingIntents.add(intent);
        intentService.submit(intent);
    }
    /*@Override    
    public Map<HostId, HostId> getEndPoints() 
    {        
    	return Collections.unmodifiableMap(endPoints);   
    }*/
}












