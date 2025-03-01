package com.bfo.sax;

import javax.xml.stream.*;
import javax.xml.stream.util.*;
import javax.xml.stream.events.*;
import javax.xml.stream.events.Attribute;
import javax.xml.namespace.*;
import java.util.*;

class BFOXMLEventReader implements XMLEventReader {

    private boolean eof;
    private XMLStreamReader reader;
    private XMLEventAllocator allocator;
    private XMLEvent lastEvent, nextEvent;

    BFOXMLEventReader(BFOXMLInputFactory factory, XMLStreamReader reader) throws  XMLStreamException {
        this.reader = reader;
        this.allocator = factory.getEventAllocator();
        if (this.allocator == null) {
            this.allocator = new XMLEventAllocatorImpl();
        }
    }

    public boolean hasNext() {
        try {
            return nextEvent != null || (!eof && reader.hasNext());
        } catch (XMLStreamException e) {
            return false;
        }
    }

    public XMLEvent nextEvent() throws XMLStreamException {
        if (nextEvent != null) {
            lastEvent = nextEvent;
            nextEvent = null;
        } else if (reader.hasNext() && !eof) {
            reader.next();
            lastEvent = allocator.allocate(reader);
            eof = reader.getEventType() == XMLEvent.END_DOCUMENT;
        } else {
            eof = true;
            lastEvent = null;
            throw new NoSuchElementException();
        }
        return lastEvent;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    public Object next() {
        try {
            return nextEvent();
        } catch (XMLStreamException e) {
            throw (NoSuchElementException)new NoSuchElementException(e.getMessage()).initCause(e);
        }
    }

    public void close() throws XMLStreamException {
        reader.close();
    }

    public String getElementText() throws XMLStreamException {
        if (lastEvent.getEventType() != XMLEvent.START_ELEMENT) {
            throw new XMLStreamException("parser must be on START_ELEMENT to read next text", lastEvent.getLocation());
        }
        CharSequence out = null;
        while (true) {
            XMLEvent event = nextEvent();
            int type = event.getEventType();
            String data = null;
            if (type == XMLEvent.CHARACTERS || type == XMLEvent.SPACE || type == XMLEvent.CDATA) {
                data = event.asCharacters().getData();
            } else if (type == XMLEvent.ENTITY_REFERENCE) {
                data = ((EntityReference)event).getDeclaration().getReplacementText();
            } else if (type == XMLEvent.COMMENT || type == XMLEvent.PROCESSING_INSTRUCTION) {
                //ignore
            } else if (type == XMLEvent.END_ELEMENT) {
                if (out == null) {
                    out = "";
                }
                break;
            } else {
                throw new XMLStreamException("getElementText() expected text, got " + eventString(type), event.getLocation());
            }
            if (out == null) {
                out = data;
            } else {
                if (!(out instanceof StringBuilder)) {
                    out = new StringBuilder(out);
                }
                ((StringBuilder)out).append(data);
            }
        }
        return out.toString();
    }

    public Object getProperty(String name) {
        return reader.getProperty(name) ;
    }

    public XMLEvent nextTag() throws XMLStreamException {
        XMLEvent event;
        do {
            event = nextEvent();
        } while (((event.getEventType() == XMLEvent.CHARACTERS || event.getEventType() == XMLEvent.CDATA) && event.asCharacters().isWhiteSpace()) || event.getEventType() == XMLEvent.COMMENT);
        if (!event.isStartElement() && !event.isStartElement()) {
            throw new XMLStreamException("nextTag() expected START_ELEMENT or END_ELEMENT, got " + eventString(event.getEventType()), event.getLocation());
        }
        return event;
    }

    public XMLEvent peek() throws XMLStreamException {
        if (nextEvent != null) {
            return nextEvent;
        } else if (hasNext()) {
            return nextEvent = nextEvent();
        } else {
            return null;
        }
    }

    private static String eventString(int type) {
        switch (type) {
            case XMLEvent.START_ELEMENT:            return "START_ELEMENT";
            case XMLEvent.END_ELEMENT:              return "END_ELEMENT";
            case XMLEvent.PROCESSING_INSTRUCTION:   return "PROCESSING_INSTRUCTION";
            case XMLEvent.CHARACTERS:               return "CHARACTERS";
            case XMLEvent.CDATA:                    return "CDATA";
            case XMLEvent.SPACE:                    return "SPACE";
            case XMLEvent.COMMENT:                  return "COMMENT";
            case XMLEvent.START_DOCUMENT:           return "START_DOCUMENT";
            case XMLEvent.END_DOCUMENT:             return "END_DOCUMENT";
            case XMLEvent.ENTITY_REFERENCE:         return "ENTITY_REFERENCE";
            case XMLEvent.ATTRIBUTE:                return "ATTRIBUTE";
            case XMLEvent.DTD:                      return "DTD";
            case XMLEvent.NAMESPACE:                return "NAMESPACE";
            case XMLEvent.ENTITY_DECLARATION:       return "ENTITY_DECLARATION";
            case XMLEvent.NOTATION_DECLARATION:     return "NOTATION_DECLARATION";
            default:                                return type < 0 ? "EOF" : "UNKNOWN-"+ type;
        }
    }

    private static class XMLEventAllocatorImpl implements XMLEventAllocator {
        private static final XMLEventFactory factory = XMLEventFactory.newFactory();
        public XMLEventAllocator newInstance() {
            return this;
        }
        public XMLEvent allocate(XMLStreamReader r) throws XMLStreamException {
            if (r == null) {
                throw new XMLStreamException("Reader cannot be null");
            }
            switch (r.getEventType()) {
                case XMLEvent.START_ELEMENT:
                    return factory.createStartElement(r.getPrefix(), r.getNamespaceURI(), r.getLocalName(), getAttributes(r), getNamespaces(r));
                case XMLEvent.END_ELEMENT:
                    return factory.createEndElement(r.getPrefix(), r.getNamespaceURI(), r.getLocalName(), getNamespaces(r));
                case XMLEvent.PROCESSING_INSTRUCTION:
                    return factory.createProcessingInstruction(r.getPITarget(), r.getPIData());
                case XMLEvent.CHARACTERS:
                    return factory.createCharacters(r.getText());
                case XMLEvent.CDATA:
                    return factory.createCData(r.getText());
                case XMLEvent.SPACE:
                    return factory.createSpace(r.getText());
                case XMLEvent.COMMENT:
                    return factory.createComment(r.getText());
                case XMLEvent.START_DOCUMENT:
                    return factory.createStartDocument(r.getCharacterEncodingScheme(), r.getVersion(), r.isStandalone());
                case XMLEvent.END_DOCUMENT:
                    return factory.createEndDocument();
                default:
                    throw new UnsupportedOperationException(eventString(r.getEventType()));
            }
        }

        private Iterator<Attribute> getAttributes(final XMLStreamReader r) {
            final int count = r.getAttributeCount();
            return new Iterator<Attribute>() {
                int ix = 0;
                public boolean hasNext() {
                    return ix < count;
                }
                public void remove() {
                    throw new UnsupportedOperationException();
                }
                public Attribute next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    int i = ix++;
                    return factory.createAttribute(r.getAttributeName(i), r.getAttributeValue(i));
                }
            };
        }

        private Iterator<Namespace> getNamespaces(final XMLStreamReader r) {
            final int count = r.getNamespaceCount();
            return new Iterator<Namespace>() {
                int ix = 0;
                public boolean hasNext() {
                    return ix < count;
                }
                public void remove() {
                    throw new UnsupportedOperationException();
                }
                public Namespace next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    final int i = ix++;
                    return factory.createNamespace(r.getNamespacePrefix(i), r.getNamespaceURI(i));
                }
            };
        }

        public void allocate(XMLStreamReader reader, XMLEventConsumer consumer) throws XMLStreamException {
            XMLEvent e = allocate(reader);
            if (e != null) {
                consumer.add(e);
            }
            return;
        }
    }

}
