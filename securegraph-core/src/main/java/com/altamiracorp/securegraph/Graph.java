package com.altamiracorp.securegraph;

import com.altamiracorp.securegraph.query.GraphQuery;

import java.util.List;

public interface Graph {
    Vertex addVertex(Visibility visibility);

    Vertex addVertex(Object vertexId, Visibility visibility);

    VertexBuilder prepareVertex(Visibility visibility);

    VertexBuilder prepareVertex(Object vertexId, Visibility visibility);

    Vertex getVertex(Object vertexId, Authorizations authorizations);

    Iterable<Vertex> getVertices(Authorizations authorizations);

    void removeVertex(Vertex vertex, Authorizations authorizations);

    void removeVertex(String vertexId, Authorizations authorizations);

    Edge addEdge(Vertex outVertex, Vertex inVertex, String label, Visibility visibility);

    Edge addEdge(Object edgeId, Vertex outVertex, Vertex inVertex, String label, Visibility visibility);

    EdgeBuilder prepareEdge(Vertex outVertex, Vertex inVertex, String label, Visibility visibility);

    EdgeBuilder prepareEdge(Object edgeId, Vertex outVertex, Vertex inVertex, String label, Visibility visibility);

    Edge getEdge(Object edgeId, Authorizations authorizations);

    Iterable<Edge> getEdges(Authorizations authorizations);

    void removeEdge(Edge edge, Authorizations authorizations);

    void removeEdge(String edgeId, Authorizations authorizations);

    GraphQuery query(String queryString, Authorizations authorizations);

    GraphQuery query(Authorizations authorizations);

    void flush();

    void shutdown();

    Iterable<List<Object>> findPaths(Vertex sourceVertex, Vertex destVertex, int maxHops, Authorizations authorizations);
}
