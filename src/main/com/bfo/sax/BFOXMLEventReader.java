package com.bfo.sax;

import javax.xml.stream.*;
import javax.xml.stream.util.*;
import javax.xml.stream.events.*;
import javax.xml.stream.events.Attribute;
import javax.xml.namespace.*;
import java.util.*;

class BFOXMLEventReader implements XMLEventReader {

    private XMLStreamReader reader;
    private XMLEventAllocator allocator;
    private XMLEvent lastEvent, nextEvent;

    BFOXMLEventReader(BFOXMLInputFactory factory, XMLStreamReader reader) throws  XMLStreamException {
        this.reader = reader;
        this.allocator = factory.getEventAllocator();
        if (this.allocator == null) {
            this.allocator = new XMLEventAllocatorImpl();
        }
        nextEvent = allocator.allocate(reader);
    }

    public boolean hasNext() {
        if (nextEvent != null) {
            return true;
        }
        try {
            return reader.hasNext();
        } catch (XMLStreamException e) {
            return false;
        }
    }


    public XMLEvent nextEvent() throws XMLStreamException {
        if (nextEvent != null) {
            lastEvent = nextEvent ;
            nextEvent = null;
            return lastEvent;
        } else if (reader.hasNext()) {
            reader.next();
            lastEvent = allocator.allocate(reader);
            return lastEvent;
        } else {
            lastEvent = null;
            throw new NoSuchElementException();
        }
    }

    public void remove() {
        //remove of the event is not supported.
        throw new java.lang.UnsupportedOperationException();
    }


    public void close() throws XMLStreamException {
        reader.close();
    }

    public String getElementText() throws XMLStreamException {
        if (lastEvent.getEventType() != XMLEvent.START_ELEMENT) {
            throw new XMLStreamException("parser must be on START_ELEMENT to read next text", lastEvent.getLocation());
        }
        String data = null;
        if (nextEvent != null) {
            XMLEvent event = nextEvent;
            nextEvent = null;
            int type = event.getEventType();

            if (type == XMLEvent.CHARACTERS || type == XMLEvent.SPACE || type == XMLEvent.CDATA) {
                data = event.asCharacters().getData();
            } else if (type == XMLEvent.ENTITY_REFERENCE) {
                data = ((EntityReference)event).getDeclaration().getReplacementText();
            } else if (type == XMLEvent.COMMENT || type == XMLEvent.PROCESSING_INSTRUCTION) {
                //ignore
            } else if (type == XMLEvent.START_ELEMENT) {
                throw new XMLStreamException("elementGetText() function expects text only elment but START_ELEMENT was encountered.", event.getLocation());
            } else if (type == XMLEvent.END_ELEMENT) {
                return "";
            }

            StringBuilder buf = new StringBuilder();
            if (data != null) {
                buf.append(data);
            }
            event = nextEvent();
            while ((type = event.getEventType()) != XMLEvent.END_ELEMENT) {
                if (type == XMLEvent.CHARACTERS || type == XMLEvent.SPACE || type == XMLEvent.CDATA) {
                    data = event.asCharacters().getData();
                } else if (type == XMLEvent.ENTITY_REFERENCE) {
                    data = ((EntityReference)event).getDeclaration().getReplacementText();
                } else if (type == XMLEvent.COMMENT || type == XMLEvent.PROCESSING_INSTRUCTION) {
                    data = null;
                } else if (type == XMLEvent.END_DOCUMENT) {
                    throw new XMLStreamException("unexpected end of document when reading element text content");
                } else if (type == XMLEvent.START_ELEMENT) {
                    throw new XMLStreamException("elementGetText() function expects text only elment but START_ELEMENT was encountered.", event.getLocation());
                } else {
                    throw new XMLStreamException("Unexpected event type "+ type, event.getLocation());
                }
                //add the data to the buf
                if (data != null) {
                    buf.append(data);
                }
                event = nextEvent();
            }
            return buf.toString();
        }
        data = reader.getElementText();
        lastEvent = allocator.allocate(reader);
        return data;
    }

    public Object getProperty(String name) {
        return reader.getProperty(name) ;
    }

    public XMLEvent nextTag() throws XMLStreamException {
        if (nextEvent != null) {
            XMLEvent event = nextEvent;
            nextEvent = null;
            int eventType = event.getEventType();
            if ((event.isCharacters() && event.asCharacters().isWhiteSpace()) || eventType == XMLStreamConstants.PROCESSING_INSTRUCTION || eventType == XMLStreamConstants.COMMENT || eventType == XMLStreamConstants.START_DOCUMENT) {
                event = nextEvent();
                eventType = event.getEventType();
            }
            while ((event.isCharacters() && event.asCharacters().isWhiteSpace()) || eventType == XMLStreamConstants.PROCESSING_INSTRUCTION || eventType == XMLStreamConstants.COMMENT) {
                event = nextEvent();
                eventType = event.getEventType();
            }
            if (eventType != XMLStreamConstants.START_ELEMENT && eventType != XMLStreamConstants.END_ELEMENT) {
                throw new XMLStreamException("expected start or end tag", event.getLocation());
            }
            return event;
        }
        reader.nextTag();
        lastEvent = allocator.allocate(reader);
        return lastEvent;
    }

    public Object next() {
        Object object = null;
        try{
            object = nextEvent();
        } catch (XMLStreamException e) {
            lastEvent = null;
            return (NoSuchElementException)new NoSuchElementException(e.getMessage()).initCause(e);
        }
        return object;
    }

    public XMLEvent peek() throws XMLStreamException{
        if (nextEvent != null) {
            return nextEvent;
        }

        if (hasNext()) {
            reader.next();
            nextEvent = allocator.allocate(reader);
            return nextEvent;
        } else {
            return null;
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
                case XMLEvent.ENTITY_REFERENCE:
                case XMLEvent.ATTRIBUTE:
                case XMLEvent.DTD: 
                default:
                    throw new UnsupportedOperationException();
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
