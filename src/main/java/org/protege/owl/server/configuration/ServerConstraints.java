package org.protege.owl.server.configuration;

import static org.protege.owl.server.configuration.MetaprojectVocabulary.HAS_TRANSPORT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.protege.owl.server.api.server.Server;
import org.protege.owl.server.api.server.ServerComponentFactory;
import org.protege.owl.server.api.server.ServerTransport;
import org.protege.owl.server.core.SynchronizationFilter;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.search.EntitySearcher;

public class ServerConstraints {
    private Logger logger = LoggerFactory.getLogger(ServerConstraints.class.getCanonicalName());
	private OWLIndividual serverIndividual;

	private FilterConstraint containingFilterConstraint;
	private List<TransportConstraints> transportConstraints = new ArrayList<TransportConstraints>();
	
	public ServerConstraints(OWLOntology configuration, OWLIndividual serverIndividual) {
		this.serverIndividual = serverIndividual;
		containingFilterConstraint = FilterConstraint.getDelegateConstraint(configuration, serverIndividual);
		Collection<OWLIndividual> transports = EntitySearcher.getObjectPropertyValues(serverIndividual, HAS_TRANSPORT, configuration);
		if (transports != null) {
			for (OWLIndividual transport : transports) {
				transportConstraints.add(new TransportConstraints(configuration, transport));
			}
		}
	}


	public boolean satisfied(Set<ServerComponentFactory> factories) {
	    if (containingFilterConstraint != null && !containingFilterConstraint.satisfied(factories)) {
	        return false;
	    }
		for (TransportConstraints constraint : transportConstraints) {
			if (!constraint.satisfied(factories)) {
				return false;
			}
		}
		for (ServerComponentFactory factory : factories) {
			if (factory.hasSuitableServer(serverIndividual)) {
			    if (logger.isDebugEnabled()) {
			        logger.debug("Using " + factory + " to satisfy " + serverIndividual);
			    }
				return true;
			}
		}
		if (logger.isDebugEnabled()) {
		    logger.debug("No factory can create a core server matching the constraint: " + serverIndividual);
		}
		return false;
	}


	public List<ServerTransport> buildServerTransports(Set<ServerComponentFactory> factories, Server server) throws IOException {
		List<ServerTransport> transports = new ArrayList<ServerTransport>();
		for (TransportConstraints constraint : transportConstraints) {
			ServerTransport transport = constraint.build(factories);
			transports.add(transport);
			transport.start(server);
		}
		return transports;
	}
	
	public Server buildServer(Set<ServerComponentFactory> factories) {
	    Server baseServer = null;
		for (ServerComponentFactory factory : factories) {
			if (factory.hasSuitableServer(serverIndividual)) {
				baseServer = factory.createServer(serverIndividual);
			}
		}
		Server filteredServer = null;
		if (baseServer == null) {
		    throw new IllegalStateException("Expected to be ready to build the server...");
		}
		else if (containingFilterConstraint != null) {
		    filteredServer = containingFilterConstraint.build(baseServer, factories);
		}
		else {
		    filteredServer = baseServer;
		}
		return new SynchronizationFilter(filteredServer);
	}
	

}