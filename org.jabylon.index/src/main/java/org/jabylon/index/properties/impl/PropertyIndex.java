/**
 * (C) Copyright 2013 Jabylon (http://www.jabylon.org) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jabylon.index.properties.impl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.LockObtainFailedException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.cdo.common.id.CDOIDUtil;
import org.eclipse.emf.common.notify.Notification;
import org.jabylon.index.properties.IndexActivator;
import org.jabylon.index.properties.QueryService;
import org.jabylon.properties.PropertyFileDescriptor;
import org.jabylon.resources.changes.PropertiesListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Service
public class PropertyIndex extends Job implements PropertiesListener {

    private static final Logger logger = LoggerFactory.getLogger(PropertyIndex.class);
    BlockingQueue<DocumentTuple> writes;

    public PropertyIndex() {
        super("Index Job");
        writes = new ArrayBlockingQueue<DocumentTuple>(50);
    }


    @Override
    public void propertyFileAdded(PropertyFileDescriptor descriptor, boolean autoSync) {

        PropertyFileAnalyzer analyzer = new PropertyFileAnalyzer();
        List<Document> documents = analyzer.createDocuments(descriptor);
        try {
            writes.put(new DocumentTuple(documents));
            schedule();
        } catch (InterruptedException e) {
            logger.warn("Interrupted",e);
        }

    }

    @Override
    public void propertyFileDeleted(PropertyFileDescriptor descriptor, boolean autoSync) {
        try {
            writes.put(new DocumentTuple(descriptor));
            schedule();
        } catch (InterruptedException e) {
            logger.warn("Interrupted",e);
        }

    }

    @Override
    public void propertyFileModified(PropertyFileDescriptor descriptor, List<Notification> changes, boolean autoSync) {
        PropertyFileAnalyzer analyzer = new PropertyFileAnalyzer();
        List<Document> documents = analyzer.createDocuments(descriptor);
        try {
            writes.put(new DocumentTuple(descriptor, documents));
            schedule();
        } catch (InterruptedException e) {
            logger.warn("Interrupted",e);
        }

    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        IndexWriter writer = null;
        try {
            writer = IndexActivator.getDefault().obtainIndexWriter();
            while (true) {
                DocumentTuple documentTuple = writes.poll(20,TimeUnit.SECONDS);
                if (documentTuple == null)
                    break;
                List<Document> documents = documentTuple.getDocuments();
                switch (documentTuple.getAction()) {
                case CREATE:
                    for (Document document : documents) {
                        writer.addDocument(document);
                    }
                    break;
                case DELETE:
                    StringBuilder builder = new StringBuilder();
                    CDOIDUtil.write(builder, documentTuple.getDescriptor().cdoID());
                    writer.deleteDocuments(new Term(QueryService.FIELD_CDO_ID, builder.toString()));
                    break;
                case REPLACE:
                    writer.deleteDocuments(new Term(QueryService.FIELD_FULL_PATH, documentTuple.getDescriptor().fullPath().toString()));
                    for (Document document : documents) {
                        writer.addDocument(document);
                    }
                    break;

                default:
                    break;
                }

            }
            writer.commit();

        } catch (CorruptIndexException e) {
            logger.error("Exception while indexing",e);
        } catch (LockObtainFailedException e) {
            logger.error("Exception while indexing",e);
        } catch (IOException e) {
            logger.error("Exception while indexing",e);
        } catch (InterruptedException e) {
        	logger.warn("Interrupted while waiting for new index events",e);
		} finally {
            try {
                IndexActivator.getDefault().returnIndexWriter(writer);
            } catch (CorruptIndexException e) {
                logger.error("Exception while closing index writer",e);
            } catch (IOException e) {
                logger.error("Exception while closing index writer",e);
            }
        }

        return Status.OK_STATUS;
    }

    @Override
    public boolean belongsTo(Object family) {
        return IndexWriter.class == family;
    }

}

class DocumentTuple {
    private List<Document> docs;
    private DocumentAction action;
    private PropertyFileDescriptor descriptor;

    public DocumentTuple(List<Document> docs) {
        super();
        this.docs = docs;
        this.action = DocumentAction.CREATE;
    }

    public DocumentTuple(PropertyFileDescriptor descriptor) {
        super();
        this.descriptor = descriptor;
        this.action = DocumentAction.DELETE;
    }

    public DocumentTuple(PropertyFileDescriptor descriptor, List<Document> docs) {
        super();
        this.descriptor = descriptor;
        this.docs = docs;
        this.action = DocumentAction.REPLACE;
    }

    public DocumentAction getAction() {
        return action;
    }

    public List<Document> getDocuments() {
        return docs;
    }

    public PropertyFileDescriptor getDescriptor() {
        return descriptor;
    }

}

enum DocumentAction {
    CREATE, DELETE, REPLACE;
}
