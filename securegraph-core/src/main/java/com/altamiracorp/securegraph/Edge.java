package com.altamiracorp.securegraph;

public interface Edge extends Element {
    /**
     * The edge label.
     */
    String getLabel();

    /**
     * Get the attach vertex id on either side of the edge.
     *
     * @param direction The side of the edge to get the vertex id from (IN or OUT).
     * @return The id of the vertex.
     */
    Object getVertexId(Direction direction);

    /**
     * Get the attach vertex on either side of the edge.
     *
     * @param direction The side of the edge to get the vertex from (IN or OUT).
     * @return The vertex.
     */
    Vertex getVertex(Direction direction, Authorizations authorizations);

    /**
     * Given a vertexId that represents one side of a relationship, get me the id of the other side.
     */
    Object getOtherVertexId(Object myVertexId);

    /**
     * Given a vertexId that represents one side of a relationship, get me the vertex of the other side.
     */
    Vertex getOtherVertex(Object myVertexId, Authorizations authorizations);

    /**
     * Prepares a mutation to allow changing multiple property values at the same time. This method is similar to
     * com.altamiracorp.securegraph.Graph#prepareEdge(com.altamiracorp.securegraph.Visibility, com.altamiracorp.securegraph.Authorizations)
     * in that it allows multiple properties to be changed and saved in a single mutation.
     *
     * @return The mutation builder.
     */
    ExistingElementMutation<Edge> prepareMutation();
}
