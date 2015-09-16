/*
 * Copyright (c) 2015 Cisco System, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.maple.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.NotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ODLMapleProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ODLMapleProvider.class);

    private ODLControllerAdapter controller;

    private ListenerRegistration<NotificationListener> notificationListenerReg;
    private ListenerRegistration<DataChangeListener> switchChangeListenerReg;
    private ListenerRegistration<DataChangeListener> linkChangeListenerReg;

    private static final String TOPOLOGY_NAME = "flow:1";

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("ODLMapleProvider Session Initiating");

        PacketProcessingService pps = session.getRpcService(PacketProcessingService.class);
        SalFlowService fs = session.getRpcService(SalFlowService.class);
        this.controller = new ODLControllerAdapter(pps, fs);

        DataBroker db = session.getSALService(DataBroker.class);

        NotificationProviderService nps = session.getSALService(NotificationProviderService.class);
        this.notificationListenerReg = nps.registerNotificationListener(this.controller);

        this.switchChangeListenerReg = db.registerDataChangeListener(
                LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.builder(Nodes.class)
                        .child(Node.class)
                        .augmentation(FlowCapableNode.class)
                        .child(Table.class)
                        .build(),
                this.controller,
                AsyncDataBroker.DataChangeScope.SUBTREE);

        this.linkChangeListenerReg = db.registerDataChangeListener(
                LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.builder(NetworkTopology.class)
                        .child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_NAME)))
                        .child(Link.class)
                        .build(),
                this.controller,
                AsyncDataBroker.DataChangeScope.BASE);

        this.controller.start();
        LOG.info("ODLMapleProvider Session Initiated");
    }

    @Override
    public void close() throws Exception {
        LOG.info("ODLMapleProvider Closing");

        try {
            this.notificationListenerReg.close();
            this.switchChangeListenerReg.close();
            this.linkChangeListenerReg.close();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }

        if (this.controller != null) {
            this.controller.stop();
        }

        LOG.info("ODLMapleProvider Closed");
    }

}
