/*
 * @(#) ElementIterator.java
 */

package net.pwall.xml;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * An iterator to iterate over the child elements of a given node.  Child nodes that are not
 * elements are skipped.  
 *
 * @author Peter Wall
 */

public class ElementIterator implements Iterator<Element> {

    private NodeList childNodes;
    private int index;
    private Node oldChild;

    public ElementIterator(Node parent) {
        if (parent == null)
            throw new IllegalArgumentException("ElementIterator parent must not be null");
        childNodes = parent.getChildNodes();
        index = 0;
        oldChild = null;
    }

    @Override
    public boolean hasNext() {
        for (; index < childNodes.getLength(); index++)
            if (childNodes.item(index) instanceof Element)
                return true;
        return false;
    }

    @Override
    public Element next() {
        if (!hasNext()) {
            oldChild = null;
            throw new NoSuchElementException();
        }
        oldChild = childNodes.item(index++);
        return (Element)oldChild;
    }

    @Override
    public void remove() {
        if (oldChild == null)
            throw new IllegalStateException();
        // the following code is intended to ensure that the index is repositioned correctly,
        // regardless of whether the childNodes NodeList is implemented as a live list or not
        if (hasNext()) {
            Node nextChild = childNodes.item(index);
            oldChild.getParentNode().removeChild(oldChild);
            for (index = 0; index < childNodes.getLength(); index++)
                if (childNodes.item(index) == nextChild)
                    break;
        }
        else {
            oldChild.getParentNode().removeChild(oldChild);
            index = childNodes.getLength();
        }
        oldChild = null;
    }

}
