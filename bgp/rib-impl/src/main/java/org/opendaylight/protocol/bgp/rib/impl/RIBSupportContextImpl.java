/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.protocol.bgp.rib.impl;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import java.util.Map.Entry;
import java.util.Set;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.protocol.bgp.rib.impl.spi.RIBSupportContext;
import org.opendaylight.protocol.bgp.rib.spi.RIBSupport;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.ClusterId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.OriginatorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.PathAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.Update;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.path.attributes.Aggregator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.path.attributes.AsPath;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.path.attributes.Communities;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.path.attributes.ExtendedCommunities;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.path.attributes.LocalPref;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.path.attributes.MultiExitDisc;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.path.attributes.Origin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev130919.PathAttributes1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev130919.PathAttributes2;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev130919.update.path.attributes.MpReachNlri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev130919.update.path.attributes.MpUnreachNlri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev130925.BgpRib;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev130925.Route;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev130925.bgp.rib.Rib;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev130925.bgp.rib.rib.LocRib;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev130925.rib.Tables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev130925.rib.tables.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev130925.route.Attributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev130925.route.AttributesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.types.rev130919.BgpAggregator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.types.rev130919.Community;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.types.rev130919.ExtendedCommunity;
import org.opendaylight.yangtools.binding.data.codec.api.BindingCodecTree;
import org.opendaylight.yangtools.binding.data.codec.api.BindingCodecTreeNode;
import org.opendaylight.yangtools.binding.data.codec.api.BindingNormalizedNodeCachingCodec;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RIBSupportContextImpl extends RIBSupportContext {

    private static final Logger LOG = LoggerFactory.getLogger(RIBSupportContextImpl.class);
    private static final ContainerNode EMPTY_TABLE_ATTRIBUTES = ImmutableNodes.containerNode(org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev130925.rib.tables.Attributes.QNAME);
    private static final Set<Class<? extends DataObject>> ATTRIBUTE_CACHEABLES;

    private static final InstanceIdentifier<Tables> TABLE_BASE_II = InstanceIdentifier.builder(BgpRib.class)
            .child(Rib.class)
            .child(LocRib.class)
            .child(Tables.class)
            .build();
    private static final InstanceIdentifier<MpReachNlri> MP_REACH_NLRI_II = InstanceIdentifier.create(Update.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.update.PathAttributes.class)
                .augmentation(PathAttributes1.class)
                .child(MpReachNlri.class);
    private static final InstanceIdentifier<MpUnreachNlri> MP_UNREACH_NLRI_II = InstanceIdentifier.create(Update.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev130919.update.PathAttributes.class)
            .augmentation(PathAttributes2.class)
            .child(MpUnreachNlri.class);


    static {
        final Builder<Class<? extends DataObject>> acb = ImmutableSet.builder();
        acb.add(Aggregator.class);
        acb.add(BgpAggregator.class);
        acb.add(AsPath.class);
        acb.add(ClusterId.class);
        acb.add(Community.class);
        acb.add(Communities.class);
        acb.add(ExtendedCommunity.class);
        acb.add(ExtendedCommunities.class);
        acb.add(LocalPref.class);
        acb.add(MultiExitDisc.class);
        acb.add(Origin.class);
        acb.add(OriginatorId.class);
        ATTRIBUTE_CACHEABLES = acb.build();
    }

    private final RIBSupport tableSupport;
    private final ImmutableSet<Class<? extends DataObject>> cacheableAttributes;
    private BindingNormalizedNodeCachingCodec<Attributes> attributesCodec;
    private BindingNormalizedNodeCachingCodec<MpReachNlri> reachNlriCodec;
    private BindingNormalizedNodeCachingCodec<MpUnreachNlri> unreachNlriCodec;


    public RIBSupportContextImpl(final RIBSupport ribSupport) {
        this.tableSupport = Preconditions.checkNotNull(ribSupport);
        final Builder<Class<? extends DataObject>> acb = ImmutableSet.builder();
        acb.addAll(ATTRIBUTE_CACHEABLES);
        acb.addAll(this.tableSupport.cacheableAttributeObjects());
        this.cacheableAttributes = acb.build();

    }

    @SuppressWarnings("unchecked")
    void onCodecTreeUpdated(final BindingCodecTree tree) {

        @SuppressWarnings("rawtypes")
        final
        BindingCodecTreeNode tableCodecContext = tree.getSubtreeCodec(TABLE_BASE_II);
        final BindingCodecTreeNode<? extends Route> routeListCodec = tableCodecContext
            .streamChild(Routes.class)
            .streamChild(this.tableSupport.routesCaseClass())
            .streamChild(this.tableSupport.routesContainerClass())
            .streamChild(this.tableSupport.routesListClass());

        this.attributesCodec = routeListCodec.streamChild(Attributes.class).createCachingCodec(this.cacheableAttributes);
        this.reachNlriCodec = tree.getSubtreeCodec(MP_REACH_NLRI_II).createCachingCodec(tableSupport.cacheableNlriObjects());
        this.unreachNlriCodec = tree.getSubtreeCodec(MP_UNREACH_NLRI_II).createCachingCodec(tableSupport.cacheableNlriObjects());
    }

    @Override
    public void writeRoutes(final DOMDataWriteTransaction tx, final YangInstanceIdentifier tableId, final MpReachNlri nlri,
            final PathAttributes attributes) {
        final ContainerNode domNlri = serialiazeReachNlri(nlri);
        final ContainerNode routeAttributes = serializeAttributes(attributes);
        this.tableSupport.putRoutes(tx, tableId, domNlri, routeAttributes);
    }

    @Override
    public void clearTable(final DOMDataWriteTransaction tx, final YangInstanceIdentifier tableId) {
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> tb = ImmutableNodes.mapEntryBuilder();
        tb.withNodeIdentifier((NodeIdentifierWithPredicates)tableId.getLastPathArgument());
        tb.withChild(EMPTY_TABLE_ATTRIBUTES);

        // tableId is keyed, but that fact is not directly visible from YangInstanceIdentifier, see BUG-2796
        final NodeIdentifierWithPredicates tableKey = (NodeIdentifierWithPredicates) tableId.getLastPathArgument();
        for (final Entry<QName, Object> e : tableKey.getKeyValues().entrySet()) {
            tb.withChild(ImmutableNodes.leafNode(e.getKey(), e.getValue()));
        }

        final ChoiceNode routes = this.tableSupport.emptyRoutes();
        Verify.verifyNotNull(routes, "Null empty routes in %s", this.tableSupport);
        Verify.verify(Routes.QNAME.equals(routes.getNodeType()), "Empty routes have unexpected identifier %s, expected %s", routes.getNodeType(), Routes.QNAME);

        tx.put(LogicalDatastoreType.OPERATIONAL, tableId, tb.withChild(routes).build());
    }

    @Override
    public void deleteRoutes(final DOMDataWriteTransaction tx, final YangInstanceIdentifier tableId, final MpUnreachNlri nlri) {
        this.tableSupport.deleteRoutes(tx, tableId, serialiazeUnreachNlri(nlri));
    }

    private ContainerNode serialiazeUnreachNlri(final MpUnreachNlri nlri) {
        Preconditions.checkState(unreachNlriCodec != null, "MpReachNlri codec not available");
        return (ContainerNode) unreachNlriCodec.serialize(nlri);
    }

    private ContainerNode serialiazeReachNlri(final MpReachNlri nlri) {
        Preconditions.checkState(reachNlriCodec != null, "MpReachNlri codec not available");
        return (ContainerNode) reachNlriCodec.serialize(nlri);
    }

    private ContainerNode serializeAttributes(final PathAttributes pathAttr) {
        Preconditions.checkState(attributesCodec != null, "MpReachNlri codec not available");
        final Attributes attr = new AttributesBuilder(pathAttr).build();
        return (ContainerNode) this.attributesCodec.serialize(attr);
    }

    @Override
    public RIBSupport getRibSupport() {
        return this.tableSupport;
    }
}