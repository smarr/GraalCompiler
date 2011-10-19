/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.sun.hotspot.igv.data.serialization;

import com.sun.hotspot.igv.data.GraphDocument;
import com.sun.hotspot.igv.data.Group;
import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputEdge;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputMethod;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.Pair;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.services.GroupCallback;
import com.sun.hotspot.igv.data.serialization.XMLParser.ElementHandler;
import com.sun.hotspot.igv.data.serialization.XMLParser.HandoverElementHandler;
import com.sun.hotspot.igv.data.serialization.XMLParser.ParseMonitor;
import com.sun.hotspot.igv.data.serialization.XMLParser.TopElementHandler;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Parser {

    public static final String INDENT = "  ";
    public static final String TOP_ELEMENT = "graphDocument";
    public static final String GROUP_ELEMENT = "group";
    public static final String GRAPH_ELEMENT = "graph";
    public static final String ROOT_ELEMENT = "graphDocument";
    public static final String PROPERTIES_ELEMENT = "properties";
    public static final String EDGES_ELEMENT = "edges";
    public static final String PROPERTY_ELEMENT = "p";
    public static final String EDGE_ELEMENT = "edge";
    public static final String NODE_ELEMENT = "node";
    public static final String NODES_ELEMENT = "nodes";
    public static final String REMOVE_EDGE_ELEMENT = "removeEdge";
    public static final String REMOVE_NODE_ELEMENT = "removeNode";
    public static final String METHOD_NAME_PROPERTY = "name";
    public static final String GROUP_NAME_PROPERTY = "name";
    public static final String METHOD_IS_PUBLIC_PROPERTY = "public";
    public static final String METHOD_IS_STATIC_PROPERTY = "static";
    public static final String TRUE_VALUE = "true";
    public static final String NODE_NAME_PROPERTY = "name";
    public static final String EDGE_NAME_PROPERTY = "name";
    public static final String NODE_ID_PROPERTY = "id";
    public static final String FROM_PROPERTY = "from";
    public static final String TO_PROPERTY = "to";
    public static final String PROPERTY_NAME_PROPERTY = "name";
    public static final String GRAPH_NAME_PROPERTY = "name";
    public static final String FROM_INDEX_PROPERTY = "fromIndex";
    public static final String TO_INDEX_PROPERTY = "toIndex";
    public static final String TO_INDEX_ALT_PROPERTY = "index";
    public static final String METHOD_ELEMENT = "method";
    public static final String INLINE_ELEMENT = "inline";
    public static final String BYTECODES_ELEMENT = "bytecodes";
    public static final String METHOD_BCI_PROPERTY = "bci";
    public static final String METHOD_SHORT_NAME_PROPERTY = "shortName";
    public static final String CONTROL_FLOW_ELEMENT = "controlFlow";
    public static final String BLOCK_NAME_PROPERTY = "name";
    public static final String BLOCK_ELEMENT = "block";
    public static final String SUCCESSORS_ELEMENT = "successors";
    public static final String SUCCESSOR_ELEMENT = "successor";
    public static final String ASSEMBLY_ELEMENT = "assembly";
    public static final String DIFFERENCE_PROPERTY = "difference";
    private TopElementHandler<GraphDocument> xmlDocument = new TopElementHandler<GraphDocument>();
    private boolean difference;
    private GroupCallback groupCallback;
    private HashMap<String, Integer> idCache = new HashMap<String, Integer>();
    private ArrayList<Pair<String, String>> blockConnections = new ArrayList<Pair<String, String>>();
    private int maxId = 0;

    private int lookupID(String i) {
        try {
            return Integer.parseInt(i);
        } catch (NumberFormatException nfe) {
            // ignore
        }
        Integer id = idCache.get(i);
        if (id == null) {
            id = maxId++;
            idCache.put(i, id);
        }
        return id.intValue();
    }

    // <graphDocument>
    private ElementHandler<GraphDocument, Object> topHandler = new ElementHandler<GraphDocument, Object>(TOP_ELEMENT) {

        @Override
        protected GraphDocument start() throws SAXException {
            return new GraphDocument();
        }
    };
    // <group>
    private ElementHandler<Group, GraphDocument> groupHandler = new XMLParser.ElementHandler<Group, GraphDocument>(GROUP_ELEMENT) {

        @Override
        protected Group start() throws SAXException {
            Group group = new Group();
            group.setComplete(false);
            
            String differenceProperty = this.readAttribute(DIFFERENCE_PROPERTY);
            Parser.this.difference = (differenceProperty != null && (differenceProperty.equals("1") || differenceProperty.equals("true")));

            ParseMonitor monitor = getMonitor();
            if (monitor != null) {
                monitor.setState(group.getName());
            }

            return group;
        }

        @Override
        protected void end(String text) throws SAXException {
            Group group = getObject();
            group.setComplete(true);
            if (groupCallback == null) {
                getParentObject().addGroup(group);
            }
        }
    };
    private HandoverElementHandler<Group> assemblyHandler = new XMLParser.HandoverElementHandler<Group>(ASSEMBLY_ELEMENT, true) {

        @Override
        protected void end(String text) throws SAXException {
            getParentObject().setAssembly(text);
        }
    };
    // <method>
    private ElementHandler<InputMethod, Group> methodHandler = new XMLParser.ElementHandler<InputMethod, Group>(METHOD_ELEMENT) {

        @Override
        protected InputMethod start() throws SAXException {

            InputMethod method = parseMethod(this, getParentObject());
            getParentObject().setMethod(method);
            return method;
        }
    };

    private InputMethod parseMethod(XMLParser.ElementHandler handler, Group group) throws SAXException {
        String s = handler.readRequiredAttribute(METHOD_BCI_PROPERTY);
        int bci = 0;
        try {
            bci = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new SAXException(e);
        }
        InputMethod method = new InputMethod(group, handler.readRequiredAttribute(METHOD_NAME_PROPERTY), handler.readRequiredAttribute(METHOD_SHORT_NAME_PROPERTY), bci);
        return method;
    }
    // <bytecodes>
    private HandoverElementHandler<InputMethod> bytecodesHandler = new XMLParser.HandoverElementHandler<InputMethod>(BYTECODES_ELEMENT, true) {

        @Override
        protected void end(String text) throws SAXException {
            getParentObject().setBytecodes(text);
        }
    };
    // <inlined>
    private HandoverElementHandler<InputMethod> inlinedHandler = new XMLParser.HandoverElementHandler<InputMethod>(INLINE_ELEMENT);
    // <inlined><method>
    private ElementHandler<InputMethod, InputMethod> inlinedMethodHandler = new XMLParser.ElementHandler<InputMethod, InputMethod>(METHOD_ELEMENT) {

        @Override
        protected InputMethod start() throws SAXException {
            InputMethod method = parseMethod(this, getParentObject().getGroup());
            getParentObject().addInlined(method);
            return method;
        }
    };
    // <graph>
    private ElementHandler<InputGraph, Group> graphHandler = new XMLParser.ElementHandler<InputGraph, Group>(GRAPH_ELEMENT) {

        private InputGraph graph;

        @Override
        protected InputGraph start() throws SAXException {
            String name = readAttribute(GRAPH_NAME_PROPERTY);
            InputGraph curGraph = InputGraph.createWithoutGroup(name, null);
            if (difference) {
                List<InputGraph> list = getParentObject().getGraphs();
                if (list.size() > 0) {
                    InputGraph previous = list.get(list.size() - 1);
                    for (InputNode n : previous.getNodes()) {
                        curGraph.addNode(n);
                    }
                    for (InputEdge e : previous.getEdges()) {
                        curGraph.addEdge(e);
                    }
                }
            }
            this.graph = curGraph;
            return curGraph;
        }

        @Override
        protected void end(String text) throws SAXException {
            // NOTE: Some graphs intentionally don't provide blocks. Instead
            //       they later generate the blocks from other information such
            //       as node properties (example: ServerCompilerScheduler).
            //       Thus, we shouldn't assign nodes that don't belong to any
            //       block to some artificial block below unless blocks are
            //       defined and nodes are assigned to them.

            if (graph.getBlocks().size() > 0) {
                boolean blocksContainNodes = false;
                for (InputBlock b : graph.getBlocks()) {
                    if (b.getNodes().size() > 0) {
                        blocksContainNodes = true;
                        break;
                    }
                }

                if (!blocksContainNodes) {
                    graph.clearBlocks();
                    blockConnections.clear();
                } else {
                    // Blocks and their nodes defined: add other nodes to an
                    //  artificial "no block" block
                    InputBlock noBlock = null;
                    for (InputNode n : graph.getNodes()) {
                        if (graph.getBlock(n) == null) {
                            if (noBlock == null) {
                                noBlock = graph.addBlock("(no block)");
                            }

                            noBlock.addNode(n.getId());
                        }

                        assert graph.getBlock(n) != null;
                    }
                }
            }

            // Resolve block successors
            for (Pair<String, String> p : blockConnections) {
                final InputBlock left = graph.getBlock(p.getLeft());
                assert left != null;
                final InputBlock right = graph.getBlock(p.getRight());
                assert right != null;
                graph.addBlockEdge(left, right);
            }
            blockConnections.clear();
            
            // Add to group
            getParentObject().addGraph(graph);
        }
    };
    // <nodes>
    private HandoverElementHandler<InputGraph> nodesHandler = new HandoverElementHandler<InputGraph>(NODES_ELEMENT);
    // <controlFlow>
    private HandoverElementHandler<InputGraph> controlFlowHandler = new HandoverElementHandler<InputGraph>(CONTROL_FLOW_ELEMENT);
    // <block>
    private ElementHandler<InputBlock, InputGraph> blockHandler = new ElementHandler<InputBlock, InputGraph>(BLOCK_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            InputGraph graph = getParentObject();
            String name = readRequiredAttribute(BLOCK_NAME_PROPERTY).intern();
            InputBlock b = graph.addBlock(name);
            for (InputNode n : b.getNodes()) {
                assert graph.getBlock(n).equals(b);
            }
            return b;
        }
    };
    // <nodes>
    private HandoverElementHandler<InputBlock> blockNodesHandler = new HandoverElementHandler<InputBlock>(NODES_ELEMENT);
    // <node>
    private ElementHandler<InputBlock, InputBlock> blockNodeHandler = new ElementHandler<InputBlock, InputBlock>(NODE_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);

            int id = 0;
            try {
                id = lookupID(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            getParentObject().addNode(id);
            return getParentObject();
        }
    };
    // <successors>
    private HandoverElementHandler<InputBlock> successorsHandler = new HandoverElementHandler<InputBlock>(SUCCESSORS_ELEMENT);
    // <successor>
    private ElementHandler<InputBlock, InputBlock> successorHandler = new ElementHandler<InputBlock, InputBlock>(SUCCESSOR_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            String name = readRequiredAttribute(BLOCK_NAME_PROPERTY);
            blockConnections.add(new Pair<String, String>(getParentObject().getName(), name));
            return getParentObject();
        }
    };
    // <node>
    private ElementHandler<InputNode, InputGraph> nodeHandler = new ElementHandler<InputNode, InputGraph>(NODE_ELEMENT) {

        @Override
        protected InputNode start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);
            int id = 0;
            try {
                id = lookupID(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            InputNode node = new InputNode(id);
            getParentObject().addNode(node);
            return node;
        }
    };
    // <removeNode>
    private ElementHandler<InputNode, InputGraph> removeNodeHandler = new ElementHandler<InputNode, InputGraph>(REMOVE_NODE_ELEMENT) {

        @Override
        protected InputNode start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);
            int id = 0;
            try {
                id = lookupID(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            return getParentObject().removeNode(id);
        }
    };
    // <graph>
    private HandoverElementHandler<InputGraph> edgesHandler = new HandoverElementHandler<InputGraph>(EDGES_ELEMENT);

    // Local class for edge elements
    private class EdgeElementHandler extends ElementHandler<InputEdge, InputGraph> {

        public EdgeElementHandler(String name) {
            super(name);
        }

        @Override
        protected InputEdge start() throws SAXException {
            int fromIndex = 0;
            int toIndex = 0;
            int from = -1;
            int to = -1;

            try {
                String fromIndexString = readAttribute(FROM_INDEX_PROPERTY);
                if (fromIndexString != null) {
                    fromIndex = Integer.parseInt(fromIndexString);
                }
                
                String toIndexString = readAttribute(TO_INDEX_PROPERTY);
                if (toIndexString == null) {
                    toIndexString = readAttribute(TO_INDEX_ALT_PROPERTY);
                }
                if (toIndexString != null) {
                    toIndex = Integer.parseInt(toIndexString);
                }

                from = lookupID(readRequiredAttribute(FROM_PROPERTY));
                to = lookupID(readRequiredAttribute(TO_PROPERTY));
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }

            InputEdge conn = new InputEdge((char) fromIndex, (char) toIndex, from, to);
            return start(conn);
        }

        protected InputEdge start(InputEdge conn) throws SAXException {
            return conn;
        }
    }
    // <edge>
    private EdgeElementHandler edgeHandler = new EdgeElementHandler(EDGE_ELEMENT) {

        @Override
        protected InputEdge start(InputEdge conn) throws SAXException {
            getParentObject().addEdge(conn);
            return conn;
        }
    };
    // <removeEdge>
    private EdgeElementHandler removeEdgeHandler = new EdgeElementHandler(REMOVE_EDGE_ELEMENT) {

        @Override
        protected InputEdge start(InputEdge conn) throws SAXException {
            getParentObject().removeEdge(conn);
            return conn;
        }
    };
    // <properties>
    private HandoverElementHandler<Properties.Provider> propertiesHandler = new HandoverElementHandler<Properties.Provider>(PROPERTIES_ELEMENT);
    // <properties>
    private HandoverElementHandler<Group> groupPropertiesHandler = new HandoverElementHandler<Group>(PROPERTIES_ELEMENT) {

        @Override
        public void end(String text) throws SAXException {
            if (groupCallback != null) {
                groupCallback.started(getParentObject());
            }
        }
    };
    // <property>
    private ElementHandler<String, Properties.Provider> propertyHandler = new XMLParser.ElementHandler<String, Properties.Provider>(PROPERTY_ELEMENT, true) {

        @Override
        public String start() throws SAXException {
            return readRequiredAttribute(PROPERTY_NAME_PROPERTY).intern();
         }

        @Override
        public void end(String text) {
            getParentObject().getProperties().setProperty(getObject(), text.trim().intern());
        }
    };

    public Parser() {
        this(null);
    }

    public Parser(GroupCallback groupCallback) {

        this.groupCallback = groupCallback;

        // Initialize dependencies
        xmlDocument.addChild(topHandler);
        topHandler.addChild(groupHandler);

        groupHandler.addChild(methodHandler);
        groupHandler.addChild(assemblyHandler);
        groupHandler.addChild(graphHandler);

        methodHandler.addChild(inlinedHandler);
        methodHandler.addChild(bytecodesHandler);

        inlinedHandler.addChild(inlinedMethodHandler);
        inlinedMethodHandler.addChild(bytecodesHandler);
        inlinedMethodHandler.addChild(inlinedHandler);

        graphHandler.addChild(nodesHandler);
        graphHandler.addChild(edgesHandler);
        graphHandler.addChild(controlFlowHandler);

        controlFlowHandler.addChild(blockHandler);

        blockHandler.addChild(successorsHandler);
        successorsHandler.addChild(successorHandler);
        blockHandler.addChild(blockNodesHandler);
        blockNodesHandler.addChild(blockNodeHandler);

        nodesHandler.addChild(nodeHandler);
        nodesHandler.addChild(removeNodeHandler);
        edgesHandler.addChild(edgeHandler);
        edgesHandler.addChild(removeEdgeHandler);

        methodHandler.addChild(propertiesHandler);
        inlinedMethodHandler.addChild(propertiesHandler);
        topHandler.addChild(propertiesHandler);
        groupHandler.addChild(groupPropertiesHandler);
        graphHandler.addChild(propertiesHandler);
        nodeHandler.addChild(propertiesHandler);
        propertiesHandler.addChild(propertyHandler);
        groupPropertiesHandler.addChild(propertyHandler);
    }

    // Returns a new GraphDocument object deserialized from an XML input source.
    public synchronized GraphDocument parse(InputSource source, XMLParser.ParseMonitor monitor) throws SAXException {
        XMLReader reader = createReader();

        reader.setContentHandler(new XMLParser(xmlDocument, monitor));
        try {
            reader.parse(source);
        } catch (IOException ex) {
            throw new SAXException(ex);
        }

        return topHandler.getObject();
    }

    private XMLReader createReader() throws SAXException {
        try {
            SAXParserFactory pfactory = SAXParserFactory.newInstance();
            pfactory.setValidating(false);
            pfactory.setNamespaceAware(true);

            // Enable schema validation
            SchemaFactory sfactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
            InputStream stream = Parser.class.getResourceAsStream("graphdocument.xsd");
            pfactory.setSchema(sfactory.newSchema(new Source[]{new StreamSource(stream)}));

            return pfactory.newSAXParser().getXMLReader();
        } catch (ParserConfigurationException ex) {
            throw new SAXException(ex);
        }
    }
}
