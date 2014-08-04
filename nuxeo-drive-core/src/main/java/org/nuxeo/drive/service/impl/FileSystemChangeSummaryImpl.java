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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.nuxeo.drive.service.FileSystemChangeSummary;
import org.nuxeo.drive.service.FileSystemItemChange;
import org.nuxeo.ecm.core.api.IdRef;

/**
 * Default implementation of a {@link FileSystemChangeSummary}.
 *
 * @author Antoine Taillefer
 */
public class FileSystemChangeSummaryImpl implements FileSystemChangeSummary {

    private static final long serialVersionUID = 1L;

    protected List<FileSystemItemChange> fileSystemChanges;

    protected Long syncDate = null;

    protected Long upperBound = null;

    protected Boolean hasTooManyChanges = Boolean.FALSE;

    protected String activeSynchronizationRootDefinitions;

    public FileSystemChangeSummaryImpl() {
        // Needed for JSON deserialization
    }

    public FileSystemChangeSummaryImpl(
            List<FileSystemItemChange> fileSystemChanges,
            Map<String, Set<IdRef>> activeRootRefs, Long syncDate,
            Long upperBound, Boolean tooManyChanges) {
        this.fileSystemChanges = fileSystemChanges;
        this.syncDate = syncDate;
        this.upperBound = upperBound;
        this.hasTooManyChanges = tooManyChanges;
        List<String> rootDefinitions = new ArrayList<String>();
        for (Map.Entry<String, Set<IdRef>> entry : activeRootRefs.entrySet()) {
            for (IdRef ref : entry.getValue()) {
                rootDefinitions.add(String.format("%s:%s", entry.getKey(),
                        ref.toString()));
            }
        }
        this.activeSynchronizationRootDefinitions = StringUtils.join(
                rootDefinitions, ",");
    }

    @Override
    public List<FileSystemItemChange> getFileSystemChanges() {
        return fileSystemChanges;
    }

    @Override
    @JsonDeserialize(using = FileSystemItemChangeListDeserializer.class)
    public void setFileSystemChanges(List<FileSystemItemChange> changes) {
        this.fileSystemChanges = changes;
    }

    /**
     * @return the time code of current sync operation in milliseconds since
     *         1970-01-01 UTC rounded to the second as measured on the server
     *         clock.
     */
    @Override
    public Long getSyncDate() {
        return syncDate;
    }

    /**
     * @return the last available log id in the audit log table
     * */
    @Override
    public Long getUpperBound() {
        return upperBound;
    }

    @Override
    public String getActiveSynchronizationRootDefinitions() {
        return activeSynchronizationRootDefinitions;
    }

    @Override
    public void setActiveSynchronizationRootDefinitions(
            String activeSynchronizationRootDefinitions) {
        this.activeSynchronizationRootDefinitions = activeSynchronizationRootDefinitions;
    }

    @Override
    public void setSyncDate(Long syncDate) {
        this.syncDate = syncDate;
    }

    @Override
    public void setUpperBound(Long upperBound) {
        this.upperBound = upperBound;
    }

    @Override
    public void setHasTooManyChanges(Boolean hasTooManyChanges) {
        this.hasTooManyChanges = hasTooManyChanges;
    }

    @Override
    public Boolean getHasTooManyChanges() {
        return this.hasTooManyChanges;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("(");
        sb.append(String.format("upperBound=%d, ", getUpperBound()));
        sb.append(String.format("syncDate=%d, ", getSyncDate()));
        if (hasTooManyChanges) {
            sb.append("hasTooManyChanges=true");
        } else {
            sb.append(String.format("items=[%s]",
                    StringUtils.join(fileSystemChanges, ", ")));
        }
        sb.append(")");
        return sb.toString();
    }
}
