package org.protege.owl.server.core;

import org.protege.owl.server.api.OntologyDocumentRevision;
import org.protege.owl.server.api.RemoteOntologyDocument;
import org.semanticweb.owlapi.model.IRI;


/**
 * This class represents an ontology document that is obtained from an OWL server by giving a location and a revision.
 * <p/>
 * From the OWL 2 Specification, an Ontology Document is defined as follows: "Each ontology is associated 
 * with an <em>ontology document</em>,
 * which physically contains the ontology stored in a particular way.  The name "ontology document"
 * reflects the expectation that a large number of ontologies will be stored in physical text 
 * documents written in one of the syntaxes of OWL 2."  By referencing a location on an OWL Server
 * and providing a revision, enough information has been given to instantiate an ontology.
 * Thus this would appear to be a reasonable definition of an Ontology Document in the OWL 2 sense.
 * 
 * @author tredmond
 *
 */
public class RemoteOntologyDocumentImpl extends ServerDocumentImpl implements RemoteOntologyDocument {
	private static final long serialVersionUID = 8685750766323114980L;
	
	public RemoteOntologyDocumentImpl(IRI backingStore) {
		super(backingStore);
	}
	
	@Override
	public String toString() {
	    return "<Doc: " + getServerLocation() + ">";
	}

}