/*
 * Copyright (c) 2015 Cisco System, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.maple.impl;

import org.maple.core.Action;
import org.maple.core.Controller;
import org.maple.core.Drop;
import org.maple.core.MapleSystem;
import org.maple.core.Punt;
import org.maple.core.Rule;
import org.maple.core.ToPorts;
import org.maple.core.TraceItem;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.maple.impl.utils.FlowUtils;
import org.opendaylight.maple.impl.utils.InstanceIdentifierUtils;
import org.opendaylight.maple.impl.utils.PacketUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowTableRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.RemoveFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.RemoveFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRemoved;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorUpdated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRemoved;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeUpdated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.OpendaylightInventoryListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class ODLControllerAdapter implements Controller,
                                             DataChangeListener,
                                             PacketProcessingListener,
                                             OpendaylightInventoryListener {

    protected static final Logger LOG = LoggerFactory.getLogger(ODLControllerAdapter.class);

    private MapleSystem maple;
    private PacketProcessingService pps;
    private SalFlowService fs;
    private Map<Integer, NodeConnectorRef> portToNodeConnectorRef;
    private InstanceIdentifier<Node> nodePath;
    private InstanceIdentifier<Table> tablePath;
    private short flowTableId = 0;
    private AtomicLong flowIdInc = new AtomicLong();

    private static final String LOCAL_PORT_STR = "LOCAL";

    public ODLControllerAdapter(PacketProcessingService pps, SalFlowService fs) {
        this.pps = pps;
        this.fs = fs;
    }

    public void start() {
        LOG.debug("start() -->");
        this.maple = new MapleSystem(this);
        System.out.println("Maple Initiated");
        this.portToNodeConnectorRef = new HashMap<>();
        this.nodePath = InstanceIdentifierUtils.createNodePath(new NodeId("node_001"));
        LOG.debug("start() <--");
    }

    public void stop() {
        LOG.debug("stop() -->");
        LOG.debug("stop() <--");
    }

    private static int portIDToSwitchNum(String portID) {
        return Integer.parseInt(portID.substring(
                portID.indexOf(':') + 1, portID.lastIndexOf(':')));
    }

    private static int portIDToPortNum(String portID) {
        return Integer.parseInt(portID.substring(portID.lastIndexOf(':') + 1));
    }

    private static int switchPortHashCode(long switchNum, int portNum) {
        final int prime = 5557;
        int result = 1;
        result = prime * result + (int) switchNum;
        result = prime * result + portNum;
        return result;
    }

    private NodeConnectorRef portPlaceHolder(int hashCode) {
        if (this.portToNodeConnectorRef.containsKey(hashCode))
            return this.portToNodeConnectorRef.get(hashCode);
        else {
            String msg = "port [" + hashCode + "] does not exist in map";
            //throw new IllegalArgumentException(msg); //FIXME: temporal fix. Remove this part if broadcast is supported.
            return null;
        }
    }

    private void installPuntRule(Rule rule, int outSwitch) {
        InstanceIdentifier<Table> tableId = getTableInstanceId(this.nodePath);
        InstanceIdentifier<Flow> flowId = getFlowInstanceId(tableId);

        Future<RpcResult<AddFlowOutput>> result;
        Match m = matchForRule(rule);
        Flow flow =
                FlowUtils.createPuntFlow(this.flowTableId, rule.priority, m)
                        .build();
        result = addFlow(this.nodePath, tableId, flowId, flow);
    }

    private void installToPortRule(Rule rule, int outSwitch, int[] outPorts) {

        NodeConnectorRef dstPorts[] = new NodeConnectorRef[outPorts.length];
        for (int i = 0; i < outPorts.length; i++) {
            dstPorts[i] = this.portToNodeConnectorRef.get(switchPortHashCode(outSwitch, outPorts[i]));
            if (dstPorts[i] == null) {
                System.out.println("!!!!!!!! WARNING - NOT INSTALLING RULE: " + rule + "!!!!!!!!!!!!!!");
                return;
            }
        }

        InstanceIdentifier<Table> tableId = getTableInstanceId(this.nodePath);
        InstanceIdentifier<Flow> flowId = getFlowInstanceId(tableId);

        Future<RpcResult<AddFlowOutput>> result;
        Match m = matchForRule(rule);
        Flow flow =
                FlowUtils.createToPortFlow(this.flowTableId, rule.priority, m, dstPorts)
                        .build();
        result = addFlow(this.nodePath, tableId, flowId, flow);
    }

    private void removePuntRule(Rule rule, int outSwitch) {
        InstanceIdentifier<Table> tableId = getTableInstanceId(this.nodePath);
        InstanceIdentifier<Flow> flowId = getFlowInstanceId(tableId);

        Future<RpcResult<RemoveFlowOutput>> result;
        Match m = matchForRule(rule);
        Flow flow =
                FlowUtils.createPuntFlow(this.flowTableId, rule.priority, m)
                        .build();
        result = removeFlow(this.nodePath, tableId, flowId, flow);
    }

    private void removeToPortRule(Rule rule, int outSwitch, int[] outPorts) {

        NodeConnectorRef dstPorts[] = new NodeConnectorRef[outPorts.length];
        for (int i = 0; i < outPorts.length; i++) {
            dstPorts[i] = this.portToNodeConnectorRef.get(switchPortHashCode(outSwitch, outPorts[i]));
            if (dstPorts[i] == null) {
                System.out.println("!!!!!!!! WARNING - NOT INSTALLING RULE: " + rule + "!!!!!!!!!!!!!!");
                return;
            }
        }

        InstanceIdentifier<Table> tableId = getTableInstanceId(this.nodePath);
        InstanceIdentifier<Flow> flowId = getFlowInstanceId(tableId);

        Future<RpcResult<RemoveFlowOutput>> result;
        Match m = matchForRule(rule);
        Flow flow =
                FlowUtils.createToPortFlow(this.flowTableId, rule.priority, m, dstPorts)
                        .build();
        result = removeFlow(this.nodePath, tableId, flowId, flow);
    }

    private Future<RpcResult<AddFlowOutput>>
    addFlow(InstanceIdentifier<Node> nodeInstanceId,
            InstanceIdentifier<Table> tableInstanceId,
            InstanceIdentifier<Flow> flowPath,
            Flow flow) {
        AddFlowInputBuilder builder = new AddFlowInputBuilder(flow);
        builder.setNode(new NodeRef(nodeInstanceId));
        builder.setFlowRef(new FlowRef(flowPath));
        builder.setFlowTable(new FlowTableRef(tableInstanceId));
        builder.setTransactionUri(new Uri(flow.getId().getValue()));
        return fs.addFlow(builder.build());
    }

    private Future<RpcResult<RemoveFlowOutput>>
    removeFlow(InstanceIdentifier<Node> nodeInstanceId,
               InstanceIdentifier<Table> tableInstanceId,
               InstanceIdentifier<Flow> flowPath,
               Flow flow) {
        RemoveFlowInputBuilder builder = new RemoveFlowInputBuilder(flow);
        builder.setNode(new NodeRef(nodeInstanceId));
        builder.setFlowRef(new FlowRef(flowPath));
        builder.setFlowTable(new FlowTableRef(tableInstanceId));
        builder.setTransactionUri(new Uri(flow.getId().getValue()));
        return fs.removeFlow(builder.build());
    }

    private InstanceIdentifier<Table>
    getTableInstanceId(InstanceIdentifier<Node> nodeId) {
        // get flow table key
        TableKey flowTableKey = new TableKey(flowTableId);

        return nodeId.builder()
                .augmentation(FlowCapableNode.class)
                .child(Table.class, flowTableKey)
                .build();
    }

    private InstanceIdentifier<Flow>
    getFlowInstanceId(InstanceIdentifier<Table> tableId) {
        // generate unique flow key
        FlowId flowId = new FlowId(String.valueOf(flowIdInc.getAndIncrement()));
        FlowKey flowKey = new FlowKey(flowId);
        return tableId.child(Flow.class, flowKey);
    }

    private NodeConnectorId nodeConnectorId(String connectorId) {
        NodeKey nodeKey = nodePath.firstKeyOf(Node.class, NodeKey.class);
        StringBuilder stringId = new StringBuilder(nodeKey.getId().getValue()).append(":").append(connectorId);
        return new NodeConnectorId(stringId.toString());
    }

    private Match matchForRule(Rule rule) {
        MatchBuilder matchBuilder = new MatchBuilder();
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();
        MacAddress addr;
        for (TraceItem item : rule.match.fieldValues) {
            switch (item.field) {
                case IN_PORT:
                    matchBuilder.setInPort(nodeConnectorId(Long.toString(item.value)));
                    break;
                case ETH_SRC:
                    addr = PacketUtils.macValueToMac(item.value);
                    ethernetMatchBuilder.setEthernetSource(
                            new EthernetSourceBuilder()
                                    .setAddress(addr)
                                    .build());
                    break;
                case ETH_DST:
                    addr = PacketUtils.macValueToMac(item.value);
                    ethernetMatchBuilder.setEthernetDestination(
                            new EthernetDestinationBuilder()
                                    .setAddress(addr)
                                    .build());
                    break;
                case ETH_TYPE:
                    ethernetMatchBuilder.setEthernetType(
                            new EthernetTypeBuilder()
                                    .setType(new EtherType(item.value))
                                    .build());
                    break;
                default:
                    assert false;
                    break;
            }
        }
        matchBuilder.setEthernetMatch(ethernetMatchBuilder.build());
        Match m = matchBuilder.build();
        return m;
    }

    private void
    sendPacketOut(byte[] payload, NodeConnectorRef ingress, NodeConnectorRef egress) {
        InstanceIdentifier<Node> egressNodePath =
                InstanceIdentifierUtils.getNodePath(egress.getValue());
        TransmitPacketInput input = new TransmitPacketInputBuilder()
                .setPayload(payload)
                .setNode(new NodeRef(egressNodePath))
                .setEgress(egress)
                .setIngress(ingress)
                .build();
        pps.transmitPacket(input);
    }

    @Override
    public void sendPacket(byte[] data, int inSwitch, int inPort, int... ports) {
        LOG.info("inPort: [" + inPort + "];");
        for (int i = 0; i < ports.length; i++) {
            LOG.info("\toutPort" + i + ": [" + ports[i] + "];");
            NodeConnectorRef outPort = portPlaceHolder(switchPortHashCode(inSwitch, ports[i]));
            if (outPort == null)
              continue;
            sendPacketOut(data,
                    portPlaceHolder(switchPortHashCode(inSwitch, inPort)),
                    outPort);
        }
    }

    @Override
    public void installRules(LinkedList<Rule> rules, int... outSwitches) {
        for (Rule rule : rules) {
            Action a = rule.action;
            if (a instanceof ToPorts) {
                int[] outPorts = ((ToPorts) a).portIDs;
                for (int sw : outSwitches) {
                    installToPortRule(rule, sw, outPorts);
                }
            } else if (a instanceof Drop) {
                int[] outPorts = new int[0];
                for (int sw : outSwitches) {
                    installToPortRule(rule, sw, outPorts);
                }
            } else if (a instanceof Punt) {
                for (int sw : outSwitches) {
                    installPuntRule(rule, sw);
                }
            } else {
                throw new IllegalArgumentException("unknown rule type: " + rule);
            }
        }
    }

    @Override
    public void deleteRules(LinkedList<Rule> rules, int... outSwitches) {
        for (Rule rule : rules) {
            Action a = rule.action;
            if (a instanceof ToPorts) {
                int[] outPorts = ((ToPorts) a).portIDs;
                for (int sw : outSwitches) {
                    removeToPortRule(rule, sw, outPorts);
                }
            } else if (a instanceof Drop) {
                int[] outPorts = new int[0];
                for (int sw : outSwitches) {
                    removeToPortRule(rule, sw, outPorts);
                }
            } else if (a instanceof Punt) {
                for (int sw : outSwitches) {
                    removePuntRule(rule, sw);
                }
            } else {
                throw new IllegalArgumentException("unknown rule type: " + rule);
            }
        }
    }

    public synchronized Future<RpcResult<AddFlowOutput>>
    onSwitchAppeared(InstanceIdentifier<Table> appearedTablePath) {
        LOG.debug("expected table acquired, learning ..");
        tablePath = appearedTablePath;
        nodePath = tablePath.firstIdentifierOf(Node.class);

        return null;
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        Short requiredTableId = 0;

        Map<InstanceIdentifier<?>, DataObject> updated = change.getUpdatedData();
        for (Map.Entry<InstanceIdentifier<?>, DataObject> updateItem : updated.entrySet()) {
            DataObject table = updateItem.getValue();
            if (table instanceof Table) {
                Table tableSure = (Table) table;
                LOG.trace("table: {}", table);

                if (requiredTableId.equals(tableSure.getId())) {
                    @SuppressWarnings("unchecked")
                    InstanceIdentifier<Table> tablePath =
                            (InstanceIdentifier<Table>) updateItem.getKey();
                    onSwitchAppeared(tablePath);
                }
            }
        }
    }

    @Override
    public void onPacketReceived(PacketReceived packet) {
        if (packet == null || packet.getPayload() ==  null)
            return;

        byte[] data = packet.getPayload();

        LOG.debug("Received packet via match: {}", packet.getMatch());

        NodeConnectorRef ingress = packet.getIngress();

        if (ingress == null)
            return;

        String portID = ingress
                .getValue()
                .firstIdentifierOf(NodeConnector.class)
                .firstKeyOf(NodeConnector.class, NodeConnectorKey.class)
                .getId()
                .getValue();

        if (portID.contains(LOCAL_PORT_STR))
            return;

        int switchNum = portIDToSwitchNum(portID);
        int portNum = portIDToPortNum(portID);

        synchronized(this) {
            this.maple.handlePacket(data, switchNum, portNum);
        }
    }

    @Override
    public void onNodeConnectorRemoved(NodeConnectorRemoved notification) {
        NodeConnectorRef ncr = notification.getNodeConnectorRef();
        String portID = ncr
                .getValue()
                .firstIdentifierOf(NodeConnector.class)
                .firstKeyOf(NodeConnector.class, NodeConnectorKey.class)
                .getId()
                .getValue();

        LOG.info("[Down] PortID = " + portID);

        if (portID.contains(LOCAL_PORT_STR))
            return;

        int switchNum = portIDToSwitchNum(portID);
        int portNum = portIDToPortNum(portID);
        this.portToNodeConnectorRef.remove(switchPortHashCode(switchNum, portNum));
        this.maple.portDown(switchNum, portNum);

        LOG.info("[Down] NodeConnectorRef " + notification.getNodeConnectorRef());
    }

    @Override
    public void onNodeConnectorUpdated(NodeConnectorUpdated notification) {
        NodeConnectorRef ncr = notification.getNodeConnectorRef();
        String portID = ncr
                .getValue()
                .firstIdentifierOf(NodeConnector.class)
                .firstKeyOf(NodeConnector.class, NodeConnectorKey.class)
                .getId()
                .getValue();

        LOG.info("[Up] PortID = " + portID);

        if (portID.contains(LOCAL_PORT_STR))
            return;

        int switchNum = portIDToSwitchNum(portID);
        int portNum = portIDToPortNum(portID);
        this.portToNodeConnectorRef.put(switchPortHashCode(switchNum, portNum), ncr);
        this.maple.portUp(switchNum, portNum);

        LOG.info("[Up] NodeConnectorRef " + notification.getNodeConnectorRef());
    }

    @Override
    public void onNodeRemoved(NodeRemoved notification) {
        LOG.info("[Down] NodeRef " + notification.getNodeRef());
    }

    @Override
    public void onNodeUpdated(NodeUpdated notification) {
        LOG.info("[Up] NodeRef " + notification.getNodeRef());
    }
}
