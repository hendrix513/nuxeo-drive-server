/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.drive.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.security.Principal;
import java.util.Calendar;
import java.util.List;

import javax.inject.Inject;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.drive.service.FileSystemChangeSummary;
import org.nuxeo.drive.service.FileSystemItemChange;
import org.nuxeo.drive.service.NuxeoDriveManager;
import org.nuxeo.drive.service.impl.FileSystemChangeSummaryImpl;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.Blob;
import org.nuxeo.ecm.automation.test.EmbeddedAutomationServerFeature;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.storage.sql.H2OnlyFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.Jetty;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Tests the {@link NuxeoDriveGetChangeSummary} operation on multiple repositories.
 *
 * @author Antoine Taillefer
 */
@RunWith(FeaturesRunner.class)
@Features({ H2OnlyFeature.class, EmbeddedAutomationServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.runtime.datasource", "org.nuxeo.drive.core", "org.nuxeo.drive.operations",
        "org.nuxeo.ecm.core.cache", "org.nuxeo.drive.core.test:OSGI-INF/test-nuxeodrive-sync-root-cache-contrib.xml" })
@LocalDeploy({ "org.nuxeo.drive.operations:drive-repo-ds.xml",
        "org.nuxeo.drive.operations:test-other-repository-config.xml",
        "org.nuxeo.drive.operations:OSGI-INF/test-nuxeodrive-change-finder-contrib.xml" })
@Jetty(port = 18080)
public class TestGetChangeSummaryMultiRepo {

    @Inject
    protected CoreSession session;

    @Inject
    protected NuxeoDriveManager nuxeoDriveManager;

    @Inject
    protected Session clientSession;

    protected CoreSession otherSession;

    protected long lastSyncDate;

    protected String lastSyncActiveRoots;

    protected DocumentModel folder1;

    protected DocumentModel folder2;

    protected DocumentModel folder3;

    protected ObjectMapper mapper;

    @Before
    public void init() throws Exception {

        otherSession = CoreInstance.openCoreSession("other", "Administrator");

        lastSyncDate = Calendar.getInstance().getTimeInMillis();
        lastSyncActiveRoots = "";

        folder1 = session.createDocument(session.createDocumentModel("/", "folder1", "Folder"));
        folder2 = session.createDocument(session.createDocumentModel("/", "folder2", "Folder"));
        folder3 = otherSession.createDocument(otherSession.createDocumentModel("/", "folder3", "Folder"));

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        mapper = new ObjectMapper();
    }

    @After
    public void cleanUp() throws Exception {

        // Reset 'other' repository
        otherSession.removeChildren(new PathRef("/"));

        // Close session bound to the 'other' repository
        otherSession.close();
        otherSession = null;
    }

    @Test
    public void testGetDocumentChangesSummary() throws Exception {

        // Register 3 sync roots and create 3 documents: 2 in the 'test'
        // repository, 1 in the 'other' repository
        DocumentModel doc1;
        DocumentModel doc2;
        DocumentModel doc3;

        Principal administrator = session.getPrincipal();
        nuxeoDriveManager.registerSynchronizationRoot(administrator, folder1, session);
        nuxeoDriveManager.registerSynchronizationRoot(administrator, folder2, session);
        nuxeoDriveManager.registerSynchronizationRoot(administrator, folder3, otherSession);

        doc1 = session.createDocumentModel("/folder1", "doc1", "File");
        doc1.setPropertyValue("file:content", new StringBlob("The content of file 1."));
        doc1 = session.createDocument(doc1);
        Thread.sleep(1000);
        doc2 = session.createDocumentModel("/folder2", "doc2", "File");
        doc2.setPropertyValue("file:content", new StringBlob("The content of file 2."));
        doc2 = session.createDocument(doc2);
        Thread.sleep(1000);
        doc3 = otherSession.createDocumentModel("/folder3", "doc3", "File");
        doc3.setPropertyValue("file:content", new StringBlob("The content of file 3."));
        doc3 = otherSession.createDocument(doc3);

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // Look in all repositories => should find 3 changes
        FileSystemChangeSummary changeSummary = getChangeSummary();
        List<FileSystemItemChange> docChanges = changeSummary.getFileSystemChanges();
        assertEquals(3, docChanges.size());

        FileSystemItemChange docChange = docChanges.get(2);
        assertEquals("test", docChange.getRepositoryId());
        assertEquals("documentChanged", docChange.getEventId());
        assertEquals(doc1.getId(), docChange.getDocUuid());
        assertEquals(Boolean.FALSE, changeSummary.getHasTooManyChanges());

        docChange = docChanges.get(1);
        assertEquals("test", docChange.getRepositoryId());
        assertEquals("documentChanged", docChange.getEventId());
        assertEquals(doc2.getId(), docChange.getDocUuid());

        docChange = docChanges.get(0);
        assertEquals("other", docChange.getRepositoryId());
        assertEquals("documentChanged", docChange.getEventId());
        assertEquals(doc3.getId(), docChange.getDocUuid());

        // Update documents
        doc1.setPropertyValue("dc:description", "Added description to doc1.");
        doc2.setPropertyValue("dc:description", "Added description to doc1.");
        doc3.setPropertyValue("dc:description", "Added description to doc1.");
        session.saveDocument(doc1);
        session.saveDocument(doc2);
        otherSession.saveDocument(doc3);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        changeSummary = getChangeSummary();
        docChanges = changeSummary.getFileSystemChanges();
        assertEquals(3, docChanges.size());
    }

    protected FileSystemChangeSummary getChangeSummary() throws Exception {
        // Wait 1 second as the mock change finder relies on steps of 1 second
        Thread.sleep(1000);
        Blob docChangeSummaryJSON = (Blob) clientSession.newRequest(NuxeoDriveGetChangeSummary.ID).set("lastSyncDate",
                lastSyncDate).set("lastSyncActiveRootDefinitions", lastSyncActiveRoots).execute();
        assertNotNull(docChangeSummaryJSON);

        FileSystemChangeSummary changeSummary = mapper.readValue(docChangeSummaryJSON.getStream(),
                FileSystemChangeSummaryImpl.class);
        assertNotNull(changeSummary);

        lastSyncDate = changeSummary.getSyncDate();
        lastSyncActiveRoots = changeSummary.getActiveSynchronizationRootDefinitions();
        return changeSummary;
    }
}
