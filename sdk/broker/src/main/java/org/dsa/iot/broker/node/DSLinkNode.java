package org.dsa.iot.broker.node;

import org.dsa.iot.broker.processor.MessageProcessor;
import org.dsa.iot.broker.processor.Responder;
import org.dsa.iot.broker.processor.stream.GenericStream;
import org.dsa.iot.broker.processor.stream.SubStream;
import org.dsa.iot.broker.processor.stream.manager.StreamManager;
import org.dsa.iot.broker.server.client.Client;
import org.dsa.iot.broker.utils.ParsedPath;
import org.dsa.iot.dslink.util.TimeUtils;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Samuel Grenier
 */
public class DSLinkNode extends BrokerNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(DSLinkNode.class);
    public static final String PROFILE = "dslink";

    // The reserved dsId that can claim this node
    private String dsId;
    private Client client;

    private final MessageProcessor processor;
    private String disconnected;
    private JsonObject linkData;

    public DSLinkNode(Downstream parent, String name) {
        this(parent, name, new MessageProcessor());
    }

    public DSLinkNode(Downstream parent,
                      String name,
                      MessageProcessor processor) {
        super(parent, name, PROFILE);
        if (processor == null) {
            throw new NullPointerException("processor");
        }
        this.processor = processor;
    }

    @Override
    public void connected(Client client) {
        String cDsId = client.handshake().dsId();
        if (!(this.dsId == null || this.dsId.equals(cDsId))) {
            throw new IllegalStateException("Expected different dsId");
        }
        LOGGER.info("Client `{}` has connected", cDsId);
        this.dsId = cDsId;
        this.client = client;
        this.disconnected = null;
        processor.initialize(this);
        if (client.handshake().isResponder()) {
            this.linkData = client.handshake().linkData();
            accessible(true);
        } else {
            this.linkData = null;
            accessible(false);
        }
    }

    @Override
    public void disconnected(Client client) {
        if (client != this.client) {
            return;
        }
        this.disconnected = TimeUtils.format(System.currentTimeMillis());
        this.client = null;
        processor.disconnected(client);
        LOGGER.info("Client `{}` has disconnected", client.handshake().dsId());
    }

    public String dsId() {
        return dsId;
    }

    public Client client() {
        return client;
    }

    public MessageProcessor processor() {
        return processor;
    }

    public JsonObject linkData() {
        return linkData;
    }

    public String disconnected() {
        return disconnected;
    }

    @Override
    public SubStream subscribe(ParsedPath path, Client requester, int sid) {
        if (path.isRemote()) {
            Responder responder = processor().responder();
            return responder.stream().sub().subscribe(path, requester, sid);
        }
        return null;
    }

    @Override
    public void unsubscribe(SubStream stream, Client requester) {
        MessageProcessor processor = processor();
        Responder responder = processor.responder();
        StreamManager sm = responder.stream();
        sm.sub().unsubscribe(stream, requester);
    }

    @Override
    public GenericStream invoke(ParsedPath path,
                                Client requester,
                                int rid,
                                JsonObject params,
                                String permit) {
        Responder responder = processor().responder();
        GenericStream stream = new GenericStream(responder, path);
        stream.add(requester, rid);
        int responderRid = responder.nextRid();
        responder.stream().addIfNull(responderRid, stream);

        JsonObject req = new JsonObject();
        req.put("rid", responderRid);
        req.put("path", path.base());
        req.put("method", "invoke");
        if (params != null) {
            req.put("params", params);
        }
        if (permit != null) {
            req.put("permit", permit);
        }

        JsonArray reqs = new JsonArray();
        reqs.add(req);
        client().writeRequest(reqs);
        return stream;
    }

    @Override
    public GenericStream set(ParsedPath path,
                    Client requester,
                    int rid,
                    Object value,
                    String permit) {
        Responder responder = processor().responder();
        GenericStream stream = new GenericStream(responder, path);
        stream.add(requester, rid);
        int responderRid = responder.nextRid();
        responder.stream().addIfNull(responderRid, stream);

        JsonObject req = new JsonObject();
        req.put("rid", responderRid);
        req.put("path", path.base());
        req.put("method", "set");
        req.put("value", value);
        if (permit != null) {
            req.put("permit", permit);
        }

        JsonArray reqs = new JsonArray();
        reqs.add(req);
        client().writeRequest(reqs);
        return stream;
    }

    @Override
    public GenericStream remove(ParsedPath path,
                             Client requester,
                             int rid,
                             String permit) {
        Responder responder = processor().responder();
        GenericStream stream = new GenericStream(responder, path);
        stream.add(requester, rid);
        int responderRid = responder.nextRid();
        responder.stream().addIfNull(responderRid, stream);

        JsonObject req = new JsonObject();
        req.put("rid", responderRid);
        req.put("path", path.base());
        req.put("method", "remove");
        if (permit != null) {
            req.put("permit", permit);
        }

        JsonArray reqs = new JsonArray();
        reqs.add(req);
        client().writeRequest(reqs);
        return stream;
    }

    @Override
    public JsonObject list(ParsedPath path, Client requester, int rid) {
        if (path.isRemote()) {
            Responder responder = processor().responder();
            responder.stream().list().add(path, requester, rid);
        }
        return null;
    }

    @Override
    protected void populateUpdates(JsonArray updates) {
        super.populateUpdates(updates);
        String disconnected = disconnected();
        if (disconnected != null) {
            JsonArray update = new JsonArray();
            update.add("disconnectedTs");
            update.add(disconnected);
            updates.add(update);
        }
    }

    @Override
    protected JsonObject getChildUpdate() {
        JsonObject tmp = super.getChildUpdate();
        JsonObject linkData = linkData();
        if (linkData != null) {
            tmp.put("linkData", linkData);
        }
        return tmp;
    }

    public JsonArray linkDataUpdate() {
        JsonObject linkData = this.linkData;
        if (linkData == null) {
            return null;
        }
        JsonArray update = new JsonArray();
        update.add("$linkData");
        update.add(linkData);
        return update;
    }
}
