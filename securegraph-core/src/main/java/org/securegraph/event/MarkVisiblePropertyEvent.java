package org.securegraph.event;

import org.securegraph.Element;
import org.securegraph.Graph;
import org.securegraph.Property;
import org.securegraph.Visibility;

public class MarkVisiblePropertyEvent extends GraphEvent {
    private final Element element;
    private final Property property;
    private final Visibility visibility;

    public MarkVisiblePropertyEvent(Graph graph, Element element, Property property, Visibility visibility) {
        super(graph);
        this.element = element;
        this.property = property;
        this.visibility = visibility;
    }

    public Element getElement() {
        return element;
    }

    public Property getProperty() {
        return property;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public String toString() {
        return "MarkVisiblePropertyEvent{element=" + element + '}';
    }

    @Override
    public int hashCode() {
        return getElement().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MarkVisiblePropertyEvent)) {
            return false;
        }

        MarkVisiblePropertyEvent other = (MarkVisiblePropertyEvent) obj;
        return getElement().equals(other.getElement()) && super.equals(obj);
    }
}
