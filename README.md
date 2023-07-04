# BFO SAX/StAX Parser

A non-validating Java SAX / StAX parser that is designed for performance.

* Just plain faster. Even without the features below, benchmarking is
  roughly three times faster than Apache Xerces.

* Multi-threaded: XML reading is done in a secondary thread, with SAX events
  fired from the primary thread. This lets XML parsing continue in the
  background while your application processes `startElement()`, `characters`
  etc.

* Xerces caches DTDs based on the Public Id and resolved System Id. This
  implementation caches on the Public Id (by default, but this can be disabled) and
  on the _checksum_ of the entity. This allows caching of DTD elements returned
  from a custom `EntityResolver`, even if there is no `public` or `system`
  ID specified. Checksumming is done with MurmurHash3, and is extremely fast.

While non-validating, it does parse DTDs to correctly evaluate entities, default attribute values and so on.

The XMLReader and its SAXParserFactory accept the following features:

  * `http://xml.org/sax/features/validation` - recognised, but only accepts the default value of false.
  * `http://xml.org/sax/features/namespaces` - true by default
  * `http://xml.org/sax/features/namespace-prefixes` - true by default
  * `http://xml.org/sax/features/external-general-entities`
  * `http://xml.org/sax/features/external-parameter-entities`
  * `http://xml.org/sax/features/string-interning` - doesn't use <code>String.intern()</code> [of course](https://shipilev.net/jvm/anatomy-quarks/10-string-intern/); if set on will deduplicate strings using a local map.
  * `http://xml.org/sax/features/use-entity-resolver2`
  * `http://apache.org/xml/features/nonvalidating/load-external-dtd`
  * `http://apache.org/xml/features/disallow-doctype-dec`
  * `http://bfo.com/sax/features/threads` - on by default, turn off to parse without a secondary thread
  * `http://bfo.com/sax/features/cache` - on by default, turn off to parse without any caching for DTDs or external entities
  * `http://bfo.com/sax/features/cache-publicid` - on by default, turn off to not presume that a public-id on an external entity uniquely defines it
  * `http://javax.xml.XMLConstants/feature/secure-processing`

The XMLReader and its SAXParserFactory accept the following properties:

  * `http://xml.org/sax/properties/lexical-handler`
  * `http://xml.org/sax/properties/declaration-handler`
  * `http://xml.org/sax/properties/document-xml-version`
  * `http://apache.org/xml/properties/input-buffer-size`
  * `http://javax.xml.XMLConstants/property/accessExternalDTD` - which will default to the System property `javax.xml.accessExternalDTD` and functions as described by [JAXP](https://docs.oracle.com/javase/tutorial/jaxp/properties/properties.html)

## StAX parser

The Jar includes an `XMLInputFactory` which can create an `XMLStreamReader`. This is built on the
SAX parser code so has the same speed benefits.

The implementation shipping with Java 17 seems to have many bugs, so testing for API compliance
is much harder. The `XMLEventReader` interface is there but untested, however as it's simply the
addition of vast amounts of boilerplate code around `XMLStreamReader` it's likely to work to the same level.

