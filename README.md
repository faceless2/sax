# BFO SAX Parser

A non-validating Java SAX parser that is designed for performance.

* Just plain faster. Even without the features below, benchmarking is
  roughly three times faster than Apache Xerces.

* Multi-threaded: XML reading is done in a secondary thread, with SAX events
  fired from the primary thread. This lets XML parsing continue in the
  background while your application processes `startElement()`, `characters`
  etc.

* Xerces caches DTDs based on the Public Id and resolved System Id. This
  implementation caches on the Public Id (by default, can be disabled) and
  on the _checksum_ of the entity. This allows caching of elements returned
  from a custom `EntityResolver`, even if there is no `public` or `system`
  ID specified. Checksumming is done with MurmurHash3 so is blazing fast.

While non-validating, it does parse DTDs to correctly evaluate entities, default attribute values and so on.

Respects the following features:

  * `http://xml.org/sax/features/namespaces` - this is on and can't be turned off
  * `http://xml.org/sax/features/validation` - this is off, and can't be turned on
  * `http://xml.org/sax/features/external-general-entities`
  * `http://xml.org/sax/features/external-parameter-entities`
  * `http://xml.org/sax/features/string-interning` - does nothing
  * `http://xml.org/sax/features/namespace-prefixes`
  * `http://xml.org/sax/features/use-entity-resolver2`
  * `http://apache.org/xml/features/nonvalidating/load-external-dtd`
  * `http://apache.org/xml/features/disallow-doctype-dec`
  * `http://bfo.com/sax/features/threads` - on by default, turn off to parse without a secondary thread
  * `http://bfo.com/sax/features/cache` - on by default, turn off to parse without any caching for DTDs or external entities
  * `http://bfo.com/sax/features/cache-publicid` - on by default, turn off to not presume that a public-id on an external entity uniquely defines it
  * `http://javax.xml.XMLConstants/feature/secure-processing`

Respects the following properties

  * `http://xml.org/sax/properties/lexical-handler`
  * `http://xml.org/sax/properties/declaration-handler`
  * `http://xml.org/sax/properties/document-xml-version`
  * `http://apache.org/xml/properties/input-buffer-size`
  * `http://javax.xml.XMLConstants/property/accessExternalDTD` - which will default to the System property `javax.xml.accessExternalDTD` and functions as described by [JAXP](https://docs.oracle.com/javase/tutorial/jaxp/properties/properties.html)
