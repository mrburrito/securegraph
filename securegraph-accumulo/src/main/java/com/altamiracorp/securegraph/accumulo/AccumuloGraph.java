package com.altamiracorp.securegraph.accumulo;

import com.altamiracorp.securegraph.*;
import com.altamiracorp.securegraph.accumulo.iterator.ElementVisibilityRowFilter;
import com.altamiracorp.securegraph.accumulo.search.SearchIndex;
import com.altamiracorp.securegraph.accumulo.serializer.ValueSerializer;
import com.altamiracorp.securegraph.id.IdGenerator;
import com.altamiracorp.securegraph.util.LookAheadIterable;
import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AccumuloGraph extends GraphBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccumuloGraph.class);

    private static final Text EMPTY_TEXT = new Text("");
    private static final Value EMPTY_VALUE = new Value(new byte[0]);
    private final Connector connector;
    private final ValueSerializer valueSerializer;
    private final SearchIndex searchIndex;
    private BatchWriter writer;
    private final Object writerLock = new Object();

    protected AccumuloGraph(AccumuloGraphConfiguration config, IdGenerator idGenerator, Connector connector, ValueSerializer valueSerializer, SearchIndex searchIndex) {
        super(config, idGenerator);
        this.connector = connector;
        this.valueSerializer = valueSerializer;
        this.searchIndex = searchIndex;
    }

    public static AccumuloGraph create(AccumuloGraphConfiguration config) throws AccumuloSecurityException, AccumuloException, SecureGraphException {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        Connector connector = config.createConnector();
        ValueSerializer valueSerializer = config.createValueSerializer();
        SearchIndex searchIndex = config.createSearchIndex();
        IdGenerator idGenerator = config.createIdGenerator();
        return new AccumuloGraph(config, idGenerator, connector, valueSerializer, searchIndex);
    }

    public static AccumuloGraph create(Map config) throws AccumuloSecurityException, AccumuloException, SecureGraphException {
        return create(new AccumuloGraphConfiguration(config));
    }

    @Override
    public Vertex addVertex(Object vertexId, Visibility vertexVisibility, Property... properties) {
        AccumuloVertex vertex = new AccumuloVertex(this, vertexId, vertexVisibility, properties);

        Mutation m = new Mutation(AccumuloVertex.ROW_KEY_PREFIX + vertex.getId());
        m.put(AccumuloVertex.CF_SIGNAL, EMPTY_TEXT, new ColumnVisibility(vertexVisibility.getVisibilityString()), EMPTY_VALUE);
        for (Property property : vertex.getProperties()) {
            addPropertyToMutation(m, property);
        }
        addMutations(m);

        getSearchIndex().addElement(vertex);

        return vertex;
    }

    private void addPropertyToMutation(Mutation m, Property property) {
        Text cf = AccumuloElement.CF_PROPERTY;
        Text columnQualifier = new Text(property.getName());
        ColumnVisibility columnVisibility = new ColumnVisibility(property.getVisibility().getVisibilityString());
        Value value = new Value(getValueSerializer().objectToValue(property.getValue()));
        m.put(cf, columnQualifier, columnVisibility, value);
    }

    private void addMutations(Mutation... mutations) {
        try {
            BatchWriter writer = getWriter();
            synchronized (this.writerLock) {
                for (Mutation m : mutations) {
                    writer.addMutation(m);
                }
                if (getConfiguration().isAutoFlush()) {
                    writer.flush();
                }
            }
        } catch (MutationsRejectedException ex) {
            throw new RuntimeException("Could not add mutation", ex);
        }
    }

    protected synchronized BatchWriter getWriter() {
        try {
            if (this.writer != null) {
                return this.writer;
            }
            BatchWriterConfig writerConfig = new BatchWriterConfig();
            this.writer = this.connector.createBatchWriter(getConfiguration().getTableName(), writerConfig);
            return this.writer;
        } catch (TableNotFoundException ex) {
            throw new RuntimeException("Could not create batch writer", ex);
        }
    }

    @Override
    public Iterable<Vertex> getVertices(Authorizations authorizations) throws SecureGraphException {
        return getVerticesInRange(null, null, authorizations);
    }

    @Override
    public void removeVertex(Object vertexId, Authorizations authorizations) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Edge addEdge(Object edgeId, Vertex outVertex, Vertex inVertex, String label, Visibility visibility, Property... properties) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Iterable<Edge> getEdges(Authorizations authorizations) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void removeEdge(Object edgeId, Authorizations authorizations) {
        throw new RuntimeException("Not implemented");
    }

    public ValueSerializer getValueSerializer() {
        return valueSerializer;
    }

    public SearchIndex getSearchIndex() {
        return searchIndex;
    }

    @Override
    public AccumuloGraphConfiguration getConfiguration() {
        return (AccumuloGraphConfiguration) super.getConfiguration();
    }

    private Iterable<Vertex> getVerticesInRange(Object startId, Object endId, Authorizations authorizations) throws SecureGraphException {
        final Scanner scanner = createVertexScanner(authorizations);

        Key startKey;
        if (startId == null) {
            startKey = new Key(AccumuloVertex.ROW_KEY_PREFIX);
        } else {
            startKey = new Key(AccumuloVertex.ROW_KEY_PREFIX + startId);
        }

        Key endKey;
        if (endId == null) {
            endKey = new Key(AccumuloVertex.AFTER_ROW_KEY_PREFIX);
        } else {
            endKey = new Key(AccumuloVertex.ROW_KEY_PREFIX + endId + "~");
        }

        scanner.setRange(new Range(startKey, endKey));
        scanner.clearColumns();

        return new LookAheadIterable<Iterator<Map.Entry<Key, Value>>, Vertex>() {

            @Override
            protected boolean isIncluded(Vertex obj) {
                return obj != null;
            }

            @Override
            protected Vertex convert(Iterator<Map.Entry<Key, Value>> next) {
                return createVertexFromRow(next);
            }

            @Override
            protected Iterator<Iterator<Map.Entry<Key, Value>>> createIterator() {
                return new RowIterator(scanner.iterator());
            }
        };
    }

    private Vertex createVertexFromRow(Iterator<Map.Entry<Key, Value>> row) throws SecureGraphException {
        String id = null;
        Map<String, Object> propertyValues = new HashMap<String, Object>();
        Map<String, Visibility> propertyVisibilities = new HashMap<String, Visibility>();
        Map<String, Map<String, Object>> propertyMetadata = new HashMap<String, Map<String, Object>>();
        ArrayList<String> inEdgeIds = new ArrayList<String>();
        ArrayList<String> outEdgeIds = new ArrayList<String>();
        Visibility vertexVisibility = null;

        while (row.hasNext()) {
            Map.Entry<Key, Value> col = row.next();
            if (id == null) {
                id = vertexIdFromRowKey(col.getKey().getRow().toString());
            }
            Text columnFamily = col.getKey().getColumnFamily();
            Text columnQualifier = col.getKey().getColumnQualifier();
            ColumnVisibility columnVisibility = new ColumnVisibility(col.getKey().getColumnVisibility().toString());
            Value value = col.getValue();

            if (AccumuloElement.CF_PROPERTY.toString().equals(columnFamily.toString())) {
                Object v = getValueSerializer().valueToObject(value);
                propertyValues.put(columnQualifier.toString(), v);
                propertyVisibilities.put(columnQualifier.toString(), new Visibility(columnVisibility.toString()));
                continue;
            }

            if (AccumuloElement.CF_PROPERTY_METADATA.toString().equals(columnFamily.toString())) {
                Object o = getValueSerializer().valueToObject(value);
                if (o == null) {
                    throw new SecureGraphException("Invalid metadata found. Expected " + Map.class.getName() + ". Found null.");
                } else if (o instanceof Map) {
                    Map v = (Map) o;
                    propertyMetadata.put(columnQualifier.toString(), v);
                } else {
                    throw new SecureGraphException("Invalid metadata found. Expected " + Map.class.getName() + ". Found " + o.getClass().getName() + ".");
                }
                continue;
            }

            if (AccumuloVertex.CF_SIGNAL.toString().equals(columnFamily.toString())) {
                vertexVisibility = new Visibility(columnVisibility.toString());
                continue;
            }

            if (AccumuloVertex.CF_OUT_EDGE.toString().equals(columnFamily.toString())) {
                outEdgeIds.add(columnQualifier.toString());
                continue;
            }

            if (AccumuloVertex.CF_IN_EDGE.toString().equals(columnFamily.toString())) {
                inEdgeIds.add(columnQualifier.toString());
                continue;
            }

            LOGGER.debug("Unhandled column {} {}", columnFamily, columnQualifier);
        }

        if (vertexVisibility == null) {
            throw new SecureGraphException("Invalid vertex visibility. This could occur if other columns are returned without the vertex signal column being returned.");
        }
        Property[] properties = toProperties(propertyValues, propertyVisibilities, propertyMetadata);
        return new AccumuloVertex(this, id, vertexVisibility, properties, inEdgeIds, outEdgeIds);
    }

    private Property[] toProperties(Map<String, Object> propertyValues, Map<String, Visibility> propertyVisibilities, Map<String, Map<String, Object>> propertyMetadata) {
        Property[] results = new Property[propertyValues.size()];
        int i = 0;
        for (Map.Entry<String, Object> propertyValueEntry : propertyValues.entrySet()) {
            String propertyName = propertyValueEntry.getKey();
            Object propertyValue = propertyValueEntry.getValue();
            results[i++] = new Property(propertyName, propertyValue, propertyVisibilities.get(propertyName), propertyMetadata.get(propertyName));
        }
        return results;
    }

    private String vertexIdFromRowKey(String rowKey) throws SecureGraphException {
        if (rowKey.startsWith(AccumuloVertex.ROW_KEY_PREFIX)) {
            return rowKey.substring(AccumuloVertex.ROW_KEY_PREFIX.length());
        }
        throw new SecureGraphException("Invalid row key for vertex: " + rowKey);
    }

    private Scanner createVertexScanner(Authorizations authorizations) throws SecureGraphException {
        return createElementVisibilityScanner(authorizations, ElementVisibilityRowFilter.OPT_FILTER_VERTICES);
    }

    private Scanner createElementVisibilityScanner(Authorizations authorizations, String elementMode) throws SecureGraphException {
        try {
            Scanner scanner = connector.createScanner(getConfiguration().getTableName(), toAccumuloAuthorizations(authorizations));
            IteratorSetting iteratorSetting = new IteratorSetting(
                    100,
                    ElementVisibilityRowFilter.class.getSimpleName(),
                    ElementVisibilityRowFilter.class
            );
            iteratorSetting.addOption(elementMode, Boolean.TRUE.toString());
            scanner.addScanIterator(iteratorSetting);
            return scanner;
        } catch (TableNotFoundException e) {
            throw new SecureGraphException(e);
        }
    }

    private org.apache.accumulo.core.security.Authorizations toAccumuloAuthorizations(Authorizations authorizations) {
        return new org.apache.accumulo.core.security.Authorizations(authorizations.getAuthorizations());
    }
}