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
package org.onosproject.sol;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
//import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import static org.slf4j.LoggerFactory.getLogger;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
//import org.onlab.packet.Ethernet;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
//import org.onosproject.net.Host;
//import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.SolIntent;
import org.onosproject.net.intent.IntentService;
//import org.onosproject.net.packet.DefaultOutboundPacket;
//import org.onosproject.net.packet.InboundPacket;
//import org.onosproject.net.packet.OutboundPacket;
//import org.onosproject.net.packet.PacketContext;
//import org.onosproject.net.packet.PacketPriority;
//import org.onosproject.net.packet.PacketProcessor;
//import org.onosproject.net.packet.PacketService;
//import org.onosproject.net.intent
import org.onosproject.net.topology.TopologyService;
import java.util.ArrayList;
import java.io.IOException;
import org.json.simple.parser.ParseException;
import java.util.Iterator;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class SolApp {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    //@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    //protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    //private ReactivePacketProcessor processor = new ReactivePacketProcessor();
    private ApplicationId appId;
    private ArrayList<TrafficClass> trafficClassList = new ArrayList<TrafficClass>();

    @Activate
    public void activate() {
        appId = coreService.registerApplication("org.onosproject.sol");

        //packetService.addProcessor(processor, PacketProcessor.ADVISOR_MAX + 2);
        ParseSol parseSol = new ParseSol();
        try {
        	trafficClassList = parseSol.getTrafficClassesFromSol();
        } catch(IOException | ParseException e) {
        	log.info("Exception occurred while Parsing SOL generated JSON files. Not activating app.");
        	return;
        }
        //TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        //selector.matchEthType(Ethernet.TYPE_IPV4);
        TrafficSelector selector = DefaultTrafficSelector.emptySelector();
        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();
        Iterator<TrafficClass> tc_iter = trafficClassList.iterator();
        ConnectPoint srcId, dstId;
        SolToOnos S2O = new SolToOnos();
        
        while(tc_iter.hasNext()) {
        	TrafficClass tc = tc_iter.next();
        	srcId = new ConnectPoint(S2O.toDeviceId(tc.src), PortNumber.ALL);
        	dstId = new ConnectPoint(S2O.toDeviceId(tc.dst), PortNumber.ALL);
        	SolIntent intent = SolIntent.builder()
                    .appId(appId)
                    .ingressPoint(srcId)
                    .egressPoint(dstId)
                    .selector(selector)
                    .treatment(treatment)
                    .priority(100)
                    .build();

            intentService.submit(intent);
        }
        	
         //packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        System.out.println("Activated!! See if the intents have installed.");
        log.info("Started. All intents installed!");
    }

    @Deactivate
    public void deactivate() {
        //packetService.removeProcessor(processor);
        //processor = null;
    	System.out.println("Deactivated!! Intents would still be there!");
        log.info("Stopped");
    }
    /**
     * Packet processor responsible for forwarding packets along their paths.
     */
   /* private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            HostId srcId = HostId.hostId(ethPkt.getSourceMAC());
            HostId dstId = HostId.hostId(ethPkt.getDestinationMAC());

            // Do we know who this is for? If not, flood and bail.
            Host dst = hostService.getHost(dstId);
            if (dst == null) {
                flood(context);
                return;
            }

            // Otherwise forward and be done with it.
            setUpConnectivity(context, srcId, dstId);
            forwardPacketToDst(context, dst);
        }
    }

    // Floods the specified packet if permissible.
    private void flood(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }

    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    private void forwardPacketToDst(PacketContext context, Host dst) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(dst.location().port()).build();
        OutboundPacket packet = new DefaultOutboundPacket(dst.location().deviceId(),
                                                          treatment, context.inPacket().unparsed());
        packetService.emit(packet);
        log.info("sending packet: {}", packet);
    }
	
    // Install a rule forwarding the packet to the specified port.
    private void setUpConnectivity(PacketContext context, ConnectPoint srcId, ConnectPoint dstId) {
        TrafficSelector selector = DefaultTrafficSelector.emptySelector();
        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

        SolIntent intent = SolIntent.builder()
                .appId(appId)
                .ingressPoint(srcId)
                .egressPoint(dstId)
                .selector(selector)
                .treatment(treatment)
                .build();

        intentService.submit(intent);
    }*/

}