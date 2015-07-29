/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.drive.adapter.impl;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.common.utils.URIUtils;
import org.nuxeo.drive.adapter.FileItem;
import org.nuxeo.drive.adapter.FolderItem;
import org.nuxeo.drive.service.NuxeoDriveManager;
import org.nuxeo.drive.service.VersioningFileSystemItemFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.runtime.api.Framework;

/**
 * {@link DocumentModel} backed implementation of a {@link FileItem}.
 *
 * @author Antoine Taillefer
 */
public class DocumentBackedFileItem extends AbstractDocumentBackedFileSystemItem implements FileItem {

    private static final long serialVersionUID = 1L;

    protected String downloadURL;

    protected String digestAlgorithm;

    protected String digest;

    protected boolean canUpdate;

    protected VersioningFileSystemItemFactory factory;

    public DocumentBackedFileItem(VersioningFileSystemItemFactory factory, DocumentModel doc) {
        this(factory, doc, false);
    }

    public DocumentBackedFileItem(VersioningFileSystemItemFactory factory, DocumentModel doc,
            boolean relaxSyncRootConstraint) {
        super(factory.getName(), doc, relaxSyncRootConstraint);
        initialize(factory, doc);
    }

    public DocumentBackedFileItem(VersioningFileSystemItemFactory factory, FolderItem parentItem, DocumentModel doc) {
        this(factory, parentItem, doc, false);
    }

    public DocumentBackedFileItem(VersioningFileSystemItemFactory factory, FolderItem parentItem, DocumentModel doc,
            boolean relaxSyncRootConstraint) {
        super(factory.getName(), parentItem, doc, relaxSyncRootConstraint);
        initialize(factory, doc);
    }

    protected DocumentBackedFileItem() {
        // Needed for JSON deserialization
    }

    /*--------------------- FileSystemItem ---------------------*/
    @Override
    public void rename(String name) {
        try (CoreSession session = CoreInstance.openCoreSession(repositoryName, principal)) {
            /* Update doc properties */
            DocumentModel doc = getDocument(session);
            // Handle versioning
            FileSystemItemHelper.versionIfNeeded(factory, doc, session);
            BlobHolder bh = getBlobHolder(doc);
            Blob blob = getBlob(bh);
            blob.setFilename(name);
            bh.setBlob(blob);
            updateDocTitleIfNeeded(doc, name);
            doc = session.saveDocument(doc);
            session.save();
            /* Update FileSystemItem attributes */
            this.name = name;
            updateDownloadURL();
            updateLastModificationDate(doc);
        }
    }

    /*--------------------- FileItem -----------------*/
    @Override
    public Blob getBlob() {
        try (CoreSession session = CoreInstance.openCoreSession(repositoryName, principal)) {
            DocumentModel doc = getDocument(session);
            return getBlob(doc);
        }
    }

    @Override
    public String getDownloadURL() {
        return downloadURL;
    }

    @Override
    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    @Override
    public String getDigest() {
        return digest;
    }

    @Override
    public boolean getCanUpdate() {
        return canUpdate;
    }

    @Override
    public void setBlob(Blob blob) {
        try (CoreSession session = CoreInstance.openCoreSession(repositoryName, principal)) {
            /* Update doc properties */
            DocumentModel doc = getDocument(session);
            // Handle versioning
            FileSystemItemHelper.versionIfNeeded(factory, doc, session);
            // If blob's filename is empty, set it to the current name
            String blobFileName = blob.getFilename();
            if (StringUtils.isEmpty(blobFileName)) {
                blob.setFilename(name);
            } else {
                updateDocTitleIfNeeded(doc, blobFileName);
                name = blobFileName;
                updateDownloadURL();
            }
            BlobHolder bh = getBlobHolder(doc);
            bh.setBlob(blob);
            doc = session.saveDocument(doc);
            session.save();
            /* Update FileSystemItem attributes */
            updateLastModificationDate(doc);
            updateDigest(doc);
        }
    }

    /*--------------------- Protected -----------------*/
    protected final void initialize(VersioningFileSystemItemFactory factory, DocumentModel doc) {
        this.factory = factory;
        name = getFileName(doc);
        folder = false;
        updateDownloadURL();
        // TODO: should get the digest algorithm from the binary store
        // configuration, but it is not exposed as a public API for now
        digestAlgorithm = FileSystemItemHelper.MD5_DIGEST_ALGORITHM;
        updateDigest(doc);
        if (digest == null) {
            digestAlgorithm = null;
        }
        canUpdate = canRename;
    }

    protected BlobHolder getBlobHolder(DocumentModel doc) {
        BlobHolder bh = doc.getAdapter(BlobHolder.class);
        if (bh == null) {
            throw new NuxeoException(
                    String.format(
                            "Document %s is not a BlobHolder, it is not adaptable as a FileItem and therefore it cannot not be part of the items to synchronize.",
                            doc.getId()));
        }
        return bh;
    }

    protected Blob getBlob(BlobHolder blobHolder) {
        Blob blob = blobHolder.getBlob();
        if (blob == null) {
            throw new NuxeoException(
                    "Document has no blob, it is not adaptable as a FileItem and therefore it cannot not be part of the items to synchronize.");
        }
        return blob;
    }

    protected Blob getBlob(DocumentModel doc) {
        BlobHolder bh = getBlobHolder(doc);
        return getBlob(bh);
    }

    protected String getFileName(DocumentModel doc) {
        String filename = getBlob(doc).getFilename();
        return filename != null ? filename : doc.getTitle();
    }

    protected void updateDocTitleIfNeeded(DocumentModel doc, String name) {
        // TODO: not sure about the behavior for the doc title
        if (this.name.equals(docTitle)) {
            doc.setPropertyValue("dc:title", name);
            docTitle = name;
        }
    }

    protected void updateDownloadURL() {
        StringBuilder downloadURLSb = new StringBuilder();
        downloadURLSb.append("nxbigfile/");
        downloadURLSb.append(repositoryName);
        downloadURLSb.append("/");
        downloadURLSb.append(docId);
        downloadURLSb.append("/");
        downloadURLSb.append("blobholder:0");
        downloadURLSb.append("/");
        // Remove chars that are invalid in filesystem names
        String escapedFilename = name.replaceAll("(/|\\\\|\\*|<|>|\\?|\"|:|\\|)", "-");
        downloadURLSb.append(URIUtils.quoteURIPathComponent(escapedFilename, true));
        downloadURL = downloadURLSb.toString();
    }

    protected void updateDigest(DocumentModel doc) {
        Blob blob = getBlob(doc);
        // Force digest computation for a StringBlob,
        // typically the note:note property of a Note document
        digest = FileSystemItemHelper.getDigest(blob, digestAlgorithm);
    }

    protected NuxeoDriveManager getNuxeoDriveManager() {
        return Framework.getLocalService(NuxeoDriveManager.class);
    }

    /*---------- Needed for JSON deserialization ----------*/
    protected void setDownloadURL(String downloadURL) {
        this.downloadURL = downloadURL;
    }

    protected void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    protected void setDigest(String digest) {
        this.digest = digest;
    }

    protected void setCanUpdate(boolean canUpdate) {
        this.canUpdate = canUpdate;
    }

}
