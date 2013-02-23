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
package org.nuxeo.drive.service.impl;

import java.security.Principal;

import org.nuxeo.drive.adapter.FileSystemItem;
import org.nuxeo.drive.adapter.FolderItem;
import org.nuxeo.drive.adapter.impl.DefaultSyncRootFolderItem;
import org.nuxeo.drive.service.FileSystemItemAdapterService;
import org.nuxeo.drive.service.FileSystemItemFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;

/**
 * Default {@link FileSystemItemFactory} for a synchronization root
 * {@link FolderItem}.
 *
 * @author Antoine Taillefer
 */
public class DefaultSyncRootFolderItemFactory extends
        AbstractSyncRootFolderItemFactory {

    /*------------------- AbstractFileSystemItemFactory ---------------------*/
    @Override
    protected FileSystemItem adaptDocument(DocumentModel doc,
            boolean forceParentId, String parentId) throws ClientException {
        return new DefaultSyncRootFolderItem(name, parentId, doc);
    }

    /*------------------ AbstractSyncRootFolderItemFactory ------------------*/
    @Override
    protected String getParentId(DocumentModel doc) throws ClientException {
        FileSystemItemAdapterService fileSystemItemAdapterService = Framework.getLocalService(FileSystemItemAdapterService.class);
        Principal principal = doc.getCoreSession().getPrincipal();
        return fileSystemItemAdapterService.getTopLevelFolderItemFactory().getTopLevelFolderItem(
                principal).getId();
    }

}
