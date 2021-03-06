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

import java.io.IOException;

import javax.mail.internet.ParseException;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.drive.adapter.FileItem;
import org.nuxeo.drive.adapter.FileSystemItem;
import org.nuxeo.drive.service.FileSystemItemManager;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.runtime.api.Framework;

/**
 * Creates a file with the given blob in the {@link FileSystemItem} with the given id for the currently authenticated
 * user.
 *
 * @author Antoine Taillefer
 */
@Operation(id = NuxeoDriveCreateFile.ID, category = Constants.CAT_SERVICES, label = "Nuxeo Drive: Create file")
public class NuxeoDriveCreateFile {

    public static final String ID = "NuxeoDrive.CreateFile";

    @Context
    protected OperationContext ctx;

    @Param(name = "parentId")
    protected String parentId;

    /**
     * @deprecated
     * @see https://jira.nuxeo.com/browse/NXP-12173
     */
    @Deprecated
    @Param(name = "name", required = false)
    protected String name;

    @OperationMethod
    public Blob run(Blob blob) throws ParseException, IOException {

        FileSystemItemManager fileSystemItemManager = Framework.getLocalService(FileSystemItemManager.class);
        // The filename transfered by the multipart encoding is not preserved
        // correctly if there is non ascii characters in it.
        if (StringUtils.isNotBlank(name)) {
            blob.setFilename(name);
        }
        NuxeoDriveOperationHelper.normalizeMimeTypeAndEncoding(blob);
        FileItem fileItem = fileSystemItemManager.createFile(parentId, blob, ctx.getPrincipal());

        return NuxeoDriveOperationHelper.asJSONBlob(fileItem);
    }

}
